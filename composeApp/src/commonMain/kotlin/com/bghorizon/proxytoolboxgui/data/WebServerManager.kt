package com.bghorizon.proxytoolboxgui.data

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebServerManager(
    private val settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    
    private val _isRunning = MutableStateFlow(value = false)
    val isRunning = _isRunning.asStateFlow()

    fun start() {
        val settings = settingsRepository.settings.value
        val port = settings.webServerPort
        val host = if (settings.webServerLocalhost) "127.0.0.1" else "0.0.0.0"

        stopInternal()
        
        server = embeddedServer(CIO, port = port, host = host) {
            routing {
                get("/") {
                    val currentSettings = settingsRepository.settings.value
                    var configs = subscriptionRepository.getWorkingConfigs()
                    
                    if (currentSettings.sortProfilesByDelay) {
                        configs = configs.sortedWith(compareBy<ProxyConfig> { it.delay }.thenBy { it.tag })
                    }
                    
                    call.respondText(
                        configs.joinToString("\n") { it.connURI },
                        contentType = ContentType.Text.Plain
                    )
                }
            }
        }.start(wait = false)

        _isRunning.value = true
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        try {
            server?.stop(1000, 2000)
            server = null
            _isRunning.value = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
