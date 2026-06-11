package com.chartmann.knightfall.data.feedback

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ImageUploadHelper {
    fun uriToBase64(context: Context, uri: Uri): String {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open InputStream for URI: $uri")
        return inputStream.use { stream ->
            val bytes = stream.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    fun generateUniqueFilename(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val randomString = UUID.randomUUID().toString().substring(0, 6)
        return "issue-$timeStamp-$randomString.png"
    }
}
