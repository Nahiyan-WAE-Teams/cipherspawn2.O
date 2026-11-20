package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.search.SearchInfo
import java.util.concurrent.TimeUnit

/**
 * Public, unauthenticated YouTube channel extraction.
 *
 * NewPipe Extractor is GPL-3.0. Keep its license and notices with distributed builds.
 * YouTube may rate-limit or change its public pages; callers must treat extraction as fallible.
 */
class PublicYouTubeExtractor {

    suspend fun searchChannels(query: String, limit: Int = 8): List<PublicChannel> =
        withContext(Dispatchers.IO) {
            initialize()
            val normalized = query.trim()
            require(normalized.isNotEmpty()) { "Enter a channel name, handle, or URL." }

            val directUrl = normalizeChannelUrl(normalized)
            if (directUrl != null) {
                return@withContext listOf(fetchChannelInfo(directUrl))
            }

            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(normalized)
            val results = SearchInfo.getInfo(service, handler).relatedItems
                .asSequence()
                .filter { it.infoType == InfoItem.InfoType.CHANNEL }
                .filterIsInstance<ChannelInfoItem>()
                .take(limit.coerceIn(1, 20))
                .toList()

            results.mapNotNull { item ->
                runCatching { fetchChannelInfo(item.url, item) }.getOrNull()
            }.distinctBy { it.channelId }
        }

    suspend fun fetchChannel(urlOrId: String): PublicChannel = withContext(Dispatchers.IO) {
        initialize()
        val url = normalizeChannelUrl(urlOrId)
            ?: throw IllegalArgumentException("Use a YouTube channel URL, @handle, or UC channel ID.")
        fetchChannelInfo(url)
    }

    private fun fetchChannelInfo(
        url: String,
        searchItem: ChannelInfoItem? = null,
    ): PublicChannel {
        val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
        val avatar = info.avatars.firstOrNull()?.url.orEmpty()
        val subscriberCount = info.subscriberCount
            .takeIf { it >= 0L }
            ?: searchItem?.subscriberCount?.takeIf { it >= 0L }
            ?: 0L
        val videoCount = searchItem?.streamCount?.takeIf { it >= 0L } ?: 0L

        return PublicChannel(
            channelId = info.id,
            title = info.name,
            handle = info.url.substringAfter("youtube.com/", "").takeIf { it.startsWith("@") }.orEmpty(),
            description = info.description.orEmpty(),
            thumbnailUrl = avatar,
            subscriberCount = subscriberCount,
            videoCount = videoCount,
            hiddenSubscriberCount = info.subscriberCount < 0L && (searchItem?.subscriberCount ?: -1L) < 0L,
            channelUrl = info.url,
        )
    }

    private fun normalizeChannelUrl(input: String): String? {
        val value = input.trim()
        if (value.startsWith("https://") || value.startsWith("http://")) {
            val host = value.substringAfter("://").substringBefore('/').lowercase()
            if (host != "youtube.com" && host != "www.youtube.com" && host != "m.youtube.com") {
                return null
            }
            return value.substringBefore('?').substringBefore('#').trimEnd('/')
        }
        if (value.startsWith("@") && value.length > 1 && !value.contains(' ')) {
            return "https://www.youtube.com/$value"
        }
        if (value.startsWith("UC") && value.length in 20..36 && !value.contains(' ')) {
            return "https://www.youtube.com/channel/$value"
        }
        return null
    }

    private fun initialize() {
        if (initialized) return
        synchronized(PublicYouTubeExtractor::class.java) {
            if (!initialized) {
                NewPipe.init(ExtractorDownloader(client))
                initialized = true
            }
        }
    }

    data class PublicChannel(
        val channelId: String,
        val title: String,
        val handle: String,
        val description: String,
        val thumbnailUrl: String,
        val subscriberCount: Long,
        val videoCount: Long,
        val hiddenSubscriberCount: Boolean,
        val channelUrl: String,
    )

    private class ExtractorDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }
            if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                builder.header("User-Agent", USER_AGENT)
            }
            val body = request.dataToSend()?.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )
            builder.method(request.httpMethod(), body)

            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBody,
                    response.request.url.toString(),
                )
            }
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"

        @Volatile
        var initialized = false

        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
}
