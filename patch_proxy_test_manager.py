import re

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/ProxyTestManager.kt', 'r') as f:
    content = f.read()

# Fix SpeedProgress
content = content.replace('    data class Error(val message: String) : TestEvent()', '    data class Error(val message: String) : TestEvent()\n    data class SpeedProgress(val tag: String, val speed: Double, val failed: Boolean) : TestEvent()')

# Move runSpeedTests inside ProxyTestManager class
match = re.search(r'(    suspend fun runSpeedTests\(.*)', content, re.DOTALL)
if match:
    run_speed = match.group(1)
    content = content.replace(run_speed, '')
    
    # Find the end of ProxyTestManager class
    # The class ProxyTestManager ends at "    fun extractIds" function's end brace.
    class_end_match = re.search(r'    fun extractIds\(tag: String\): Pair<String, Int>\? \{.*?return subId to configId\n    \}', content, re.DOTALL)
    
    if class_end_match:
        end_idx = class_end_match.end()
        # insert before the closing brace of the class
        content = content[:end_idx] + '\n\n' + run_speed + content[end_idx:]

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/ProxyTestManager.kt', 'w') as f:
    f.write(content)

