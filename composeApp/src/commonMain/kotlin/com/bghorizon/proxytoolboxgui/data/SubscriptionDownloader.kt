package com.bghorizon.proxytoolboxgui.data

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

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
}
