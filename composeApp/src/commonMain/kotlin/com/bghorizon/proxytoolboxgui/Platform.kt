package com.bghorizon.proxytoolboxgui

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform