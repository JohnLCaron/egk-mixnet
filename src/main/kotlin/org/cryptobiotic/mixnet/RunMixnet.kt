package org.cryptobiotic.mixnet

import com.github.michaelbull.result.unwrap
import electionguard.core.*
import electionguard.publish.Consumer
import electionguard.publish.makeConsumer
import electionguard.util.Stopwatch
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.cryptobiotic.maths.VectorCiphertext
import org.cryptobiotic.writer.BallotReader
import org.cryptobiotic.writer.writeBallotsToFile
import org.cryptobiotic.writer.writeProofOfShuffleJsonToFile

class RunMixnet {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunMixnet")
            val electionguardDir by parser.option(
                ArgType.String,
                shortName = "egDir",
                description = "electionguard directory containing the init file"
            ).required()
            val encryptedBallotDir by parser.option(
                ArgType.String,
                shortName = "eballots",
                description = "Directory of encrypted ballots"
            )
            val inputBallots by parser.option(
                ArgType.String,
                shortName = "in",
                description = "Input ciphertext binary file"
            )
            val width by parser.option(
                ArgType.Int,
                shortName = "width",
                description = "Number of ciphertexts in each ballot"
            ).required()
            val outputDir by parser.option(
                ArgType.String,
                shortName = "out",
                description = "Output directory"
            ).required()
            val mixName by parser.option(
                ArgType.String,
                shortName = "mix",
                description = "mix name"
            ).required()
            parser.parse(args)

            println( buildString {
                appendLine("=========================================")
                appendLine("  RunMixnet starting")
                appendLine( "   electionguardDir= $electionguardDir")
                appendLine( "   encryptedBallotDir= $encryptedBallotDir")
                appendLine( "   inputBallots= $inputBallots")
                appendLine( "   width= $width")
                appendLine( "   outputDir= $outputDir")
                appendLine( "   mixName= $mixName")
            })
            val mixnet = Mixnet(electionguardDir, outputDir, width)

            val ballots: List<VectorCiphertext>
            if (encryptedBallotDir != null) {
                ballots = readEncryptedBallots(mixnet.group, encryptedBallotDir!!)
            } else if (inputBallots != null) {
                ballots = mixnet.readInputBallots(inputBallots!!)
            } else {
                throw RuntimeException("Must specify either encryptedBallotDir or inputBallots")
            }

            val (shuffled, proof) = mixnet.runShuffleProof(ballots, mixName)
            writeBallotsToFile(shuffled, "$outputDir/Shuffled.bin")
            writeProofOfShuffleJsonToFile(proof, "$outputDir/Proof.json")
            println(" RunMixnet complete successfully")
        }
    }
}

class Mixnet(egDir:String, outputDir:String, val width: Int) {
    val group = productionGroup()
    val consumer : Consumer = makeConsumer(group, egDir, true)
    val publicKey: ElGamalPublicKey

    init {
        val init = consumer.readElectionInitialized().unwrap()
        publicKey = init.jointPublicKey()
    }

    fun readInputBallots(inputBallots: String): List<VectorCiphertext> {
        val reader = BallotReader(group, width)
        return reader.readFromFile(inputBallots)
    }

    fun runShuffleProof(
        ballots: List<VectorCiphertext>,
        mixName: String,
        nthreads: Int = 10,
    ): Pair<List<VectorCiphertext>, ProofOfShuffle> {
        val nrows = ballots.size
        val width = ballots[0].nelems
        val N = nrows * width
        println("  runShuffleProof nrows=$nrows, width= $width per row, N=$N, nthreads=$nthreads")

        val stopwatch = Stopwatch()
        val (mixedBallots, rnonces, psi) = shuffle(ballots, publicKey, nthreads)
        println("  shuffle took = ${Stopwatch.perRow(stopwatch.stop(), nrows)}")

        stopwatch.start()
        val proof = runProof(
            group,
            mixName,
            publicKey,
            w = ballots,
            wp = mixedBallots,
            rnonces,
            psi,
            nthreads
        )
        println("  proof took = ${Stopwatch.perRow(stopwatch.stop(), nrows)}")

        return Pair(mixedBallots, proof)
    }
}

fun readEncryptedBallots(group: GroupContext, encryptedBallotDir: String): List<VectorCiphertext> {
    val consumer : Consumer = makeConsumer(group, encryptedBallotDir, true)

    val mixnetBallots = mutableListOf<VectorCiphertext>()
    var first = true
    var countCiphertexts = 0
    consumer.iterateEncryptedBallotsFromDir(encryptedBallotDir, null).forEach { encryptedBallot ->
        val ciphertexts = mutableListOf<ElGamalCiphertext>()
        ciphertexts.add(encryptedBallot.encryptedSn!!) // always the first one
        encryptedBallot.contests.forEach { contest ->
            contest.selections.forEach { selection ->
                ciphertexts.add(selection.encryptedVote)
            }
        }
        mixnetBallots.add(VectorCiphertext(group, ciphertexts))
        if (first) countCiphertexts = ciphertexts.size else require(countCiphertexts == ciphertexts.size)
    }
    return mixnetBallots
}