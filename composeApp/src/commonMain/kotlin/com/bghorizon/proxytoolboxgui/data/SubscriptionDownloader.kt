package com.bghorizon.proxytoolboxgui.data

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object SubscriptionDownloader {

    private val client = HttpClient()

    suspend fun download(url: String, timeoutSeconds: Int): String {
        val response = client.request(url) {
            method = HttpMethod.Get
            timeout {
                requestTimeoutMillis = timeoutSeconds * 1000L
                connectTimeoutMillis = timeoutSeconds * 1000L
                socketTimeoutMillis = timeoutSeconds * 1000L
            }
        }
        if (response.status != HttpStatusCode.OK) {
            throw Exception("HTTP ${response.status}")
        }
        return response.bodyAsText()
    }

    suspend fun <T> downloadParallel(
        urls: List<T>,
        getUrl: (T) -> String,
        timeoutSeconds: Int,
        maxParallel: Int,
        onDownloadComplete: suspend (T, String) -> Unit,
        onDownloadError: suspend (T, Exception) -> Unit
    ) {
        val semaphore = Semaphore(maxParallel)
        coroutineScope {
            urls.forEach { item ->
                launch {
                    semaphore.withPermit {
                        try {
                            val content = download(getUrl(item), timeoutSeconds)
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
