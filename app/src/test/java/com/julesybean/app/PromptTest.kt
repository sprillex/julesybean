package com.julesybean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun testPromptDataClassCopy() {
        val original = Prompt(
            id = 10,
            title = "Original Title",
            content = "Original Content",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val updated = original.copy(
            title = "New Title",
            updatedAt = 2000L
        )

        assertEquals(10L, updated.id)
        assertEquals("New Title", updated.title)
        assertEquals("Original Content", updated.content)
        assertEquals(1000L, updated.createdAt)
        assertEquals(2000L, updated.updatedAt)
    }

    @Test
    fun testFilenameSanitizationEdgeCases() {
        assertEquals("prompt", PromptUtils.sanitizeFilename(""))
        assertEquals("prompt", PromptUtils.sanitizeFilename("....   "))
        assertEquals("Hello_World", PromptUtils.sanitizeFilename("Hello/World"))
        assertEquals("Prompt_#1", PromptUtils.sanitizeFilename("Prompt?#1"))
    }

    @Test
    fun testSnippetFormatting() {
        val shortText = "Quick prompt"
        assertEquals("Quick prompt", PromptUtils.getSnippet(shortText))

        val whitespaceText = "  Multiple \n\n  spaces   and \t lines  "
        assertEquals("Multiple spaces and lines", PromptUtils.getSnippet(whitespaceText))
    }
}
