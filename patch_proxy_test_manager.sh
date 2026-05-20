#!/bin/bash
sed -i 's/    data class Error(val message: String) : TestEvent()/    data class Error(val message: String) : TestEvent()\n    data class SpeedProgress(val tag: String, val speed: Double, val failed: Boolean) : TestEvent()/' composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/ProxyTestManager.kt

# Move runSpeedTests inside ProxyTestManager class
# The end of the class is around line 123
# We will use awk to fix this.
