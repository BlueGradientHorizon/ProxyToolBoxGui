import re

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Daos.kt', 'r') as f:
    content = f.read()

# Extract the methods
match = re.search(r'(    @Query\("UPDATE subscriptions_data SET workingSpeed = :workingSpeed, speed = :speed WHERE subId = :subId AND configId = :configId"\)\s+suspend fun updateConfigSpeedTestResult\(.*?\)\s+@Transaction\s+suspend fun updateConfigSpeedTestResultsBatch\(results: List<ConfigSpeedTestResultUpdate>\) \{.*?\}\s+@Query\("UPDATE subscriptions_data SET workingSpeed = 0, speed = -1\.0"\)\s+suspend fun resetWorkingSpeedData\(\))', content, re.DOTALL)

if match:
    methods = match.group(1)
    # Remove from original location
    content = content.replace(methods, '')
    
    # Insert before the last brace of SubscriptionDao interface
    # which is right before "data class SubscriptionWithStats"
    insert_point_match = re.search(r'    fun getSubscriptionsWithStatsFlow\(\): kotlinx\.coroutines\.flow\.Flow<List<SubscriptionWithStats>>\n\}', content)
    
    if insert_point_match:
        insert_idx = insert_point_match.start() + len('    fun getSubscriptionsWithStatsFlow(): kotlinx.coroutines.flow.Flow<List<SubscriptionWithStats>>\n')
        content = content[:insert_idx] + methods + '\n' + content[insert_idx:]

with open('composeApp/src/commonMain/kotlin/com/bghorizon/proxytoolboxgui/data/db/Daos.kt', 'w') as f:
    f.write(content)
