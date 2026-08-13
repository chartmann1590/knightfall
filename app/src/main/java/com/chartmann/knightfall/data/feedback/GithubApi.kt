package com.chartmann.knightfall.data.feedback

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Talks to the cloudflare-worker/ feedback relay, not api.github.com directly. See
 * GithubApi.create and cloudflare-worker/src/index.ts.
 */
interface GithubService {
    @POST("issue")
    suspend fun createIssue(@Body request: CreateIssueRequest): GithubIssue

    @GET("issue/{number}")
    suspend fun getIssue(@Path("number") number: Int): GithubIssue

    @GET("issue/{number}/comments")
    suspend fun getComments(@Path("number") number: Int): List<GithubComment>

    @POST("issue/{number}/comments")
    suspend fun postComment(
        @Path("number") number: Int,
        @Body request: PostCommentRequest
    ): GithubComment

    @POST("upload-image")
    suspend fun uploadAsset(@Body request: UploadAssetRequest): UploadAssetResponse
}

class GithubApi(private val service: GithubService) : GithubService by service {
    companion object {
        // GitHub token no longer travels through this app — held server-side as a
        // Worker secret instead. Previously embedded BuildConfig.GITHUB_API_TOKEN
        // client-side as a Bearer header, which shipped a real repo-write PAT in every
        // release build (extractable from the APK).
        fun create(): GithubApi {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://knightfall-github-feedback.charles-h-hartmann1.workers.dev/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return GithubApi(retrofit.create(GithubService::class.java))
        }
    }
}
