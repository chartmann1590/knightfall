package com.chartmann.knightfall.coach

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Owns the on-device Gemma 4 model file and its download lifecycle.
 * The model (~2.5 GB) is fetched from the LiteRT community repo on
 * Hugging Face — there is no Firebase Storage on the Spark plan, and the
 * model is Apache 2.0 with no download gate.
 */
class CoachModelManager(private val context: Context) {

    sealed class ModelState {
        data object NotDownloaded : ModelState()
        data class Downloading(val progressPercent: Int) : ModelState()
        data object Ready : ModelState()
        data class Failed(val message: String) : ModelState()
    }

    val modelFile: File
        get() = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "models/$MODEL_FILE_NAME",
        )

    val isDownloaded: Boolean
        get() = modelFile.exists() && modelFile.length() >= MIN_VALID_SIZE_BYTES

    fun stateFlow(): Flow<ModelState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(DOWNLOAD_WORK_NAME)
            .map { infos ->
                val info = infos.firstOrNull()
                when {
                    isDownloaded -> ModelState.Ready
                    info == null -> ModelState.NotDownloaded
                    info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED ->
                        ModelState.Downloading(info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0))
                    info.state == WorkInfo.State.SUCCEEDED -> ModelState.Ready
                    info.state == WorkInfo.State.FAILED ->
                        ModelState.Failed("Download failed — check your connection and retry")
                    info.state == WorkInfo.State.CANCELLED -> ModelState.NotDownloaded
                    else -> ModelState.NotDownloaded
                }
            }

    fun startDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DOWNLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork(DOWNLOAD_WORK_NAME)
    }

    fun deleteModel() {
        cancelDownload()
        modelFile.delete()
        partialFile().delete()
    }

    fun partialFile(): File = File(modelFile.parentFile, "$MODEL_FILE_NAME.part")

    companion object {
        const val DOWNLOAD_WORK_NAME = "gemma4_model_download"
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        // Public, ungated repo (Apache 2.0) — no Hugging Face token required.
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        // The real file is ~2.5 GB; anything below this is a broken download.
        const val MIN_VALID_SIZE_BYTES = 1_500_000_000L
    }
}
