package org.cryptobiotic.mixnet.ch

import electionguard.core.*
import org.cryptobiotic.mixnet.core.*

private val debug1 = false
private val debug2 = false

// unfinished
// starting from the CHvote code; add the multitext stuff
// TODO hack in nlnu algorithm, reconciled with vmn multitext
private fun shufflePrepV(
    group: GroupContext,
    U: String,
    seed: ElementModQ,
    publicKey: ElGamalPublicKey, // public key = pk
    psi: Permutation, // permutation = psi
    ciphertexts: List<ElGamalCiphertext>, // ciphertexts = bold_e
    shuffled: List<ElGamalCiphertext>, // shuffled ciphertexts = bold_e_tilde
): ShufflePrepV {
    // create independent, deterministic group generators, from a seed and a string.
    val (h, generators) = getGeneratorsV(group, psi.n, U, seed)

    val (pcommit, pnonces) = permutationCommitmentV(group, psi, generators)
    // TODO review
    val challenges = getChallengesV(group, psi.n, listOf(ciphertexts, shuffled, pcommit, publicKey))
    val pchallenges = challenges.permute(psi)

    // TODO CHvote vs VMN
    val (cchallenges, ccnonces) = committmentChainV(group, h, pchallenges) // cbold_hat, rbold_hat
    return ShufflePrepV(h, generators, pcommit, pnonces, challenges, pchallenges, cchallenges, ccnonces)
}

private data class ShufflePrepV(
    val h: ElementModP,
    val generators: VectorP,
    val pcommit: VectorP, // permutation commitment = cbold
    val pnonces: VectorQ, // permutation nonces = rbold
    val u: VectorQ, // challenges = hash(stuff) = bold_u = challenges
    val pu: VectorQ,   // permuted challenges = ubold_tilde - ctilde

    val cchallenges: VectorP, // chained challenges = ĉbold = cbold_hat
    val ccnonces: VectorQ,    // chained challenges nonces = rbold_hat
)

fun shuffleProofV(
    group: GroupContext,
    U: String,
    seed: ElementModQ,
    publicKey: ElGamalPublicKey,
    psi: Permutation,
    rows: List<MultiText>, // ciphertexts
    shuffledBallots: List<MultiText>, // shuffled ciphertexts
    rnonces: MatrixQ, // re-encryption nonces = pr = nrows x width
    nthreads: Int = 10,
): ShuffleProofV {
    val nrows = rows.size
    val width = rows[0].width
    val ciphertexts = rows.flatMap { it.ciphertexts }
    val shuffled = shuffledBallots.flatMap { it.ciphertexts }

    val prep = shufflePrepV(group, U, seed, publicKey, psi, ciphertexts, shuffled)

    val bold_omega_hat = VectorQ.randomQ(group, nrows)
    val bold_omega_tilde = VectorQ.randomQ(group, nrows)

    val bold_R_prime = mutableListOf<ElementModQ>()
    val bold_U_prime = mutableListOf<ElementModQ>()
    val bold_t_hat = mutableListOf<ElementModP>()

    //// loop1
    var R_i_minus_1 = group.ZERO_MOD_Q
    var U_i_minus_1 = group.ONE_MOD_Q
    repeat (nrows) { i ->

        // Ri  = r̂i + ũi * Ri-1 mod q
        // var R_i = ZZ_q.add(bold_r_hat.getValue(i), ZZ_q.multiply(bold_u_tilde.getValue(i), R_i_minus_1));
        val R_i = prep.ccnonces.elems[i] + (prep.pu.elems[i] * R_i_minus_1)

        // Rip = ω̂i + ω̃i * Ri-1 mod q
        // var R_prime_i = ZZ_q.add(omega_hat_i, ZZ_q.multiply(omega_tilde_i, R_i_minus_1));
        val R_prime_i = bold_omega_hat.elems[i] + (bold_omega_tilde.elems[i] * R_i_minus_1)

        // Ui  = ũi * Ui-1 mod q
        // var U_i = ZZ_q.multiply(bold_u_tilde.getValue(i), U_i_minus_1);
        val U_i = prep.pu.elems[i] * U_i_minus_1

        // Uip = ω̃i * Ui´1 mod q
        // var U_prime_i = ZZ_q.multiply(omega_tilde_i, U_i_minus_1);
        val U_prime_i = bold_omega_tilde.elems[i] * U_i_minus_1

        bold_R_prime.add(R_prime_i)
        bold_U_prime.add(U_prime_i)

        // 25) t̂i ← g^ω̂i * ĉi−1^wpi
        // t̂i = g^Rip * h^Uip mod p|
        // val t_hat_i = ZZPlus_p.multiply(ZZPlus_p.pow(g, R_prime_i), ZZPlus_p.pow(h, U_prime_i))
        val t_hat_i = group.gPowP(R_prime_i) * (prep.h powP U_prime_i)
        bold_t_hat.add(t_hat_i)

        R_i_minus_1 = R_i // preparation for next loop cycle
        U_i_minus_1 = U_i // preparation for next loop cycle
    }

    val omega_1: ElementModQ = group.randomElementModQ(minimum = 1)
    val t_1 = group.gPowP(omega_1) // 19) t1 ← g^ω1 mod p
    val omega_2: ElementModQ = group.randomElementModQ(minimum = 1)
    val t_2 = group.gPowP(omega_2) // 20) t2 ← g^ω2 mod p

    // (21) t3 ← g^ω3 * Prod( hi^ωi' )
    //  var t_3 = ZZPlus_p.multiply(ZZPlus_p.pow(g, omega_3), ZZPlus_p.prodPow(bold_h, bold_omega_tilde));
    val omega_3: ElementModQ = group.randomElementModQ(minimum = 1)
    val t_3 = group.gPowP(omega_3) * Prod(prep.generators powP bold_omega_tilde)

    val omega_4: ElementModQ = group.randomElementModQ(minimum = 1)
    val (t41, t42) = if (nthreads == 1) {
        // t_41 = pk^-ω4 * Prod(ãi^ω̃i')
        // var t_41 = ZZPlus_p.multiply(ZZPlus_p.invert(ZZPlus_p.pow(pk, omega_4)),
        //          ZZPlus_p.prodPow(bold_e_tilde.map(Encryption::get_a), bold_omega_tilde)); // a = pk^eps term
        val t_41 = group.prodPowA( shuffledBallots, bold_omega_tilde) / (publicKey powP omega_4)

        // t_42 = g^-ω4 * Prod(bti^ω̃i'), bt = btilde
        // var t_42 = ZZPlus_p.multiply(ZZPlus_p.invert(ZZPlus_p.pow(g, omega_4)),
        //          ZZPlus_p.prodPow(bold_e_tilde.map(Encryption::get_b), bold_omega_tilde)); // b = g^eps term
        val t_42 = group.prodPowB(shuffledBallots, bold_omega_tilde) / group.gPowP(omega_4)
        Pair(t_41, t_42)
    } else {
        // parellel calculation here
        val (t1sum, t2sum) = PcalcProdPow(group, nthreads).calcProdPow(shuffledBallots, bold_omega_tilde)
        val t_41 = t1sum / (publicKey powP omega_4)
        val t_42 = t2sum / group.gPowP(omega_4)
        Pair(t_41, t_42)
    }

    val t = listOf(t_1, t_2, t_3, t41, t42, bold_t_hat)
    val y = listOf(ciphertexts, shuffled, prep.pcommit, prep.cchallenges, publicKey)
    val c = getChallenge(group, y, t)

    // var r_bar = ZZ_q.sum(bold_r);
    // var s_1 = ZZ_q.subtract(omega_1, ZZ_q.multiply(c, r_bar));
    val s1 = omega_1 - c * prep.pnonces.sum()

    //  var r_hat = ZZ_q.sumProd(bold_r_hat, bold_v);
    //  var s_2 = ZZ_q.subtract(omega_2, ZZ_q.multiply(c, r_hat));
    val s2 = computeS2(group, nrows, prep, omega_2, c)

    //  var r = ZZ_q.sumProd(bold_r, bold_u);
    //  var s_3 = ZZ_q.subtract(omega_3, ZZ_q.multiply(c, r));
    val s3 = omega_3 - c * prep.u.innerProduct(prep.pnonces)

    //  var s_4 = ZZ_q.subtract(omega_4, ZZ_q.multiply(c, r_tilde));
    val s4 = computeS4(group, width, rnonces, prep.pu, omega_4, c)

    //// loop2
    //val bold_s_hat = List(nrows) { bold_omega_hat[it] - c * prep.ccnonces[it] }
    //val bold_s_tilde = List(nrows) { bold_omega_tilde[it] - c * prep.pu[it] }
    val minusc = -c
    val bold_s_hat = bold_omega_hat + prep.ccnonces.timesScalar(minusc)
    val bold_s_tilde = bold_omega_tilde + prep.pu.timesScalar(minusc)

    val proof = ShuffleProofV(U, seed, prep.pcommit, prep.cchallenges,
        c, s1, s2, s3, s4, bold_s_hat, bold_s_tilde,
        bold_omega_hat, bold_omega_tilde, listOf(omega_1, omega_2, omega_3, omega_4))

    return proof
}

//  10. rhat_utilde = Sumi(r̂i * (Prodj(ũj), j=i+1..N), i=1..N) = r̂
private fun computeS2(group: GroupContext, N: Int, prep: ShufflePrepV, omega_2: ElementModQ, c:ElementModQ): ElementModQ {
    // var v_i = BigInteger.ONE;
    // for (int i = N; i >= 1; i--) {
    //    builder_bold_v.setValue(i, v_i);
    //    v_i = ZZ_q.multiply(bold_u_tilde.getValue(i), v_i);
    // }
    // var bold_v = builder_bold_v.build();
    val bold_v = MutableList(N) { group.ZERO_MOD_Q }
    var partialProduct = group.ONE_MOD_Q
    for (i in N-1 downTo 0) {
        bold_v[i] = partialProduct
        partialProduct = prep.pu.elems[i] * partialProduct
    }
    //  var r_hat = ZZ_q.sumProd(bold_r_hat, bold_v);
    //  var s_2 = ZZ_q.subtract(omega_2, ZZ_q.multiply(c, r_hat));
    val s2 = omega_2 - c * prep.ccnonces.innerProduct(VectorQ(group, bold_v))
    return s2
}

private fun computeS4(group: GroupContext, width:Int, rnonces: MatrixQ, pu: VectorQ, omega_4: ElementModQ, c:ElementModQ): ElementModQ {
    // NOT var r_tilde = ZZ_q.sumProd(bold_r_tilde, bold_u);
    // NOT val rtilde_u = group.sumProd(rbold_tilde, ubold); line 141 GenShuffleProof
    // ubold = challenges, but need permuted challenges
    val rtilde_u = group. ZERO_MOD_Q // width.toElementModQ(group) * rnonces.innerProduct(pu) // TODO

    //  var s_4 = ZZ_q.subtract(omega_4, ZZ_q.multiply(c, r_tilde));
    val s4 = omega_4 - (c * rtilde_u)
    return s4
}

data class ShuffleProofV(
    val U: String,
    val seed: ElementModQ,
    val pcommit: VectorP,     // permutation committment = cbold, vmn u
    val cchallenges: VectorP, // chained challenges = cbold_hat

    val c: ElementModQ, // challenge
    val s1: ElementModQ,
    val s2: ElementModQ,
    val s3: ElementModQ,
    val s4: ElementModQ,
    val bold_s_hat: VectorQ,
    val bold_s_tilde: VectorQ,
    val bold_omega_hat: VectorQ,
    val bold_omega_tilde: VectorQ,
    val omega: List<ElementModQ>, // size 4
)

fun verifyShuffleProofV(
    group: GroupContext,
    U: String,
    seed: ElementModQ,
    pk: ElGamalPublicKey,
    ballots: List<MultiText>, // ciphertexts
    shuffledBallots: List<MultiText>, // shuffled
    proof: ShuffleProofV,
    nthreads: Int = 10,
): Boolean {
    val nrows = ballots.size

    // create independent, deterministic group generators, from a seed and a string.
    val (h, generators) = getGeneratorsV(group, nrows, U, seed) // List<ElementModP> = bold_h

    val ciphertexts = ballots.flatMap { it.ciphertexts }
    val shuffled = shuffledBallots.flatMap { it.ciphertexts }
    val bold_u = getChallengesV(group, nrows, listOf(ciphertexts, shuffled, proof.pcommit, pk))
    val u = Prod(bold_u) // product of the challenges

    // val c_bar = ZZPlus_p.divide(ZZPlus_p.prod(bold_c), ZZPlus_p.prod(bold_h))
    val c_bar = Prod(proof.pcommit) / Prod(generators)
    // var c_hat = ZZPlus_p.divide(N == 0 ? c_hat_0 : bold_c_hat.getValue(N), ZZPlus_p.pow(h, u));
    val c_hat = proof.cchallenges.elems[nrows - 1] / (h powP u)
    val c_tilde = Prod(proof.pcommit powP bold_u)

    val (a_tilde, b_tilde) = if (nthreads == 1) {
        Pair(group.prodPowA(ballots, bold_u), group.prodPowB(ballots, bold_u))
    } else {
        PcalcProdPow(group, nthreads).calcProdPow(ballots, bold_u)
    }

    val bold_t_hat = mutableListOf<ElementModP>()
    repeat(nrows) { i ->
        val chatMinusOne = if (i == 0) h else proof.cchallenges.elems[i - 1]
        bold_t_hat.add((proof.cchallenges.elems[i] powP proof.c) * group.gPowP(proof.bold_s_hat.elems[i]) *
                (chatMinusOne powP proof.bold_s_tilde.elems[i]))
    }
    val t_1 = (c_bar powP proof.c) * group.gPowP(proof.s1)
    val t_2 = (c_hat powP proof.c) * group.gPowP(proof.s2)
    val t_3 = (c_tilde powP proof.c) * group.gPowP(proof.s3) * Prod(generators powP proof.bold_s_tilde)

    val (t41, t42) = if (nthreads == 1) {
        val t_41 = (a_tilde powP proof.c) * group.prodPowA(shuffledBallots, proof.bold_s_tilde) / (pk powP proof.s4)
        val t_42 = (b_tilde powP proof.c) * group.prodPowB(shuffledBallots, proof.bold_s_tilde) / group.gPowP(proof.s4)
        Pair(t_41, t_42)
    } else {
        // parellel calculation here
        val (t1sum, t2sum) = PcalcProdPow(group, nthreads).calcProdPow(shuffledBallots, proof.bold_s_tilde)
        val t_41 = (a_tilde powP proof.c) * t1sum / (pk powP proof.s4)
        val t_42 = (b_tilde powP proof.c) * t2sum / group.gPowP(proof.s4)
        Pair(t_41, t_42)
    }

    val t = listOf(t_1, t_2, t_3, t41, t42, bold_t_hat)
    val y = listOf(ciphertexts, shuffled, proof.pcommit, proof.cchallenges, pk)
    val challenge_prime = getChallenge(group, y, t)

    return proof.c.equals(challenge_prime)
}