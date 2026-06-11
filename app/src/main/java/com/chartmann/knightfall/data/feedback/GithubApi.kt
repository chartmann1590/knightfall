package com.chartmann.knightfall.data.feedback

import com.chartmann.knightfall.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface GithubService {
    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateIssueRequest
    ): GithubIssue

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun getIssue(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): GithubIssue

    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun getComments(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int
    ): List<GithubComment>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun postComment(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("number") number: Int,
        @Body request: PostCommentRequest
    ): GithubComment

    @PUT("repos/{owner}/{repo}/contents/{assetDir}/{filename}")
    suspend fun uploadAsset(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("assetDir") assetDir: String,
        @Path("filename") filename: String,
        @Body request: UploadAssetRequest
    ): UploadAssetResponse
}

class GithubApi(private val service: GithubService) : GithubService by service {
    companion object {
        fun create(): GithubApi {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val headerInterceptor = Interceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "Knightfall-Android/0.1")

                val token = BuildConfig.GITHUB_API_TOKEN
                if (token.isNotEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }

                chain.proceed(builder.build())
            }

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader("Authorization")
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            return GithubApi(retrofit.create(GithubService::class.java))
        }
    }
}
