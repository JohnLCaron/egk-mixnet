package org.cryptobiotic.gmp

import electionguard.core.*
import org.cryptobiotic.mixnet.VectorCiphertext
import org.cryptobiotic.mixnet.VectorQ
import java.lang.foreign.*
import java.lang.foreign.ValueLayout.*


/*

cd ~/install/jextract-21/bin

./jextract  --source \
    --header-class-name EgkGmpIF \
    --target-package org.cryptobiotic.gmp \
    --dump-includes /home/stormy/dev/github/egk-mixnet/src/main/c/includes.txt \
    -I /home/stormy/dev/github/egk-mixnet/src/main/c/egk_gmp.h \
    -l /usr/local/lib/libegkgmp.so \
    --output /home/stormy/dev/github/egk-mixnet/src/main/java \
    /home/stormy/dev/github/egk-mixnet/src/main/c/egk_gmp.h

./jextract  --source \
    --header-class-name EgkGmpIF \
    --target-package org.cryptobiotic.gmp \
    --source @/home/stormy/dev/github/egk-mixnet/src/main/c/include.txt \
    -I /home/stormy/dev/github/egk-mixnet/src/main/c/egk_gmp.h \
    -l /usr/local/lib/libegkgmp.so \
    --output /home/stormy/dev/github/egk-mixnet/src/main/java \
    /home/stormy/dev/github/egk-mixnet/src/main/c/egk_gmp.h

 */

private const val debug = false

/**
 * Compute Prod (col_i ^ exp_i) for i = 0..nrows.
 * Interface to egk_prodPowW.c
 * Douglas Wikstrom version of prodPow, running about 50% faster, but uses more memory.
 */
fun prodColumnPowGmpW(rows: List<VectorCiphertext>, exps: VectorQ): VectorCiphertext {
    val nrows = rows.size
    require(exps.nelems == nrows)
    val width = rows[0].nelems
    val result = List(width) { col -> // can parellelize
        val column = List(nrows) { row -> rows[row].elems[col] }
        val pad = prodColumnPowGmpW( column.map { it.pad }, exps)
        val data = prodColumnPowGmpW( column.map { it.data }, exps)
        ElGamalCiphertext(pad, data)
    }
    return VectorCiphertext(exps.group, result)
}

// compute Prod (col_i ^ exp_i) for one column
private fun prodColumnPowGmpW(col: List<ElementModP>, exps: VectorQ): ElementModP {
    val qbs = exps.elems.map { it.byteArray() }
    val pbs = col.map { it.byteArray() }
    val modulusBytes = exps.group.constants.largePrime
    val resultBytes =  egkProdPowW(pbs, qbs, modulusBytes)
    return exps.group.binaryToElementModPsafe(resultBytes)
}

private fun egkProdPowW(pbs: List<ByteArray>, qbs: List<ByteArray>, modulusBytes: ByteArray): ByteArray {
    require( pbs.size == qbs.size)
    Arena.ofConfined().use { arena ->
        pbs.forEach { require(it.size == modulusBytes.size) }
        val pbytes = modulusBytes.size
        val pbytesL = pbytes.toLong()

        // Array of arrays of 512 bytes
        val pbaa : MemorySegment = arena.allocateArray(ADDRESS, pbs.size.toLong())
        pbs.forEachIndexed { idx, pb ->
            require( pb.size == pbytes)
            if (debug) println(" pbs first byte = ${pb[0]}")
            val heapSegment = MemorySegment.ofArray(pb) // turn it into a MemorySegment, on the heap
            val offheap = arena.allocate(pbytesL)
            // copy to the offheap segment
            MemorySegment.copy(heapSegment, 0.toLong(), offheap, 0, pbytesL)
            // put into the address array
            pbaa.setAtIndex(ADDRESS, idx.toLong(), offheap)
        }

        // Array of arrays of 32 bytes
        val qbytes = 32.toLong()
        val qbaa : MemorySegment = arena.allocateArray(ADDRESS, qbs.size.toLong())
        qbs.forEachIndexed { idx, qb ->
            if (debug) println(" qbs first byte = ${qb[0]}")
            require( qb.size == 32)
            val heapSegment = MemorySegment.ofArray(qb) // turn it into a MemorySegment, on the heap
            val offheap = arena.allocate(qbytes)
            // copy to the offheap segment
            MemorySegment.copy(heapSegment, 0.toLong(), offheap, 0, qbytes)
            // put into the address array
            qbaa.setAtIndex(ADDRESS, idx.toLong(), offheap)
        }

        if (debug) println("byteaToMS")
        val msModulus = byteToOffHeap(modulusBytes, arena.allocate(pbytesL))
        if (debug) println("byteToModulus")

        // the result is just len * pbytes
        val resultBytes = arena.allocate(pbytesL)

        // void egk_prodPowW(void *result, const void **pb, const void **qb, const int len, const void *modulusBytes, size_t pbytes, size_t qbytes);
        EgkGmpIF.egk_prodPowW(resultBytes, pbaa, qbaa, pbs.size, msModulus, pbytesL, qbytes)
        if (debug) println("egk_prodPowW")

        // copies it back to on heap
        val raw : ByteArray = resultBytes.toArray(JAVA_BYTE)
        if (debug) println("raw nbytes = ${raw.size} expect= ${pbs.size * pbytes }")
        return raw
    }
}