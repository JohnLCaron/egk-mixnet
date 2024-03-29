package org.cryptobiotic.maths

import electionguard.core.*
import electionguard.util.Stopwatch
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.random.Random
import kotlin.test.Test

class ProdColumnPowTest {
    val group = productionGroup()

    @Test
    fun testProdColumnPow1() {
        val nrows = 7
        val width = 100
        val exps = VectorQ(group, List(nrows) { group.randomElementModQ() })

        val keypair = elGamalKeyPairFromRandom(group)

        val ciphertexts: List<VectorCiphertext> = List(nrows) {
            val ciphertexts = List(width) { Random.nextInt(11).encrypt(keypair) }
            VectorCiphertext(group, ciphertexts)
        }

        val stopwatch = Stopwatch()
        val single = prodColumnPowJava(ciphertexts, exps, 1)
        val timeSingle = stopwatch.stop()

        stopwatch.start()
        val multi = prodColumnPowJava(ciphertexts, exps)
        val timeMulti = stopwatch.stop()
        assertEquals(single, multi)

        println("testProdColumnPow1 nrows=$nrows width=$width timeSingle/timeMulti = ${Stopwatch.ratioAndPer(timeSingle, timeMulti, nrows)}")
    }

    @Test
    fun testProdColumnPowTab1() {
        val nrows = 7
        val width = 100
        val exps = VectorQ(group, List(nrows) { group.randomElementModQ() })

        val keypair = elGamalKeyPairFromRandom(group)

        val ciphertexts: List<VectorCiphertext> = List(nrows) {
            val ciphertexts = List(width) { Random.nextInt(11).encrypt(keypair) }
            VectorCiphertext(group, ciphertexts)
        }

        val stopwatch = Stopwatch()
        val single = prodColumnPowTab(ciphertexts, exps, 1)
        val timeSingle = stopwatch.stop()

        stopwatch.start()
        val multi = prodColumnPowTab(ciphertexts, exps)
        val timeMulti = stopwatch.stop()
        assertEquals(single, multi)

        println("testProdColumnPowTab1 nrows=$nrows width=$width timeSingle/timeMulti = ${Stopwatch.ratioAndPer(timeSingle, timeMulti, nrows)}")
    }

    @Test
    fun testProdColumnPow() {
        compareProdColumnPow(7,100)
        compareProdColumnPow(100,34)
        compareProdColumnPow(500,34)
        compareProdColumnPow(7,100, 2)
        compareProdColumnPow(100,34, 2)
        compareProdColumnPow(500,34, 2)
        println()
    }

    @Test
    fun testProdColumnPowNarrow() {
        compareProdColumnPow(7,1)
        compareProdColumnPow(100,1)
        compareProdColumnPow(600,1)
        println()
    }

    @Test
    fun testProdColumnPowRows() {
        compareProdColumnPow(1,34)
        compareProdColumnPow(10,34)
        compareProdColumnPow(100,34)
        compareProdColumnPow(1000,34)
        compareProdColumnPow(2000,34)
        println()
    }

    @Test
    fun testProdColumnPowWidth() {
        compareProdColumnPow(100,1)
        compareProdColumnPow(100,10)
        compareProdColumnPow(100,100)
        compareProdColumnPow(100,300)
        println()
    }

    @Test
    fun testProdColumnPowWidthRow() {
        compareProdColumnPow(1000,1)
        compareProdColumnPow(1000,10)
        compareProdColumnPow(1000,100)
        compareProdColumnPow(1000,300)
        println()
    }

    fun compareProdColumnPow(nrows: Int, width: Int, threads: Int? = null) {
        val exps = VectorQ(group, List(nrows) { group.randomElementModQ() })

        val keypair = elGamalKeyPairFromRandom(group)
        val ciphertexts: List<VectorCiphertext> = List(nrows) {
            val ciphertexts = List(width) { Random.nextInt(11).encrypt(keypair) }
            VectorCiphertext(group, ciphertexts)
        }

        val stopwatch = Stopwatch()
        val org = prodColumnPowJava(ciphertexts, exps, threads)
        val timeOrg = stopwatch.stop()

        stopwatch.start()
        val tab = prodColumnPowTab(ciphertexts, exps, threads)
        val timeTab = stopwatch.stop()
        assertEquals(org, tab)

        println("testProdColumnPowTab nrows=$nrows width=$width threads=$threads timeOrg/timeTab = ${Stopwatch.ratioAndPer(timeOrg, timeTab, nrows)}")
    }

    @Test
    fun testProdColumnPowGvsJ() {
        compareProdColumnPowGvsJ(1,10)
        compareProdColumnPowGvsJ(10,10)
        compareProdColumnPowGvsJ(100,10)
        compareProdColumnPowGvsJ(100,34)
        compareProdColumnPowGvsJ(1000,34)
        println()
    }

    @Test
    fun testSmallGvsJ() {
        compareProdColumnPowGvsJ(100,10) // warmup
            repeat(30) { width ->
                compareProdColumnPowGvsJ(1, 2*width+1)
            }
        println()
    }

    fun compareProdColumnPowGvsJ(nrows: Int, width: Int, threads: Int? = null) {
        val exps = VectorQ(group, List(nrows) { group.randomElementModQ() })

        val keypair = elGamalKeyPairFromRandom(group)
        val ciphertexts: List<VectorCiphertext> = List(nrows) {
            val ciphertexts = List(width) { Random.nextInt(11).encrypt(keypair) }
            VectorCiphertext(group, ciphertexts)
        }

        val stopwatch = Stopwatch()
        val org =  ProdColumnPow.prodColumnPow(ciphertexts, exps, null, ProdColumnAlg.Java)
        val timeOrg = stopwatch.stop()

        stopwatch.start()
        val gmp = ProdColumnPow.prodColumnPow(ciphertexts, exps, null, ProdColumnAlg.Gmp)
        val timeTab = stopwatch.stop()
        assertEquals(org, gmp)

        println("compareProdColumnPowGvsJ nrows=$nrows width=$width threads=$threads timeOrg/timeTab = ${Stopwatch.ratioAndPer(timeOrg, timeTab, nrows)}")
    }

}