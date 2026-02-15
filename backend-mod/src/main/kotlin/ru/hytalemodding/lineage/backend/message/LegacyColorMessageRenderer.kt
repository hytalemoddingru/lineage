/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.message

import com.hypixel.hytale.server.core.Message

/**
 * Converts legacy color codes and hex color markup into Hytale Message components.
 */
object LegacyColorMessageRenderer {
    private const val SECTION = '\u00A7'

    fun render(input: String): Message {
        if (input.isBlank()) {
            return Message.raw("")
        }
        val root = Message.empty()
        val buffer = StringBuilder(input.length)
        var color: String? = null
        var bold = false
        var italic = false

        fun flush() {
            if (buffer.isEmpty()) {
                return
            }
            var part = Message.raw(buffer.toString())
            color?.let { part = part.color(it) }
            if (bold) {
                part = part.bold(true)
            }
            if (italic) {
                part = part.italic(true)
            }
            root.insert(part)
            buffer.setLength(0)
        }

        var index = 0
        while (index < input.length) {
            val hexInline = parseInlineHex(input, index)
            if (hexInline != null) {
                flush()
                color = "#${hexInline.hex.lowercase()}"
                bold = false
                italic = false
                index += hexInline.length
                continue
            }

            if ((input[index] == SECTION || input[index] == '&') && index + 1 < input.length) {
                val sectionHex = parseSectionHex(input, index)
                if (sectionHex != null) {
                    flush()
                    color = "#${sectionHex.hex.lowercase()}"
                    bold = false
                    italic = false
                    index += sectionHex.length
                    continue
                }

                val code = input[index + 1].lowercaseChar()
                val colorHex = colorCodeToHex(code)
                if (colorHex != null) {
                    flush()
                    color = colorHex
                    bold = false
                    italic = false
                    index += 2
                    continue
                }
                when (code) {
                    'l' -> {
                        flush()
                        bold = true
                        index += 2
                        continue
                    }

                    'o' -> {
                        flush()
                        italic = true
                        index += 2
                        continue
                    }

                    'r' -> {
                        flush()
                        color = null
                        bold = false
                        italic = false
                        index += 2
                        continue
                    }
                }
            }
            buffer.append(input[index])
            index++
        }
        flush()
        return root
    }

    private fun parseInlineHex(input: String, index: Int): ParsedHex? {
        if (index + 8 < input.length && input[index] == '<' && input[index + 1] == '#' && input[index + 8] == '>') {
            val hex = input.substring(index + 2, index + 8)
            if (isHexColor(hex)) {
                return ParsedHex(hex, 9)
            }
        }
        if (index + 7 < input.length && input[index] == '&' && input[index + 1] == '#') {
            val hex = input.substring(index + 2, index + 8)
            if (isHexColor(hex)) {
                return ParsedHex(hex, 8)
            }
        }
        return null
    }

    private fun parseSectionHex(input: String, start: Int): ParsedHex? {
        val marker = input[start]
        if (start + 13 >= input.length || input[start + 1].lowercaseChar() != 'x') {
            return null
        }
        val hex = StringBuilder(6)
        var cursor = start + 2
        repeat(6) {
            if (cursor + 1 >= input.length || input[cursor] != marker) {
                return null
            }
            val nibble = input[cursor + 1]
            if (!isHexDigit(nibble)) {
                return null
            }
            hex.append(nibble)
            cursor += 2
        }
        return ParsedHex(hex.toString(), cursor - start)
    }

    private fun isHexColor(value: String): Boolean {
        if (value.length != 6) {
            return false
        }
        return value.all(::isHexDigit)
    }

    private fun isHexDigit(ch: Char): Boolean {
        return ch.isDigit() || ch.lowercaseChar() in 'a'..'f'
    }

    private fun colorCodeToHex(code: Char): String? {
        return when (code) {
            '0' -> "#000000"
            '1' -> "#0000AA"
            '2' -> "#00AA00"
            '3' -> "#00AAAA"
            '4' -> "#AA0000"
            '5' -> "#AA00AA"
            '6' -> "#FFAA00"
            '7' -> "#AAAAAA"
            '8' -> "#555555"
            '9' -> "#5555FF"
            'a' -> "#55FF55"
            'b' -> "#55FFFF"
            'c' -> "#FF5555"
            'd' -> "#FF55FF"
            'e' -> "#FFFF55"
            'f' -> "#FFFFFF"
            else -> null
        }
    }

    private data class ParsedHex(
        val hex: String,
        val length: Int,
    )
}
