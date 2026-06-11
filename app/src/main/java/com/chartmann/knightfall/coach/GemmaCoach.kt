package com.chartmann.knightfall.coach

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Natural-language chess coach backed by Gemma 4 (E2B) running fully
 * on-device through LiteRT-LM. The coach only ever comments — Stockfish
 * plays the moves.
 */
class GemmaCoach(private val modelManager: CoachModelManager) {

    sealed class CoachState {
        data object Unavailable : CoachState()
        data object Initializing : CoachState()
        data object Ready : CoachState()
        data class Error(val message: String) : CoachState()
    }

    @Volatile var state: CoachState = CoachState.Unavailable
        private set

    private var engine: Engine? = null
    private val initMutex = Mutex()

    val isReady: Boolean get() = state is CoachState.Ready

    /**
     * Loads the model. Can take ~10s; callers should show a spinner.
     * Tries the GPU (OpenCL) backend first and falls back to CPU.
     */
    suspend fun initialize(): Boolean = initMutex.withLock {
        if (engine != null) return true
        if (!modelManager.isDownloaded) {
            state = CoachState.Unavailable
            return false
        }
        state = CoachState.Initializing
        return withContext(Dispatchers.IO) {
            val path = modelManager.modelFile.absolutePath
            for (backend in listOf(Backend.GPU(), Backend.CPU())) {
                try {
                    val candidate = Engine(EngineConfig(modelPath = path, backend = backend))
                    candidate.initialize()
                    engine = candidate
                    state = CoachState.Ready
                    return@withContext true
                } catch (e: Throwable) {
                    // try next backend
                }
            }
            state = CoachState.Error("Couldn't start the coach on this device")
            false
        }
    }

    /** Streams a short comment about the move that was just played. */
    fun commentOnPosition(
        fen: String,
        recentMovesUci: List<String>,
        playerIsWhite: Boolean,
        evalCp: Int?,
        mateIn: Int?,
    ): Flow<String> {
        val evalText = when {
            mateIn != null -> "There is a forced mate in ${kotlin.math.abs(mateIn)}."
            evalCp != null -> {
                val pawns = evalCp / 100.0
                "Engine evaluation: ${"%+.1f".format(pawns)} pawns for the side to move."
            }
            else -> ""
        }
        val prompt = """
            You are a friendly chess coach talking to a casual player who is playing ${if (playerIsWhite) "White" else "Black"}.
            Current position (FEN): $fen
            Recent moves (UCI): ${recentMovesUci.takeLast(6).joinToString(" ")}
            $evalText
            In 2 sentences or fewer, give one encouraging, concrete observation about the position or the last move. Mention piece names, not square coordinates. Do not suggest an exact move. Do not use markdown.
        """.trimIndent()
        return generate(prompt)
    }

    /** Streams an answer to a free-form question about the current game. */
    fun answerQuestion(
        fen: String,
        recentMovesUci: List<String>,
        playerIsWhite: Boolean,
        question: String,
    ): Flow<String> {
        val prompt = """
            You are a friendly chess coach. Your student plays ${if (playerIsWhite) "White" else "Black"}.
            Current position (FEN): $fen
            Recent moves (UCI): ${recentMovesUci.takeLast(8).joinToString(" ")}
            Student's question: $question
            Answer in 3 sentences or fewer, plain language, no markdown. Give ideas and plans rather than exact move sequences.
        """.trimIndent()
        return generate(prompt)
    }

    private fun generate(prompt: String): Flow<String> = flow {
        val e = engine ?: error("Coach not initialized")
        val conversation = e.createConversation()
        try {
            conversation.sendMessageAsync(prompt).collect { message ->
                emit(message.toString())
            }
        } finally {
            try {
                conversation.close()
            } catch (_: Throwable) {
            }
        }
    }.flowOn(Dispatchers.IO)
        .catch { e -> emit("(coach hiccup: ${e.message ?: "unknown error"})") }

    fun shutdown() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        state = CoachState.Unavailable
    }
}
