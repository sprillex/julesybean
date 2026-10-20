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
    fun testNormalizeUrlWithJulesSchemeQueryParams() {
        val input = "jules://jules.google.com/chat?q=test&mode=dark"
        val expected = "https://jules.google.com/chat?q=test&mode=dark"
        assertEquals(expected, UrlUtils.normalizeUrl(input))
    }

    @Test
    fun testNormalizeUrlWithJulesSchemeSubdomain() {
        val input = "jules://sub.jules.google.com/page"
        val expected = "https://sub.jules.google.com/page"
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

    @Test
    fun testIsAuthHost() {
        assertTrue(UrlUtils.isAuthHost("accounts.google.com"))
        assertTrue(UrlUtils.isAuthHost("sub.accounts.google.com"))
        assertTrue(UrlUtils.isAuthHost("myaccount.google.com"))

        assertFalse(UrlUtils.isAuthHost("google.com"))
        assertFalse(UrlUtils.isAuthHost("jules.google.com"))
        assertFalse(UrlUtils.isAuthHost("example.com"))
        assertFalse(UrlUtils.isAuthHost(null))
        assertFalse(UrlUtils.isAuthHost(""))
    }

    @Test
    fun testIsAllowedHost() {
        assertTrue(UrlUtils.isAllowedHost("jules.google.com"))
        assertTrue(UrlUtils.isAllowedHost("sub.jules.google.com"))
        assertTrue(UrlUtils.isAllowedHost("accounts.google.com"))
        assertTrue(UrlUtils.isAllowedHost("myaccount.google.com"))

        assertFalse(UrlUtils.isAllowedHost("google.com"))
        assertFalse(UrlUtils.isAllowedHost("example.com"))
        assertFalse(UrlUtils.isAllowedHost(null))
        assertFalse(UrlUtils.isAllowedHost(""))
    }
}
