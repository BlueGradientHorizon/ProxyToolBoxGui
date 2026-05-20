sed -i '/module.subscriptionRepository.resetWorkingData()/a \
                module.subscriptionRepository.resetWorkingSpeedData() \
' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/viewmodel/HomeScreenViewModel.kt

sed -i '/module.subscriptionRepository.updateConfigTestResultsBatch(updates)/a \
\
                var finalConfigs = resultConfigs\
                if (currentSettings.performSpeedTest && resultConfigs.isNotEmpty()) {\
                    _uiState.update { state ->\
                        val totalBatches = if (currentSettings.testByBatches && currentSettings.batchSize > 0) {\
                            (resultConfigs.size + currentSettings.batchSize - 1) / currentSettings.batchSize\
                        } else 1\
                        val totalRounds = currentSettings.speedTestRounds\
                        val speedTotalSeconds = totalBatches * totalRounds * currentSettings.roundTimeout\
                        val current = state.testProgress\
                        state.copy(\
                            testProgress = current.copy(\
                                phase = 1,\
                                speedTotalBatches = totalBatches,\
                                speedTotalRounds = totalRounds,\
                                totalSeconds = current.totalSeconds + speedTotalSeconds,\
                                speedCurrentBatch = 0,\
                                speedCurrentRound = 0,\
                                speedBatchProgresses = (1..totalBatches).flatMap { b ->\
                                    (1..totalRounds).map { r -> BatchProgress(batchNum = b, roundNum = r) }\
                                }\
                            )\
                        )\
                    }\
                    val tags = resultConfigs.map { it.tag }\
                    val speedConfigs = module.testManager.runSpeedTests(\
                        settings = currentSettings,\
                        targetTags = tags\
                    ) { event ->\
                        if (job?.isActive != true) return@runSpeedTests\
                        handleTestEvent(event, currentSettings)\
                    }\
                    if (job?.isActive != true) return@launch\
                    val speedUpdates = speedConfigs.mapNotNull { cfg ->\
                        module.testManager.extractIds(cfg.tag)?.let { (subId, configId) ->\
                            com.bghorizon.proxytoolboxgui.data.db.ConfigSpeedTestResultUpdate(\
                                subId, configId, true, cfg.speed\
                            )\
                        }\
                    }\
                    module.subscriptionRepository.updateConfigSpeedTestResultsBatch(speedUpdates)\
                    finalConfigs = speedConfigs\
                }\
' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/viewmodel/HomeScreenViewModel.kt

