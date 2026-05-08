package com.bghorizon.proxytoolboxgui.data

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType

class ProxyWebServer {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(
        port: Int,
        host: String,
        getConfigUris: () -> String
    ) {
        stop()
        server = embeddedServer(CIO, port = port, host = host) {
            routing {
                get("/") {
                    call.respondText(getConfigUris(), contentType = ContentType.Text.Plain)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    fun isRunning(): Boolean = server != null
}
