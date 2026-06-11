package com.chartmann.knightfall.ui.board

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chartmann.knightfall.ui.theme.CheckHighlight
import com.chartmann.knightfall.ui.theme.LastMoveHighlight
import com.chartmann.knightfall.ui.theme.LegalDot
import com.chartmann.knightfall.ui.theme.MoveHighlight
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import kotlin.math.floor
import kotlin.math.roundToInt

/** Stable identity for a piece so it can animate between squares. */
data class PlacedPiece(val id: Int, val piece: Piece, val square: Square)

/** Column 0..7 (a..h) and row 0..7 (rank 1..8) of a square. */
private val Square.col: Int get() = ordinal % 8
private val Square.row: Int get() = ordinal / 8

private fun squareAt(col: Int, row: Int): Square? =
    if (col in 0..7 && row in 0..7) Square.values()[row * 8 + col] else null

/**
 * Maintains stable piece IDs across positions so Compose can animate piece
 * movement. Positions are diffed: a piece that disappears from one square
 * and appears on another (same piece kind, or a promotion from a pawn)
 * keeps its ID and slides.
 */
class PieceTracker {
    private var nextId = 0
    private var current: List<PlacedPiece> = emptyList()

    fun update(position: Map<Square, Piece>): List<PlacedPiece> {
        val previousBySquare = current.associateBy { it.square }
        val unmatchedPrev = current.toMutableList()
        val result = mutableListOf<PlacedPiece>()
        val appeared = mutableListOf<Pair<Square, Piece>>()

        for ((square, piece) in position) {
            val still = previousBySquare[square]
            if (still != null && still.piece == piece) {
                result += still
                unmatchedPrev -= still
            } else {
                appeared += square to piece
            }
        }

        for ((square, piece) in appeared) {
            // Prefer a vanished piece of the same kind (movement), then a
            // vanished pawn of the same side (promotion).
            val moved = unmatchedPrev.firstOrNull { it.piece == piece }
                ?: unmatchedPrev.firstOrNull {
                    it.piece.pieceSide == piece.pieceSide &&
                        it.piece.pieceType == com.github.bhlangonijr.chesslib.PieceType.PAWN
                }
            if (moved != null) {
                unmatchedPrev -= moved
                result += PlacedPiece(moved.id, piece, square)
            } else {
                result += PlacedPiece(nextId++, piece, square)
            }
        }

        current = result.sortedBy { it.id }
        return current
    }
}

@Composable
fun ChessBoard(
    position: Map<Square, Piece>,
    flipped: Boolean,
    theme: BoardTheme,
    selected: Square?,
    legalTargets: Set<Square>,
    lastMove: Pair<Square, Square>?,
    checkSquare: Square?,
    interactive: Boolean,
    onSquareTap: (Square) -> Unit,
    onDragMove: (from: Square, to: Square) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardSizePx by remember { mutableStateOf(0) }
    val tracker = remember { PieceTracker() }
    val placed = tracker.update(position)

    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    fun squareForOffset(offset: Offset): Square? {
        if (boardSizePx == 0) return null
        val cell = boardSizePx / 8f
        var col = floor(offset.x / cell).toInt()
        var row = 7 - floor(offset.y / cell).toInt()
        if (flipped) {
            col = 7 - col
            row = 7 - row
        }
        return squareAt(col, row)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .onSizeChanged { boardSizePx = it.width }
            .pointerInput(interactive, flipped) {
                if (!interactive) return@pointerInput
                detectTapGestures { offset ->
                    squareForOffset(offset)?.let(onSquareTap)
                }
            }
            .pointerInput(interactive, flipped, position) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val sq = squareForOffset(offset)
                        if (sq != null && position[sq] != null) {
                            dragFrom = sq
                            dragOffset = offset
                            onSquareTap(sq)
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val to = squareForOffset(dragOffset)
                        dragFrom = null
                        if (from != null && to != null && from != to) {
                            onDragMove(from, to)
                        }
                    },
                    onDragCancel = { dragFrom = null },
                )
            },
    ) {
        BoardSquares(theme, flipped, selected, legalTargets, lastMove, checkSquare, position)

        val cellPx = if (boardSizePx > 0) boardSizePx / 8 else 0
        val cellDp: Dp = with(androidx.compose.ui.platform.LocalDensity.current) {
            (cellPx).toDp()
        }

        for (p in placed) {
            val res = pieceDrawable(p.piece) ?: continue
            val col = if (flipped) 7 - p.square.col else p.square.col
            val row = if (flipped) p.square.row else 7 - p.square.row
            val isDragged = dragFrom == p.square

            val target = IntOffset(col * cellPx, row * cellPx)
            val animated by animateIntOffsetAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 180),
                label = "piece-${p.id}",
            )
            val offset = if (isDragged) {
                IntOffset(
                    (dragOffset.x - cellPx / 2f).roundToInt(),
                    (dragOffset.y - cellPx * 0.75f).roundToInt(),
                )
            } else animated

            Image(
                painter = painterResource(res),
                contentDescription = null,
                modifier = Modifier
                    .offset { offset }
                    .size(cellDp)
                    .zIndex(if (isDragged) 2f else 1f),
            )
        }
    }
}

@Composable
private fun BoardSquares(
    theme: BoardTheme,
    flipped: Boolean,
    selected: Square?,
    legalTargets: Set<Square>,
    lastMove: Pair<Square, Square>?,
    checkSquare: Square?,
    position: Map<Square, Piece>,
) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = size.width / 8f

        fun drawCell(square: Square, color: Color) {
            val col = if (flipped) 7 - square.col else square.col
            val row = if (flipped) square.row else 7 - square.row
            drawRect(
                color = color,
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell),
            )
        }

        for (row in 0..7) {
            for (col in 0..7) {
                val light = (row + col) % 2 == 1
                val sq = squareAt(col, row) ?: continue
                drawCell(sq, if (light) theme.lightSquare else theme.darkSquare)
            }
        }

        lastMove?.let { (from, to) ->
            drawCell(from, LastMoveHighlight)
            drawCell(to, LastMoveHighlight)
        }
        selected?.let { drawCell(it, MoveHighlight) }
        checkSquare?.let { drawCell(it, CheckHighlight) }

        for (sq in legalTargets) {
            val col = if (flipped) 7 - sq.col else sq.col
            val row = if (flipped) sq.row else 7 - sq.row
            val center = Offset(col * cell + cell / 2f, row * cell + cell / 2f)
            if (position[sq] != null) {
                // Capture: ring around the square
                drawCircle(
                    color = LegalDot,
                    radius = cell * 0.46f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.08f),
                )
            } else {
                drawCircle(color = LegalDot, radius = cell * 0.16f, center = center)
            }
        }
    }
}
