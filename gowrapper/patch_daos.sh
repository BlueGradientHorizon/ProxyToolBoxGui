sed -i '/data class ConfigTestResultUpdate(/i \
    @Query("UPDATE subscriptions_data SET workingSpeed = :workingSpeed, speed = :speed WHERE subId = :subId AND configId = :configId")\
    suspend fun updateConfigSpeedTestResult(\
        subId: String,\
        configId: Int,\
        workingSpeed: Boolean,\
        speed: Double,\
    )\
\
    @Transaction\
    suspend fun updateConfigSpeedTestResultsBatch(results: List<ConfigSpeedTestResultUpdate>) {\
        results.forEach { updateConfigSpeedTestResult(it.subId, it.configId, it.workingSpeed, it.speed) }\
    }\
\
    @Query("UPDATE subscriptions_data SET workingSpeed = 0, speed = -1.0")\
    suspend fun resetWorkingSpeedData()\
' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Daos.kt

cat << 'EOF2' >> /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Daos.kt

data class ConfigSpeedTestResultUpdate(
    val subId: String,
    val configId: Int,
    val workingSpeed: Boolean,
    val speed: Double
)
EOF2
