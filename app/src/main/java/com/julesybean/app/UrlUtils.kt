package com.julesybean.app

object UrlUtils {
    /**
     * Normalizes non-HTTPS schemes (jules://, julesybean://, http://) to https://.
     */
    fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("jules://") -> url.replaceFirst("jules://", "https://")
            url.startsWith("julesybean://") -> url.replaceFirst("julesybean://", "https://")
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
    }

    /**
     * Checks if the given host corresponds to an internal domain (jules.google.com or *.jules.google.com).
     */
    fun isInternalHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        return host == "jules.google.com" || host.endsWith(".jules.google.com")
    }

    /**
     * Checks if the given host corresponds to a Google authentication domain (e.g., accounts.google.com).
     */
    fun isAuthHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        return host == "accounts.google.com" ||
                host.endsWith(".accounts.google.com") ||
                host == "myaccount.google.com"
    }

    /**
     * Checks if navigation to the host is allowed inside the WebView (internal host or authentication host).
     */
    fun isAllowedHost(host: String?): Boolean {
        return isInternalHost(host) || isAuthHost(host)
    }
}
