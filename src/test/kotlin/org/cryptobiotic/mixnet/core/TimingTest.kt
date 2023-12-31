package org.cryptobiotic.mixnet.core

import electionguard.core.getSystemTimeInMillis
import electionguard.core.productionGroup
import electionguard.core.randomElementModQ
import kotlin.test.Test

class TimingTest {
    val group = productionGroup()

    @Test
    // compare exp vs acc
    fun testExp() {
        warmup(20000)
        compareExp(1000)
        compareExp(10000)
        compareExp(20000)
    }

    fun warmup(n:Int) {
        repeat(n) { group.gPowP(group.randomElementModQ()) }
        println("warmup with $n")
    }

    fun compareExp(n:Int) {

        val nonces = List(n) { group.randomElementModQ() }
        val h = group.gPowP(group.randomElementModQ())
        println("h class is ${h.javaClass.name}")

        var starting = getSystemTimeInMillis()
        repeat(n) { group.gPowP(nonces[it]) }
        var duration = getSystemTimeInMillis() - starting
        val peracc = duration.toDouble() / n
        println("acc took $duration msec for $n = $peracc msec per acc")

        starting = getSystemTimeInMillis()
        repeat(n) { h powP nonces[it] }
        duration = getSystemTimeInMillis() - starting
        val perexp = duration.toDouble() / n
        println("exp took $duration msec for $n = $perexp msec per exp")

        println("exp/acc took ${perexp/peracc}")

    }
}