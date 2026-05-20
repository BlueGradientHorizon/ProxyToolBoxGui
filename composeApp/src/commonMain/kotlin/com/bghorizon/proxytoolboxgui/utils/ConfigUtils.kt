package com.bghorizon.proxytoolboxgui.utils

object ConfigUtils {
    /**
     * Deduplicates repeated configs connection uris by comparing them
     * with text after shebang (#) symbol excluded. The latter part is often called a remark in proxy clients. 
     * Doesn't work with base64-encrypted configs, which internally differ only by notes.
     *
     * @param items List of items to deduplicate.
     * @param selector Function to extract the connection URI from an item.
     * @param seen Optional mutable set to track already seen base URIs across multiple calls.
     * @return List of unique items.
     */
    fun <T> naiveDeduplicateByConnUri(
        items: List<T>,
        selector: (T) -> String,
        seen: MutableSet<String> = mutableSetOf()
    ): List<T> {
        val unique = mutableListOf<T>()
        for (item in items) {
            val connUri = selector(item)
            val key = connUri.substringBefore('#')
            if (key !in seen) {
                seen.add(key)
                unique.add(item)
            }
        }
        return unique
    }

    fun generateUUID(): String {
        val hexChars = "0123456789abcdef"
        val random = kotlin.random.Random.Default
        fun randomHex(length: Int) = buildString {
            repeat(length) { append(hexChars[random.nextInt(16)]) }
        }
        return "${randomHex(8)}-${randomHex(4)}-4${randomHex(3)}-${
            (8 + random.nextInt(4)).toString(
                16
            )
        }${randomHex(3)}-${randomHex(12)}"
    }
}
