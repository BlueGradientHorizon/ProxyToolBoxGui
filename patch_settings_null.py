with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/SettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("itemSecondaryLabel = { null }", "itemSecondaryLabel = null")

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/ui/screens/SettingsScreen.kt', 'w') as f:
    f.write(content)
