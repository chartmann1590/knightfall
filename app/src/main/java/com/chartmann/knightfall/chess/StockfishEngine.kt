package com.chartmann.knightfall.chess

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * UCI wrapper around the bundled Stockfish binary. The binary ships as
 * libstockfish.so so it lands in nativeLibraryDir — the only location a
 * modern Android app is allowed to exec from.
 */
class StockfishEngine(private val context: Context) {

    enum class Difficulty(
        val label: String,
        val approxElo: Int,
        val skillLevel: Int?,   // used below the UCI_Elo floor
        val uciElo: Int?,       // engine's calibrated Elo limit
        val moveTimeMs: Int,
    ) {
        PAWN("Beginner", 700, 0, null, 250),
        KNIGHT("Casual", 1100, 4, null, 350),
        BISHOP("Club player", 1500, null, 1500, 500),
        ROOK("Advanced", 1900, null, 1900, 700),
        QUEEN("Expert", 2350, null, 2350, 900),
        KING("Master", 3000, null, null, 1400),
    }

    data class SearchResult(val bestMoveUci: String, val scoreCp: Int?, val mateIn: Int?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readJob: Job? = null

    @Volatile private var readyDeferred = CompletableDeferred<Unit>()
    @Volatile private var bestMoveDeferred: CompletableDeferred<SearchResult>? = null
    @Volatile private var lastScoreCp: Int? = null
    @Volatile private var lastMateIn: Int? = null

    val isRunning: Boolean get() = process?.isAlive == true

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        val binary = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        check(binary.exists()) { "Stockfish binary missing at ${binary.absolutePath}" }

        val p = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()
        process = p
        writer = BufferedWriter(OutputStreamWriter(p.outputStream))
        readJob = scope.launch { readLoop(BufferedReader(InputStreamReader(p.inputStream))) }

        send("uci")
        send("setoption name Threads value 2")
        send("setoption name Hash value 64")
        awaitReady()
    }

    private suspend fun readLoop(reader: BufferedReader) {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                handleLine(line.trim())
            }
        } catch (_: Exception) {
            // process closed
        }
    }

    private fun handleLine(line: String) {
        when {
            line == "uciok" || line == "readyok" -> readyDeferred.complete(Unit)
            line.startsWith("info") -> parseInfo(line)
            line.startsWith("bestmove") -> {
                val move = line.split(" ").getOrNull(1) ?: return
                bestMoveDeferred?.complete(SearchResult(move, lastScoreCp, lastMateIn))
            }
        }
    }

    private fun parseInfo(line: String) {
        val tokens = line.split(" ")
        val scoreIdx = tokens.indexOf("score")
        if (scoreIdx > 0 && scoreIdx + 2 < tokens.size) {
            when (tokens[scoreIdx + 1]) {
                "cp" -> {
                    lastScoreCp = tokens[scoreIdx + 2].toIntOrNull()
                    lastMateIn = null
                }
                "mate" -> {
                    lastMateIn = tokens[scoreIdx + 2].toIntOrNull()
                    lastScoreCp = null
                }
            }
        }
    }

    private suspend fun awaitReady() {
        readyDeferred = CompletableDeferred()
        send("isready")
        withTimeout(15_000) { readyDeferred.await() }
    }

    private fun send(command: String) {
        val w = writer ?: return
        synchronized(w) {
            w.write(command)
            w.newLine()
            w.flush()
        }
    }

    suspend fun applyDifficulty(difficulty: Difficulty) = commandMutex.withLock {
        if (!isRunning) start()
        if (difficulty.uciElo != null) {
            send("setoption name UCI_LimitStrength value true")
            send("setoption name UCI_Elo value ${difficulty.uciElo}")
            send("setoption name Skill Level value 20")
        } else if (difficulty.skillLevel != null) {
            send("setoption name UCI_LimitStrength value false")
            send("setoption name Skill Level value ${difficulty.skillLevel}")
        } else {
            send("setoption name UCI_LimitStrength value false")
            send("setoption name Skill Level value 20")
        }
        awaitReady()
    }

    /** Finds the best move for [fen]. Score is from the side to move. */
    suspend fun search(fen: String, moveTimeMs: Int): SearchResult = commandMutex.withLock {
        if (!isRunning) start()
        lastScoreCp = null
        lastMateIn = null
        val deferred = CompletableDeferred<SearchResult>()
        bestMoveDeferred = deferred
        send("position fen $fen")
        send("go movetime $moveTimeMs")
        withTimeout(moveTimeMs + 20_000L) { deferred.await() }
    }

    /** Full-strength quick eval used for hints and coach context. */
    suspend fun hint(fen: String): SearchResult {
        val previous = currentDifficulty
        applyDifficulty(Difficulty.KING)
        val result = search(fen, 600)
        previous?.let { applyDifficulty(it) }
        return result
    }

    @Volatile var currentDifficulty: Difficulty? = null
        private set

    suspend fun setDifficulty(difficulty: Difficulty) {
        currentDifficulty = difficulty
        applyDifficulty(difficulty)
    }

    fun stopSearch() = send("stop")

    fun shutdown() {
        try {
            send("quit")
        } catch (_: Exception) {
        }
        readJob?.cancel()
        process?.destroy()
        process = null
        writer = null
    }
}
