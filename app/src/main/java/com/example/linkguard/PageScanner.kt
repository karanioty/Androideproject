package com.example.linkguard

import java.util.regex.Pattern

object PageScanner {

    // Regex to match URLs in screen text
    private val URL_PATTERN: Pattern = Pattern.compile(
        "(https?://[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+)|" +
        "(www\\.[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]+)|" +
        "([a-zA-Z0-9-]+\\.(com|org|net|io|co|in|edu|gov|xyz|top|info|biz|me|app|dev|link|site|club|online|store|tech|live|world|news|ai|tv|cc|us|uk|ca|de|fr|ru|jp|br|au|pro|cloud|vip|icu|space|fun|bar|shop|click|work|fit|zone|today|agency|host|website|mil)[a-zA-Z0-9-._~:/?#\\[\\]@!$&'()*+,;=%]*)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts all valid URLs from the visible screen text nodes.
     */
    fun extractUrls(screenTexts: List<String>): List<String> {
        val uniqueUrls = mutableSetOf<String>()

        for (text in screenTexts) {
            val matcher = URL_PATTERN.matcher(text)
            while (matcher.find()) {
                var found = matcher.group(0)?.trim() ?: continue

                // Clean trailing punctuation
                found = found.trimEnd('.', ',', ')', ']', ';', ':', '!', '?', '>', '<', '"', '\'')

                // Avoid false positives (like email addresses or file extensions without domain)
                if (found.contains("@") && !found.startsWith("http")) continue
                if (found.length < 5) continue

                if (found.contains(".")) {
                    val normalized = if (!found.startsWith("http://", ignoreCase = true) &&
                        !found.startsWith("https://", ignoreCase = true)
                    ) {
                        "https://$found"
                    } else {
                        found
                    }
                    uniqueUrls.add(normalized)
                }
            }
        }

        return uniqueUrls.toList()
    }
}
