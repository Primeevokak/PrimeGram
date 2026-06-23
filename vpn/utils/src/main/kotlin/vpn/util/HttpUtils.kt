package vpn.util

fun splitPathAndQueryParams(url: String): Pair<String, Map<String, String>> {
    val parts = url.split("?")
    val path = parts[0]
    val queryParams = if (parts.size > 1) {
        parts[1].split("&").associate {
            val query = it.split("=")
            query[0] to query[1]
        }
    } else {
        emptyMap()
    }
    return Pair(path, queryParams)
}
