package com.julesybean.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptUtilsTest {

    @Test
    fun testSanitizeFilename() {
        assertEquals("My_File_Name", PromptUtils.sanitizeFilename("My:File/Name"))
        assertEquals("Clean Title_", PromptUtils.sanitizeFilename("Clean Title?"))
        assertEquals("prompt", PromptUtils.sanitizeFilename("   "))
        assertEquals("Special_Chars___", PromptUtils.sanitizeFilename("Special*Chars<>|"))
    }

    @Test
    fun testGetDefaultTitle() {
        val title = PromptUtils.getDefaultTitle(1700000000000L)
        assertEquals("Untitled Prompt - 2023-11-14", title)
    }

    @Test
    fun testGetSnippet() {
        val multiline = "Line 1\nLine 2\n   Line 3"
        assertEquals("Line 1 Line 2 Line 3", PromptUtils.getSnippet(multiline, 100))

        val longText = "A".repeat(150)
        val snippet = PromptUtils.getSnippet(longText, 10)
        assertEquals("AAAAAAAAAA…", snippet)
    }
}
