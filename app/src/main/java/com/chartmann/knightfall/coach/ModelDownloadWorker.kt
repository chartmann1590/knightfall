package com.chartmann.knightfall.coach

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.chartmann.knightfall.KnightfallApp
import com.chartmann.knightfall.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the Gemma 4 model with resume support (HTTP Range requests),
 * showing progress in a foreground notification. WorkManager retries and
 * survives app restarts; partially downloaded bytes are kept.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val manager = CoachModelManager(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (manager.isDownloaded) return@withContext Result.success()

        val partial = manager.partialFile()
        partial.parentFile?.mkdirs()

        try {
            setForeground(foregroundInfo(0))

            var downloaded = if (partial.exists()) partial.length() else 0L
            val connection = URL(CoachModelManager.MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (downloaded > 0) connection.setRequestProperty("Range", "bytes=$downloaded-")
            connection.connect()

            val code = connection.responseCode
            if (code !in 200..299) {
                if (code == 416) {
                    // Range beyond EOF — partial file corrupt; start over.
                    partial.delete()
                    return@withContext Result.retry()
                }
                return@withContext if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
            if (code != HttpURLConnection.HTTP_PARTIAL && downloaded > 0) {
                // Server ignored the range; restart from scratch.
                partial.delete()
                downloaded = 0
            }

            val remaining = connection.contentLengthLong
            val total = if (remaining > 0) downloaded + remaining else -1L

            connection.inputStream.use { input ->
                RandomAccessFile(partial, "rw").use { out ->
                    out.seek(downloaded)
                    val buffer = ByteArray(1 shl 18)
                    var lastReportedPercent = -1
                    while (true) {
                        if (isStopped) return@withContext Result.retry()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                setProgress(workDataOf(KEY_PROGRESS to percent))
                                setForeground(foregroundInfo(percent))
                            }
                        }
                    }
                }
            }

            if (downloaded < CoachModelManager.MIN_VALID_SIZE_BYTES) {
                return@withContext if (runAttemptCount < 5) Result.retry() else Result.failure()
            }

            if (!partial.renameTo(manager.modelFile)) {
                partial.copyTo(manager.modelFile, overwrite = true)
                partial.delete()
            }
            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, KnightfallApp.CHANNEL_GENERAL)
                .setSmallIcon(R.drawable.ic_knight)
                .setContentTitle("Downloading chess coach")
                .setContentText("Gemma 4 model — $percent%")
                .setProgress(100, percent, percent == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        private const val NOTIFICATION_ID = 4801
    }
}
