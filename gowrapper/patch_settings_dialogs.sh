sed -i '/null -> {}/i \
        SettingsDialog.SpeedTestRounds -> {\
            NumberInputDialog(\
                title = "Speed Test Rounds",\
                initialValue = settings.speedTestRounds,\
                hint = "Number of rounds",\
                onDismiss = { mainVm.hideDialog() },\
                onSave = {\
                    settingsVm.updateSettings(settings.copy(speedTestRounds = it.coerceAtLeast(1)))\
                    true\
                }\
            )\
        }\
\
        SettingsDialog.SpeedTestProvider -> {\
            val providers = listOf("cloudflare")\
            SelectionDialog(\
                title = "Speed Test Provider",\
                items = providers,\
                selectedItem = settings.speedTestProvider,\
                onDismiss = { mainVm.hideDialog() },\
                onSelect = { settingsVm.updateSettings(settings.copy(speedTestProvider = it)) },\
                emptyText = "No providers available",\
                itemLabel = { it },\
                itemSecondaryLabel = { null }\
            )\
        }\
\
        SettingsDialog.SpeedTestMode -> {\
            val modes = listOf("download", "upload")\
            SelectionDialog(\
                title = "Speed Test Mode",\
                items = modes,\
                selectedItem = settings.speedTestMode,\
                onDismiss = { mainVm.hideDialog() },\
                onSelect = { settingsVm.updateSettings(settings.copy(speedTestMode = it)) },\
                emptyText = "No modes available",\
                itemLabel = { it },\
                itemSecondaryLabel = { null }\
            )\
        }\
\
        SettingsDialog.SpeedTestTargetBytes -> {\
            NumberInputDialog(\
                title = "Target Bytes",\
                initialValue = settings.speedTestTargetBytes.toInt(),\
                hint = "Number of bytes",\
                onDismiss = { mainVm.hideDialog() },\
                onSave = {\
                    settingsVm.updateSettings(settings.copy(speedTestTargetBytes = it.toLong().coerceAtLeast(1L)))\
                    true\
                }\
            )\
        }\
' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/SettingsScreen.kt
