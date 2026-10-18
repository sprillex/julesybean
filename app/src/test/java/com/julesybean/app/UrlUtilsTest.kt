package com.julesybean.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun testNormalizeUrlWithJulesScheme() {
        val input = "jules://jules.google.com/chat/12345"
        val expected = "https://jules.google.com/chat/12345"
        assertEquals(expected, UrlUtils.normalizeUrl(input))
    }

    @Test
    fun testNormalizeUrlWithJulesybeanScheme() {
        val input = "julesybean://jules.google.com/chat/12345"
        val expected = "https://jules.google.com/chat/12345"
        assertEquals(expected, UrlUtils.normalizeUrl(input))
    }

    @Test
    fun testNormalizeUrlWithHttpScheme() {
        val input = "http://jules.google.com/chat/12345"
        val expected = "https://jules.google.com/chat/12345"
        assertEquals(expected, UrlUtils.normalizeUrl(input))
    }

    @Test
    fun testNormalizeUrlWithHttpsSchemeUnchanged() {
        val input = "https://jules.google.com/chat/12345"
        val expected = "https://jules.google.com/chat/12345"
        assertEquals(expected, UrlUtils.normalizeUrl(input))
    }

    @Test
    fun testIsInternalHost() {
        assertTrue(UrlUtils.isInternalHost("jules.google.com"))
        assertTrue(UrlUtils.isInternalHost("sub.jules.google.com"))
        assertTrue(UrlUtils.isInternalHost("deep.sub.jules.google.com"))

        assertFalse(UrlUtils.isInternalHost("google.com"))
        assertFalse(UrlUtils.isInternalHost("notjules.google.com"))
        assertFalse(UrlUtils.isInternalHost("example.com"))
        assertFalse(UrlUtils.isInternalHost(null))
        assertFalse(UrlUtils.isInternalHost(""))
    }
}
