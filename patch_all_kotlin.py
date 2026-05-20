import re

# 1. Fix SettingsScreen.kt
with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

settings_dialog = """    data object ParallelDownloads : SettingsDialog
    data object SpeedTestRounds : SettingsDialog
    data object SpeedTestProvider : SettingsDialog
    data object SpeedTestMode : SettingsDialog
    data object SpeedTestTargetBytes : SettingsDialog"""
content = content.replace("    data object ParallelDownloads : SettingsDialog", settings_dialog)

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(content)


# 2. Fix GoBridge.jvm.kt
with open('composeApp/src/jvmMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.jvm.kt', 'r') as f:
    content = f.read()

content = content.replace("@JvmStatic\n    @JvmStatic\n    actual external fun nativeRunSpeedTests", "@JvmStatic\n    actual external fun nativeRunSpeedTests")

with open('composeApp/src/jvmMain/kotlin/com/bghorizon/proxytoolboxgui/data/GoBridge.jvm.kt', 'w') as f:
    f.write(content)


# 3. Fix HomeScreenViewModel.kt
with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/viewmodel/HomeScreenViewModel.kt', 'r') as f:
    content = f.read()

speed_progress = """            is TestEvent.SpeedProgress -> {
                _uiState.update { state ->
                    val current = state.testProgress
                    val updatedProgresses = current.speedBatchProgresses.toMutableList()
                    val batchIndex = updatedProgresses.indexOfFirst {
                        it.batchNum == current.speedCurrentBatch && it.roundNum == current.speedCurrentRound
                    }

                    if (batchIndex >= 0) {
                        val bp = updatedProgresses[batchIndex]
                        updatedProgresses[batchIndex] = bp.copy(
                            running = bp.running - 1,
                            failed = if (event.failed) bp.failed + 1 else bp.failed,
                            succeeded = if (!event.failed) bp.succeeded + 1 else bp.succeeded
                        )
                    }

                    state.copy(testProgress = current.copy(speedBatchProgresses = updatedProgresses))
                }
            }
"""
# Insert before 'is TestEvent.RoundEnded ->'
content = content.replace("            is TestEvent.RoundEnded ->", speed_progress + "            is TestEvent.RoundEnded ->")

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/viewmodel/HomeScreenViewModel.kt', 'w') as f:
    f.write(content)

