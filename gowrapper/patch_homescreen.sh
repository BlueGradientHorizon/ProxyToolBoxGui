sed -i '/testProgress.batchProgresses.isNotEmpty()/a \
            if (testProgress.speedBatchProgresses.isNotEmpty()) {\
                val groupedSpeedBatches = testProgress.speedBatchProgresses.groupBy { it.batchNum }.toList()\
                    .sortedBy { it.first }\
\
                items(groupedSpeedBatches) { (batchNum, rounds) ->\
                    BatchTable(\
                        title = "Speed Test - " + stringResource(Res.string.batch_title, batchNum),\
                        headers = listOf(\
                            stringResource(Res.string.column_round),\
                            stringResource(Res.string.column_total),\
                            stringResource(Res.string.column_running),\
                            stringResource(Res.string.column_failed),\
                            stringResource(Res.string.column_succeeded)\
                        ),\
                        headerWeights = listOf(0.7f, 1f, 1f, 1f, 1f),\
                        rows = rounds.sortedBy { it.roundNum }.map { round ->\
                            listOf(\
                                round.roundNum.toString(),\
                                round.total.toString(),\
                                round.running.toString(),\
                                round.failed.toString(),\
                                round.succeeded.toString()\
                            )\
                        }\
                    )\
                }\
            }\
' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/HomeScreen.kt

# Also patch TestProgressBar to show phase info
sed -i 's/remaining, /remaining, if (progress.phase == 1) progress.speedCurrentBatch else /g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/HomeScreen.kt
sed -i 's/progress.currentBatch,/progress.currentBatch,/g' /home/engine/project/composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/HomeScreen.kt # skipped because replaced above
