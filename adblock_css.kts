data class CssRule(
    val domains: List<String>,
    val notDomains: List<String>,
    val selector: String
) {
    fun matches(baseUrl: okhttp3.HttpUrl?): Boolean {
        if (baseUrl == null) {
            return domains.isEmpty()
        }
        val host = baseUrl.host
        val topDomain = baseUrl.topPrivateDomain() ?: host

        if (notDomains.isNotEmpty()) {
            if (notDomains.any { host == it || host.endsWith(".$it") }) {
                return false
            }
        }

        if (domains.isNotEmpty()) {
            if (!domains.any { host == it || host.endsWith(".$it") }) {
                return false
            }
        }

        return true
    }
}
