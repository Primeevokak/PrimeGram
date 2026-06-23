package vpn.util

fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) {
        return false
    }
    return copyOfRange(0, prefix.size).contentEquals(prefix)
}
