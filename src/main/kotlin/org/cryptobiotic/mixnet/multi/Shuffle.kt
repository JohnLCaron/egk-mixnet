package org.cryptobiotic.mixnet.multi

import electionguard.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cryptobiotic.mixnet.core.*

/**
 * Shuffle rows (nrows x width) of ElGamalCiphertext.
 * Return shufffled rows (nrows x width), matrix of reencryption nonces (nrows x width), and permutation function.
 * Operation count is 2 * nrows * width accelerated exponentiations = "2N acc".
 */
fun shuffle(rows: List<VectorCiphertext>, publicKey: ElGamalPublicKey, nthreads: Int = 10):
        Triple<List<VectorCiphertext>, MatrixQ, PermutationVmn> {
    return if (nthreads == 0) {
        shuffle(rows, publicKey)
    } else {
        PShuffle(rows, publicKey, nthreads).shuffle()
    }
}

fun shuffle(
    rows: List<VectorCiphertext>,
    publicKey: ElGamalPublicKey,
): Triple<List<VectorCiphertext>, MatrixQ, PermutationVmn> {

    val mixed = mutableListOf<VectorCiphertext>()
    val rnonces = mutableListOf<VectorQ>()

    val psi = PermutationVmn.random(rows.size)
    repeat(rows.size) { jdx ->
        val idx = psi.of(jdx) //  pe[jdx] = e[ps.of(jdx)]; you have an element in pe, and need to get the corresponding element from e
        val (reencrypt, nonceV) = rows[idx].reencrypt(publicKey)
        mixed.add(reencrypt)
        rnonces.add(nonceV)
    }
    return Triple(mixed, MatrixQ(rnonces), psi)
}

fun shuffleWithInverse(
    rows: List<VectorCiphertext>,
    publicKey: ElGamalPublicKey,
): Triple<List<VectorCiphertext>, MatrixQ, PermutationVmn> {

    val mixed = mutableListOf<VectorCiphertext>()
    val rnonces = mutableListOf<VectorQ>()

    val psi = PermutationVmn.random(rows.size)
    repeat(rows.size) { jdx ->
        val idx = psi.inv(jdx) //  pe[jdx] = e[ps.of(jdx)]; you have an element in pe, and need to get the corresponding element from e
        val (reencrypt, nonceV) = rows[idx].reencrypt(publicKey)
        mixed.add(reencrypt)
        rnonces.add(nonceV)
    }
    return Triple(mixed, MatrixQ(rnonces), psi)
}

// parallel shuffle
class PShuffle(val rows: List<VectorCiphertext>, val publicKey: ElGamalPublicKey, val nthreads: Int = 10) {
    val group: GroupContext = publicKey.context
    val n = rows.size
    var mixed = MutableList(n) { VectorCiphertext.empty(group) }
    var rnonces = MutableList(n) { VectorQ.empty(group) }
    val psi = PermutationVmn.random(n)

    fun shuffle(): Triple<List<VectorCiphertext>, MatrixQ, PermutationVmn> {
        runBlocking {
            val jobs = mutableListOf<Job>()
            val pairProducer = producer(rows, psi)
            repeat(nthreads) {
                jobs.add( launchCalculator(pairProducer) { row -> row.reencrypt(publicKey) })
            }
            // wait for all calculations to be done, then close everything
            joinAll(*jobs.toTypedArray())
        }

        return Triple(mixed, MatrixQ(rnonces), psi)
    }

    private fun CoroutineScope.producer(rows: List<VectorCiphertext>, psi: PermutationVmn): ReceiveChannel<Pair<VectorCiphertext, Int>> =
        produce {
            rows.forEachIndexed { idx, row ->
                send(Pair(row, psi.inv(idx)))
                yield()
            }
            channel.close()
        }

    private val mutex = Mutex()

    private fun CoroutineScope.launchCalculator(
        input: ReceiveChannel<Pair<VectorCiphertext, Int>>,
        calculate: (VectorCiphertext) -> Pair<VectorCiphertext, VectorQ>
    ) = launch(Dispatchers.Default) {

        for (pair in input) {
            val (row, jdx) = pair
            val (reencrypt, nonces) = calculate(row)
            mutex.withLock {
                mixed[jdx] = reencrypt
                rnonces[jdx] = nonces
            }
            yield()
        }
    }
}



