package vpn.util

import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// форматирование милисекунд к виду MM:SS
fun formatMillisToMMss(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d", minutes, secs)
}


// форматирование секунд к виду MM:SS
fun formatTimeToMinutesSeconds(i: Int): String {
    val duration = i.toDuration(DurationUnit.SECONDS)
    return duration.toComponents { minutes, seconds, _ ->
        String.format(Locale.getDefault(), "%01d:%02d", minutes, seconds)
    }
}
