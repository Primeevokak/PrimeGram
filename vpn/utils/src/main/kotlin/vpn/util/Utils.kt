package vpn.util

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val QT_HEADER = byteArrayOf(0x00, 0x00, 0x00, 0xc3.toByte()) // Magic qt header

fun encode(inputData: String): String {
    /**
     * Encodes the input data using a predefined constant header.
     * @param inputData: The string to encode
     * @return: Base64 encoded string
     */
    val compressedData = compress(inputData.toByteArray(Charsets.UTF_8))
    val qtCompressedData = QT_HEADER + compressedData

    val base64Data = Base64.getUrlEncoder().withoutPadding().encodeToString(qtCompressedData)
    return base64Data
}

fun decode(base64Data: String): String {
    /**
     * Decodes a Base64 encoded string with a predefined constant header.
     * @param base64Data: The Base64 encoded string
     * @return: The original decoded string
     */
    val paddedBase64Data = base64Data + "=".repeat((4 - base64Data.length % 4) % 4)
    val qtCompressedData = Base64.getUrlDecoder().decode(paddedBase64Data)

    if (!qtCompressedData.startsWith(QT_HEADER)) {
        throw IllegalArgumentException("Invalid header in encoded data.")
    }
    val compressedData = qtCompressedData.copyOfRange(QT_HEADER.size, qtCompressedData.size)

    val originalData = decompress(compressedData)
    return String(originalData, Charsets.UTF_8)
}

private fun compress(data: ByteArray): ByteArray {
    val deflater = Deflater(9)
    deflater.setInput(data)
    deflater.finish()

    val outputStream = ByteArrayOutputStream(data.size)
    val buffer = ByteArray(1024)
    while (!deflater.finished()) {
        val count = deflater.deflate(buffer)
        outputStream.write(buffer, 0, count)
    }
    outputStream.close()
    return outputStream.toByteArray()
}

private fun decompress(data: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)

    val outputStream = ByteArrayOutputStream(data.size)
    val buffer = ByteArray(1024)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput()) break
                if (inflater.needsDictionary()) {
                    throw IllegalArgumentException("Dictionary required")
                }
            }
            outputStream.write(buffer, 0, count)
        }
    } catch (e: DataFormatException) {
        throw IllegalArgumentException("Invalid compressed data.", e)
    } finally {
        outputStream.close()
    }
    return outputStream.toByteArray()
}

/**
 * Пояснение как это регулярное выражение работает от gemini, его сгенерившей:
 * 1. ^: Это начало строки. Мы убеждаемся, что шаблон начинается с самого начала.
 * 2. [^@]+: Это означает один или более символов, которые НЕ являются символом "@".
 *     2.1. [^@]: Любой символ, кроме @.
 *     2.2. +: Означает "один или более раз".
 *     2.3. Условие 1: Это гарантирует, что символ "@" не является первым в строке.
 * 3. @: Соответствует одному единственному символу "@".
 *     3.1. Условие 2: Это явно проверяет наличие только одного "@".
 *     (За счет того, что [^@]+ не содержит @, а последующие части выражения тоже исключают @).
 * 4. [^.@]+: Это означает один или более символов, которые НЕ являются символами "@" или ".".
 *     4.1. [^.@]: Любой символ, кроме . и @.
 *     4.2. +: Означает "один или более раз".
 *     4.3. Условие 3: Это гарантирует, что между символами "@" и первым символом "."
 *     есть хотя бы один другой символ.
 *     4.5. Условие 4: Также обеспечивает, что сразу после @ нет . или другого @.
 * 5. (\.[^.]+)+: Это самая важная часть для обработки доменных имен,
 * таких как example.com или domain.co.uk.
 *     5.1. \.: Соответствует одному символу точки (точка экранирована,
 *     так как это специальный символ в регулярных выражениях).
 *     5.2.[^.]+: Соответствует одному или более символов, которые НЕ являются точкой. Это значит,
 *     что после каждой точки должен быть хотя бы один символ, который не является точкой.
 *     5.3. (...): Скобки создают группу.
 *     5.4. + (в конце группы): Означает, что эта группа (точка, за которой следуют не-точки)
 *     должна встречаться один или более раз.
 *         5.4.1. Условие 5: Это гарантирует, что после символа "@" в строке встречается хотя бы
 *         один символ "." (поскольку группа (\.[^.]+) должна встретиться хотя бы один раз).
 *         5.4.2. Условие 6: Так как [^.]+ требует минимум один символ после каждой точки,
 *         это также гарантирует, что символ "." не является последним в строке
 *         (поскольку самая последняя группа (\.[^.]+) закончится на [^.]+, а не на \.).
 * 6. $: Это конец строки.
 * 7. \: Экранирует спецсимволы
 */
fun isEmailValid(email: String): Boolean {
    val regexPattern = "^[^@]+@[^.@]+(\\.[^.]+)+$".toRegex()
    return regexPattern.matches(email)
}

