package org.cryptobiotic.mixnet.ch

import electionguard.core.ElementModP
import electionguard.core.ElementModQ
import electionguard.core.GroupContext
import electionguard.core.randomElementModQ
import org.cryptobiotic.mixnet.core.VectorP
import org.cryptobiotic.mixnet.core.VectorQ

//  * ALGORITHM 8.47
fun committmentChain(group: GroupContext,
                     h: ElementModP, // another generator besides g
                     bold_u_tilde: List<ElementModQ> // Permuted public challenges
): Pair<List<ElementModP>, List<ElementModQ>> {

    val nrows = bold_u_tilde.size
    val bold_c_hat = mutableListOf<ElementModP>()
    val bold_r_hat = mutableListOf<ElementModQ>()

    var R_i_minus_1 = group.ZERO_MOD_Q
    var U_i_minus_1 = group.ONE_MOD_Q
    repeat(nrows) { i ->
        val r_hat_i: ElementModQ = group.randomElementModQ(minimum = 1)
        bold_r_hat.add(r_hat_i)

        val R_i = r_hat_i + bold_u_tilde[i] * R_i_minus_1
        val U_i = bold_u_tilde[i] * U_i_minus_1
        val c_hat_i = group.gPowP(R_i) * (h powP U_i)
        bold_c_hat.add(c_hat_i)

        R_i_minus_1 = R_i
        U_i_minus_1 = U_i
    }

    return Pair(bold_c_hat, bold_r_hat)
}

fun committmentChainV(group: GroupContext,
                     h: ElementModP, // another generator besides g
                     bold_u_tilde: VectorQ // Permuted public challenges
): Pair<VectorP, VectorQ> {

    val bold_c_hat = mutableListOf<ElementModP>()
    val bold_r_hat = mutableListOf<ElementModQ>()

    var R_i_minus_1 = group.ZERO_MOD_Q
    var U_i_minus_1 = group.ONE_MOD_Q
    repeat(bold_u_tilde.nelems) { i ->
        val r_hat_i: ElementModQ = group.randomElementModQ(minimum = 1)
        bold_r_hat.add(r_hat_i)

        val R_i = r_hat_i + bold_u_tilde.elems[i] * R_i_minus_1
        val U_i = bold_u_tilde.elems[i] * U_i_minus_1
        val c_hat_i = group.gPowP(R_i) * (h powP U_i)
        bold_c_hat.add(c_hat_i)

        R_i_minus_1 = R_i
        U_i_minus_1 = U_i
    }

    return Pair(VectorP(group, bold_c_hat), VectorQ(group, bold_r_hat))
}