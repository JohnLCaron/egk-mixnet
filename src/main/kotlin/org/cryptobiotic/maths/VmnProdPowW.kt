package org.cryptobiotic.maths

import java.math.BigInteger
import kotlin.math.min

/**
 * Port of VCR's spowm.c (GMP version's algorithm) to Java.
 * Also note VmnProdPowWGmp, which calls the gmp c library with same algorithm.
 * Also note VmnProdPowWB (in test) that uses instrumented BigInteger for operation counting.
 *
 * @param bases Bases used for pre-computation.
 * @param width Number of bases elements to use.
 * @param modulus Underlying modulus.
 */
class VmnProdPowW(
    bases: List<BigInteger>,
    val width: Int, // Width of table of pre-computed values, eg 7
    val modulus: BigInteger,
) {
    val nrows = bases.size
    val ntabs = (nrows + width - 1) / width
    val tables = mutableListOf<PartialProductTable>()
    var countMultiply = 0


    init {
        var offset = 0
        repeat(ntabs) {
            val slice = if (offset + width < nrows) width else nrows - offset
            tables.add(PartialProductTable(bases, offset, slice))
            offset += width
        }
    }

    // partial products for bases_j, j = offset..offset+width
    // called N/w times, each call costs 2^w multiplies
    inner class PartialProductTable(bases: List<BigInteger>, val offset: Int, val slice: Int) {
        val size = 1 shl slice // 2 ^ width
        val pre = MutableList<BigInteger>(size) { BigInteger.ONE }

        // count 2^width modMultiply
        init {
            var i = 1
            var j = offset
            while (i < pre.size) {
                pre[i] = bases[j]
                i *= 2
                j++
            }

            // Perform precalculation of all possible combinations, 2^width products, 2^width multiply and mod.
            for (mask in pre.indices) {
                val onemask = mask and (-mask)
                pre[mask] = pre[mask xor onemask].multiply(pre[onemask]).mod(modulus)
                countMultiply++
            }
        }
    }

    // for exponents_j, j=offset..offset+width, form the word from the bits at bitpos.
    // result lies between 0 and 2^width
    fun egk_getbits(exponents: List<BigInteger>, tableIdx: Int, bitpos: Int): Int {
        val table = tables[tableIdx]
        val offset = table.offset
        val slice = table.slice
        var result = 0
        // Loop over exponents to form a word from all the bits at bitpos.
        for (j in offset until offset + slice) {
            if (exponents[j].testBit(bitpos)) {
                result = result or (1 shl (j - offset))
            }
        }
        return result
    }

    fun modPowProd(
        exponents: List<BigInteger>,
        bitLength: Int
    ): BigInteger {
        require(exponents.size == nrows)

        val maxBitLength = bitLength // TODO

        var res: BigInteger = BigInteger.ONE

        // t squares, and t * N/w multiplies
        for (bitpos in maxBitLength - 1 downTo 0) {  // 256 times = t
            // When processing the batch, all squarings are done for the complete batch
            res = res.multiply(res).mod(modulus)
            countMultiply++

            var tableIdx = 0
            while (tableIdx < ntabs) {
                val factorIdx = egk_getbits(exponents, tableIdx, bitpos)
                val product = tables[tableIdx].pre[factorIdx]
                res = res.multiply(product).mod(modulus)
                tableIdx++
                countMultiply++
            }
        }
        return res
    }

    companion object {
        val maxBatchSize = 84
        val bitLength = 256 // exps always 256 bits
        val width = 7       // so this is fixed also

        // this version does the batches for you.
        fun modPowProdBatched(
            bases: List<BigInteger>,
            exponents: List<BigInteger>,
            modulus: BigInteger,
            show: Boolean = false
        ): BigInteger {

            // For the moment we're not going to break this into batches
            val results = mutableListOf<BigInteger>()
            var offset = 0
            val end = bases.size

            // called N/w times (2^w + 2t) modMultiply
            while (offset < end) {
                val batchSize = min(maxBatchSize, end-offset)
                val baseBatch = bases.subList(offset, offset+batchSize)
                val expsBatch = exponents.subList(offset, offset+batchSize)
                val tab = VmnProdPowW(baseBatch, width, modulus)

                val batch: BigInteger = tab.modPowProd(expsBatch, bitLength)
                results.add(batch)
                offset += batchSize
                if (show) println(" ${tab.countMultiply} countMultiply = ${tab.countMultiply / batchSize} perN")
            }

            // multiply results from each batch
            return results.reduce { a, b -> (a.multiply(b)).mod(modulus) }
        }

        // this version just does the one batch that is sent to it.
        fun modPowProd(
            bases: List<BigInteger>,
            exponents: List<BigInteger>,
            modulus: BigInteger,
            show: Boolean = false
        ): BigInteger {
            val tab = VmnProdPowW(bases, width, modulus)
            val result: BigInteger = tab.modPowProd(exponents, bitLength)
            if (show) println(" ${tab.countMultiply} countMultiply = ${tab.countMultiply / bases.size} perN")
            return result
        }

        // cost per batch is t squares + t * b/w multiplies + b/w * 2^w multiplies
        // you need N/b batches. Keep b a multiple of w, then:
        // = N/b ( t + t * b/w + b/w * 2^w) = N (t/b + (t + 2^w)/w)
        // = (t + 2^w)/w + t/b per row
        fun expectedCount(batch: Int): String {
            val t = 256 // exp size
            val w = 7 // window size
            val w2 = 128 // table size
            val ntabs = (batch + w - 1) / w  // = N/w
            var multiply = ntabs * (t + w2) + t
            return "expected multiply = ${multiply} perRow = ${multiply/batch}"
        }

        // memory needed is 128 * batch/7 * 512 bytes
        fun expectedMemory(batch: Int): String {
            val t = 256 // exp size
            val w = 7 // window size
            val w2 = 128 // table size
            val size = w2 * batch * 512 / w / 1024
            return "expected memory = ${size} kB for batch=$batch"
        }
    }
}