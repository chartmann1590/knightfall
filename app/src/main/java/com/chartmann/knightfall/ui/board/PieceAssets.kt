package com.chartmann.knightfall.ui.board

import androidx.annotation.DrawableRes
import com.chartmann.knightfall.R
import com.github.bhlangonijr.chesslib.Piece

@DrawableRes
fun pieceDrawable(piece: Piece): Int? = when (piece) {
    Piece.WHITE_KING -> R.drawable.piece_wk
    Piece.WHITE_QUEEN -> R.drawable.piece_wq
    Piece.WHITE_ROOK -> R.drawable.piece_wr
    Piece.WHITE_BISHOP -> R.drawable.piece_wb
    Piece.WHITE_KNIGHT -> R.drawable.piece_wn
    Piece.WHITE_PAWN -> R.drawable.piece_wp
    Piece.BLACK_KING -> R.drawable.piece_bk
    Piece.BLACK_QUEEN -> R.drawable.piece_bq
    Piece.BLACK_ROOK -> R.drawable.piece_br
    Piece.BLACK_BISHOP -> R.drawable.piece_bb
    Piece.BLACK_KNIGHT -> R.drawable.piece_bn
    Piece.BLACK_PAWN -> R.drawable.piece_bp
    else -> null
}
