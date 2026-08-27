package com.example.foldermind

import java.util.regex.Pattern

object MarkdownUtils {
    /**
     * Toggles the state of the nth checkbox block in the provided markdown text.
     * A checkbox block is expected to match `[ ]` or `[x]` after a list marker.
     */
    fun toggleCheckbox(markdown: String, blockIndex: Int): String {
        val pattern = Pattern.compile("(?m)^(\\s*(?:-|\\*)\\s*\\[)([ xX])(\\]\\s+)")
        val matcher = pattern.matcher(markdown)
        var count = 0
        val sb = StringBuffer()
        while (matcher.find()) {
            if (count == blockIndex) {
                val currentState = matcher.group(2)
                val newState = if (currentState == " ") "x" else " "
                matcher.appendReplacement(sb, (matcher.group(1) ?: "") + newState + (matcher.group(3) ?: ""))
                matcher.appendTail(sb)
                return sb.toString()
            }
            count++
        }
        return markdown
    }
}
