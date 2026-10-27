package com.julesybean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PromptDatabaseHelperTest {

    @Test
    fun testPromptModelCreationAndDefaults() {
        val now = System.currentTimeMillis()
        val prompt = Prompt(
            id = 1,
            title = "Sample Prompt",
            content = "This is a prompt content body.",
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null,
            sortOrder = 0
        )

        assertEquals(1L, prompt.id)
        assertEquals("Sample Prompt", prompt.title)
        assertEquals("This is a prompt content body.", prompt.content)
        assertEquals(now, prompt.createdAt)
        assertEquals(now, prompt.updatedAt)
        assertNull(prompt.lastUsedAt)
        assertEquals(0, prompt.sortOrder)
    }

    @Test
    fun testSortTypesEnum() {
        val sortTypes = PromptSortType.values()
        assertEquals(3, sortTypes.size)
        assertEquals(PromptSortType.ALPHABETICAL, sortTypes[0])
        assertEquals(PromptSortType.RECENTLY_USED, sortTypes[1])
        assertEquals(PromptSortType.DATE_CREATED, sortTypes[2])
    }
}
