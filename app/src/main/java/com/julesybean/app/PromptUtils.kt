package com.julesybean.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PromptUtils {

    /**
     * Converts a database prompt title into a sanitized string safe for filesystems across platforms.
     */
    fun sanitizeFilename(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            return "prompt"
        }
        // Replace reserved characters for Windows/Linux/macOS/Android filesystem: \ / : * ? " < > |
        var safe = trimmed.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
        // Trim trailing periods and spaces which are invalid in some OS filenames
        safe = safe.trimEnd('.', ' ')
        return if (safe.isEmpty()) "prompt" else safe
    }

    /**
     * Generates a default prompt title when title is left blank by the user.
     */
    fun getDefaultTitle(timestamp: Long = System.currentTimeMillis()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(Date(timestamp))
        return "Untitled Prompt - $dateStr"
    }

    /**
     * Creates a single-line or multi-line truncated preview snippet from full content.
     */
    fun getSnippet(content: String, maxLength: Int = 100): String {
        val singleLine = content.replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= maxLength) {
            singleLine
        } else {
            singleLine.substring(0, maxLength).trimEnd() + "…"
        }
    }

    /**
     * Formats epoch timestamp for display in UI.
     */
    fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
}
