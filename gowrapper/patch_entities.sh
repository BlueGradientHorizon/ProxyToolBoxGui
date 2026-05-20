sed -i 's/val sortProfilesByDelay: Boolean = false/val sortProfilesByDelay: Boolean = false,\n    val performSpeedTest: Boolean = true,\n    val speedTestRounds: Int = 1,\n    val speedTestProvider: String = "cloudflare",\n    val speedTestMode: String = "download",\n    val speedTestTargetBytes: Long = 1024L,\n    val sortSpeedByDelay: Boolean = false/g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Entities.kt

sed -i 's/val delay: Long = -1/val delay: Long = -1,\n    val workingSpeed: Boolean = false,\n    val speed: Double = -1.0/g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Entities.kt

sed -i 's/val sortProfilesByDelay: Boolean = false/val sortProfilesByDelay: Boolean = false,\n    val performSpeedTest: Boolean = true,\n    val speedTestRounds: Int = 1,\n    val speedTestProvider: String = "cloudflare",\n    val speedTestMode: String = "download",\n    val speedTestTargetBytes: Long = 1024L,\n    val sortSpeedByDelay: Boolean = false/g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/Models.kt

sed -i 's/val delay: Long = -1/val delay: Long = -1,\n    val speed: Double = -1.0/g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/Models.kt
