package com.bghorizon.proxytoolboxgui.data

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.readAvailable
import kotlin.io.encoding.Base64
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object SubscriptionDownloader {

    private val client = HttpClient {
        install(HttpTimeout)
    }

    suspend fun download(
        url: String,
        timeoutSeconds: Int,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String {
        return client.prepareGet(url) {
            timeout {
                requestTimeoutMillis = timeoutSeconds * 1000L
                connectTimeoutMillis = timeoutSeconds * 1000L
                socketTimeoutMillis = timeoutSeconds * 1000L
            }
        }.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                throw Exception("HTTP ${response.status}")
            }
            
            val contentLength = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val packetBuilder = BytePacketBuilder()
            var downloaded = 0L
            val buffer = ByteArray(8192)

            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                
                packetBuilder.writeFully(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, contentLength)
            }

            val content = packetBuilder.build().readText()
            tryDecodeBase64(content)
        }
    }

    private fun tryDecodeBase64(content: String): String {
        if (content.isEmpty()) return content

        return try {
            Base64
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(content).decodeToString()
        } catch (_: Exception) {
            content
        }
    }

    suspend fun <T> downloadParallel(
        urls: List<T>,
        getUrl: (T) -> String,
        timeoutSeconds: Int,
        maxParallel: Int,
        onStart: suspend (T) -> Unit,
        onDownloadComplete: suspend (T, String) -> Unit,
        onDownloadError: suspend (T, Exception) -> Unit,
        onProgress: (T, Long, Long) -> Unit = { _, _, _ -> }
    ) {
        val semaphore = Semaphore(maxParallel)
        coroutineScope {
            urls.forEach { item ->
                launch {
                    semaphore.withPermit {
                        try {
                            onStart(item)
                            val content = download(getUrl(item), timeoutSeconds) { downloaded, total ->
                                onProgress(item, downloaded, total)
                            }
                            onDownloadComplete(item, content)
                        } catch (e: Exception) {
                            onDownloadError(item, e)
                        }
                    }
                }
            }
        }
    }
}
