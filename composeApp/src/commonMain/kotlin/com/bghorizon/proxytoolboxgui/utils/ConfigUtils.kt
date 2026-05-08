package com.bghorizon.proxytoolboxgui.utils

object ConfigUtils {
    /**
     * NaiveDeduplicateConfigsUris deduplicates repeated configs connection uris by comparing them 
     * with text after shebang (#) symbol excluded. The latter part is often called a remark in proxy clients. 
     * Doesn't work with base64-encrypted configs.
     * 
     * @param connUris List of URIs to deduplicate.
     * @param seen Optional mutable set to track already seen base URIs across multiple calls.
     * @return List of unique URIs.
     */
    fun naiveDeduplicate(connUris: List<String>, seen: MutableSet<String> = mutableSetOf()): List<String> {
        val unique = mutableListOf<String>()
        for (connUri in connUris) {
            val key = connUri.substringBefore('#')
            if (key !in seen) {
                seen.add(key)
                unique.add(connUri)
            }
        }
        return unique
    }
}
