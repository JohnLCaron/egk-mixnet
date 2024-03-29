package org.cryptobiotic.maths

import electionguard.core.ElGamalCiphertext
import electionguard.core.ElementModP
import electionguard.core.GroupContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cryptobiotic.gmp.EgkGmpLib
import org.cryptobiotic.gmp.VmnProdPowWGmp
import kotlin.math.max
import kotlin.math.min

enum class ProdColumnAlg { Gmp, Java }

/** Component-wise product of the ballot's column vectors ^ exps. */
class ProdColumnPow(val nthreads: Int, val alg: ProdColumnAlg? = null) {

    companion object {
        private val debugCores = false

        val hasGmp by lazy { EgkGmpLib.loadIfAvailable() }

        fun calcCores(): Int {
            val cores = Runtime.getRuntime().availableProcessors()
            val useCores = max(cores * 3 / 4, 1)
            if (debugCores) println("cores = $cores useCores = $useCores")
            return useCores
        }

        /**
         * Component-wise product of the ballot's column vectors ^ exps.
         *
         *  rows (aka ballots): nrows x width ElGamalCiphertexts
         *  exps: nrows ElementModQ
         *  for each column, calculate Prod (col ^ exps) modulo, return VectorCiphertext(width).
         */
        fun prodColumnPow(
            rows: List<VectorCiphertext>,
            exps: VectorQ,
            nthreads: Int? = null,
            alg: ProdColumnAlg? = null
        ): VectorCiphertext {
            val useCores = if (nthreads == null) calcCores() else nthreads
            return ProdColumnPow(useCores, alg).prodColumnPow(rows, exps)
        }
    }

    fun prodColumnPow(rows: List<VectorCiphertext>, exps: VectorQ): VectorCiphertext {
        return if (nthreads < 2) {
            calcSingleThread(rows, exps)

        } else {
            PprodColumnPow(rows, exps, nthreads).calc()
        }
    }

    // TODO batching, but only if its gmp.
    private fun calcSingleThread(rows: List<VectorCiphertext>, exps: VectorQ): VectorCiphertext {
        val nrows = rows.size
        require(exps.nelems == nrows)
        val width = rows[0].nelems
        val result = List(width) { col ->
            val column = List(nrows) { row -> rows[row].elems[col] }
            val padElement = prodColumnPow(VectorP(exps.group, column.map { it.pad }), exps)
            val dataElement = prodColumnPow(VectorP(exps.group, column.map { it.data }), exps)
            ElGamalCiphertext(padElement, dataElement)
        }
        return VectorCiphertext(exps.group, result)
    }

    // compute Prod (col_i ^ exp_i)
    private fun prodColumnPow(col: VectorP, exps: VectorQ): ElementModP {
        require(exps.nelems == col.nelems)
        return if (alg == ProdColumnAlg.Java) {
            Prod(col powP exps)
        } else if (alg == ProdColumnAlg.Gmp) { // force Gmp even if we dont have it
            VmnProdPowWGmp.prodPow(col.elems, exps)
        } else {
            if (hasGmp) {
                VmnProdPowWGmp.prodPow(col.elems, exps)
            } else {
                Prod(col powP exps)
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // parallel calculator of product of columns vectors to a power
    // we separate the pads and data, and divide into batches of size VmnModPowTabW.maxBatchSize,
    // even when not using VmnModPowTabW.

    inner class PprodColumnPow(val rows: List<VectorCiphertext>, val exps: VectorQ, val nthreads: Int) {
        val group: GroupContext = exps.group
        val pads = mutableMapOf<Int, MutableList<ElementModP>>()
        val datas = mutableMapOf<Int, MutableList<ElementModP>>()

        fun calc(): VectorCiphertext {
            require(exps.nelems == rows.size)

            runBlocking {
                val jobs = mutableListOf<Job>()
                val colProducer = producer(rows)
                repeat(nthreads) {
                    jobs.add(launchCalculator(colProducer) { (columnV, expsV, colIdx) ->
                        Pair(prodColumnPow(columnV, expsV), colIdx)
                    })
                }
                // wait for all calculations to be done, then close everything
                joinAll(*jobs.toTypedArray())
            }

            // put results in order
            require(pads.size == datas.size)
            val texts = List(pads.size) {
                val pad = pads[it]!!.reduce { a, b -> a * b }
                val data = datas[it]!!.reduce { a, b -> a * b }
                ElGamalCiphertext(pad, data)
            }
            return VectorCiphertext(group, texts)
        }

        private fun CoroutineScope.producer(rows: List<VectorCiphertext>): ReceiveChannel<Triple<VectorP, VectorQ, Int>> =
            produce {
                val nrows = rows.size
                val width = rows[0].nelems
                List(width) { col ->
                    val column = List(nrows) { row -> rows[row].elems[col] }

                    // batch the pads
                    val columnPad = VectorP(group, column.map { it.pad })
                    var offset = 0
                    while (offset < nrows) {
                        val batchSize = min(VmnProdPowWGmp.maxBatchSize, nrows - offset)
                        val baseBatch = columnPad.elems.subList(offset, offset + batchSize)
                        val expsBatch = exps.elems.subList(offset, offset + batchSize)
                        send(Triple(VectorP(group, baseBatch), VectorQ(group, expsBatch), col * 2))
                        yield()

                        offset += batchSize
                    }

                    // batch the datas
                    val columnData = VectorP(group, column.map { it.data })
                    offset = 0
                    while (offset < nrows) {
                        val batchSize = min(VmnProdPowWGmp.maxBatchSize, nrows - offset)
                        val baseBatch = columnData.elems.subList(offset, offset + batchSize)
                        val expsBatch = exps.elems.subList(offset, offset + batchSize)
                        send(Triple(VectorP(group, baseBatch), VectorQ(group, expsBatch), col * 2 + 1))
                        yield()

                        offset += batchSize
                    }
                }
                channel.close()
            }

        private val mutex = Mutex()

        private fun CoroutineScope.launchCalculator(
            producer: ReceiveChannel<Triple<VectorP, VectorQ, Int>>,
            calculate: (Triple<VectorP, VectorQ, Int>) -> Pair<ElementModP, Int>
        ) = launch(Dispatchers.Default) {

            for (pair in producer) {
                val (column, idx) = calculate(pair)
                // println("calculated column $idx")
                mutex.withLock {
                    val colIdx = idx / 2
                    val mlist = if (idx % 2 == 0) pads.getOrPut(colIdx) { mutableListOf() }
                    else datas.getOrPut(colIdx) { mutableListOf() }
                    mlist.add(column)
                }
                yield()
            }
        }
    }
}