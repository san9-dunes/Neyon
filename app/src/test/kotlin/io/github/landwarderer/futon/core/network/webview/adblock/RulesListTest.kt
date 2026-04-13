package io.github.landwarderer.futon.core.network.webview.adblock

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RulesListTest {

    @Test
    fun testDomainModifiers() {
        val rulesList = RulesList()
        // Rule that blocks "ad.com" but only when requested from "example.com" or its subdomains, except "foo.example.com"
        rulesList.add("ad.com*\$domain=example.com|~foo.example.com")

        // This should be blocked on example.com
        assertNotNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://example.com/page.html".toHttpUrl()))

        // This should be blocked on www.example.com
        assertNotNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://www.example.com/page.html".toHttpUrl()))

        // This should be allowed on foo.example.com (since it's in domainsNot)
        assertNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://foo.example.com/page.html".toHttpUrl()))

        // This should be allowed on other.com
        assertNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://other.com/page.html".toHttpUrl()))
    }

    @Test
    fun testDomainNotModifiers() {
        val rulesList = RulesList()
        // Rule that blocks "ad.com" everywhere except "example.com"
        rulesList.add("ad.com*\$domain=~example.com")

        // This should be allowed on example.com
        assertNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://example.com/page.html".toHttpUrl()))

        // This should be allowed on www.example.com
        assertNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://www.example.com/page.html".toHttpUrl()))

        // This should be blocked on other.com
        assertNotNull(rulesList.get("https://ad.com/script.js".toHttpUrl(), "https://other.com/page.html".toHttpUrl()))
    }
}
