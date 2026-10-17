package dev.dcesares.bifolio.parse

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Encoding detected for a source file (DOC-03). */
public enum class SourceEncoding { UTF_8, UTF_8_BOM, UTF_16_LE, UTF_16_BE, WINDOWS_1252 }

/**
 * Decoded file text. [encodingWarning] is true when the bytes were not valid UTF-8 and no BOM was present,
 * so the Windows-1252 fallback was used and characters may be wrong (DOC-03).
 */
public data class DecodedText(
    val text: String,
    val encoding: SourceEncoding,
    val encodingWarning: Boolean,
)

/** Byte decoding (DOC-03) and line-ending normalization (DOC-04). */
public object TextDecoder {
    private const val BOM_UTF8_LENGTH = 3
    private const val BOM_UTF16_LENGTH = 2
    private const val EF = 0xEF
    private const val BB = 0xBB
    private const val BF = 0xBF
    private const val FE = 0xFE
    private const val FF = 0xFF
    private const val BYTE_MASK = 0xFF

    private val windows1252: Charset = Charset.forName("windows-1252")

    /** Decodes [bytes]: UTF-8 with or without BOM, UTF-16 by BOM, otherwise Windows-1252 with a warning. */
    public fun decode(bytes: ByteArray): DecodedText =
        when {
            hasPrefix(bytes, EF, BB, BF) ->
                DecodedText(
                    String(bytes, BOM_UTF8_LENGTH, bytes.size - BOM_UTF8_LENGTH, Charsets.UTF_8),
                    SourceEncoding.UTF_8_BOM,
                    false,
                )
            hasPrefix(bytes, FF, FE) ->
                DecodedText(
                    String(bytes, BOM_UTF16_LENGTH, bytes.size - BOM_UTF16_LENGTH, Charsets.UTF_16LE),
                    SourceEncoding.UTF_16_LE,
                    false,
                )
            hasPrefix(bytes, FE, FF) ->
                DecodedText(
                    String(bytes, BOM_UTF16_LENGTH, bytes.size - BOM_UTF16_LENGTH, Charsets.UTF_16BE),
                    SourceEncoding.UTF_16_BE,
                    false,
                )
            else -> decodeUtf8OrFallback(bytes)
        }

    /** CRLF and CR become LF; nothing else changes. Returns the same instance when there is no CR (DOC-04). */
    public fun normalizeLineEndings(text: String): String {
        if (text.indexOf('\r') < 0) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\r') {
                sb.append('\n')
                if (i + 1 < text.length && text[i + 1] == '\n') i++
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun decodeUtf8OrFallback(bytes: ByteArray): DecodedText {
        val strict =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            DecodedText(strict.decode(ByteBuffer.wrap(bytes)).toString(), SourceEncoding.UTF_8, false)
        } catch (_: CharacterCodingException) {
            DecodedText(String(bytes, windows1252), SourceEncoding.WINDOWS_1252, true)
        }
    }

    private fun hasPrefix(
        bytes: ByteArray,
        vararg prefix: Int,
    ): Boolean = bytes.size >= prefix.size && prefix.indices.all { (bytes[it].toInt() and BYTE_MASK) == prefix[it] }
}
