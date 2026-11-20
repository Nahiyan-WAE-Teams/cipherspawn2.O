package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface YouTubeApiService {

    @GET("channels")
    suspend fun getChannelsByIds(
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("id") ids: String,
    ): YouTubeChannelsResponse

    @GET("channels")
    suspend fun getChannelByHandle(
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("forHandle") handle: String,
    ): YouTubeChannelsResponse

    @GET("channels")
    suspend fun getMyChannels(
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("mine") mine: Boolean = true,
    ): YouTubeChannelsResponse

    @GET("search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "channel",
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 10,
    ): YouTubeSearchResponse

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 10,
    ): YouTubePlaylistItemsResponse

    @GET("videos")
    suspend fun getVideosByIds(
        @Query("part") part: String = "snippet,statistics,contentDetails",
        @Query("id") ids: String,
    ): YouTubeVideosResponse
}

class YouTubeAuthInterceptor(
    private val oauthManager: YouTubeOAuthManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val bearer = oauthManager.currentAccessToken()
        val apiKey = BuildConfig.YOUTUBE_API_KEY
            .takeIf { it.isNotBlank() && it != PLACEHOLDER_API_KEY }

        val urlBuilder = original.url.newBuilder()
        if (!bearer.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $bearer")
        } else if (!apiKey.isNullOrBlank() && original.url.queryParameter("key") == null) {
            urlBuilder.addQueryParameter("key", apiKey)
        }

        val updatedRequest = builder
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .build()

        return chain.proceed(updatedRequest)
    }

    companion object {
        const val PLACEHOLDER_API_KEY = "YOUR_YOUTUBE_DATA_API_V3_KEY"
    }
}

object YouTubeApiClientFactory {
    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"

    fun create(oauthManager: YouTubeOAuthManager): YouTubeApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(YouTubeAuthInterceptor(oauthManager))
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }
}
