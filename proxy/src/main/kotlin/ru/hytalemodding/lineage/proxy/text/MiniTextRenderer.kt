/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.text

import kotlin.math.roundToInt

/**
 * Safe bounded text renderer for proxy/system messages.
 *
 * Supported syntax (v1):
 * - legacy: `&a`, `&l`, `&r` and `§` equivalents
 * - hex: `<#RRGGBB>`, `&#RRGGBB`, section-hex `§x§R§R§G§G§B§B`
 * - tags: `<red>...</red>`, `<bold>...</bold>`, `<italic>...</italic>`, `<underline>...</underline>`
 * - gradient: `<gradient:#RRGGBB:#RRGGBB>text</gradient>`
 */
object MiniTextRenderer {
    private const val COLOR_CHAR = '\u00A7'
    private const val ANSI_ESC = "\u001B["
    private const val ANSI_RESET = "${ANSI_ESC}0m"

    fun render(
        input: String,
        profile: RenderProfile,
        limits: RenderLimits = RenderLimits(),
    ): String {
        if (input.isEmpty()) {
            return input
        }
        val bounded = if (input.length > limits.maxInputLength) {
            input.substring(0, limits.maxInputLength)
        } else {
            input
        }
        val output = StringBuilder(bounded.length + 32)
        val state = RenderState(
            profile = profile,
            limits = limits,
            input = bounded,
            output = output,
        )
        renderInternal(state, start = 0, end = bounded.length, depth = 0)
        if (profile == RenderProfile.CONSOLE && state.consoleStyled) {
            output.append(ANSI_RESET)
        }
        return output.toString()
    }

    private fun renderInternal(
        state: RenderState,
        start: Int,
        end: Int,
        depth: Int,
    ) {
        var index = start
        while (index < end) {
            val current = state.input[index]

            if ((current == '&' || current == COLOR_CHAR) && index + 1 < end) {
                val consumed = consumeLegacySequence(state, index, end)
                if (consumed > 0) {
                    index += consumed
                    continue
                }
            }

            if (current == '<') {
                val consumed = consumeTag(state, index, end, depth)
                if (consumed > 0) {
                    index += consumed
                    continue
                }
            }

            state.output.append(current)
            index++
        }
    }

    private fun consumeLegacySequence(state: RenderState, index: Int, end: Int): Int {
        val marker = state.input[index]
        val next = state.input[index + 1]

        if (next == '#' && index + 7 < end) {
            val hex = state.input.substring(index + 2, index + 8)
            if (isHexColor(hex)) {
                applyStyle(state, state.currentStyle.withHexColor(hex))
                return 8
            }
        }

        if ((next == 'x' || next == 'X') && index + 13 < end) {
            val parsed = parseSectionHex(state.input, index, marker)
            if (parsed != null) {
                applyStyle(state, state.currentStyle.withHexColor(parsed))
                return 14
            }
        }

        val code = next.lowercaseChar()
        if (code !in LEGACY_CODES) {
            return 0
        }
        val style = when (code) {
            'r' -> TextStyle.DEFAULT
            in LEGACY_COLORS -> state.currentStyle.withLegacyColor(code)
            'l' -> state.currentStyle.copy(bold = true)
            'o' -> state.currentStyle.copy(italic = true)
            'n' -> state.currentStyle.copy(underline = true)
            'm' -> state.currentStyle.copy(strikethrough = true)
            'k' -> state.currentStyle.copy(obfuscated = true)
            else -> state.currentStyle
        }
        applyStyle(state, style)
        return 2
    }

    private fun consumeTag(
        state: RenderState,
        index: Int,
        end: Int,
        depth: Int,
    ): Int {
        val close = state.input.indexOf('>', index + 1)
        if (close <= index || close >= end) {
            return 0
        }
        if (close - index > state.limits.maxTagLength) {
            return 0
        }
        val rawTag = state.input.substring(index + 1, close).trim()
        if (rawTag.isEmpty()) {
            return 0
        }
        if (rawTag.startsWith("/")) {
            val tagName = normalizeTagName(rawTag.substring(1))
            return if (closeTag(state, tagName)) close - index + 1 else 0
        }
        if (rawTag.startsWith("#") && isHexColor(rawTag.substring(1))) {
            applyStyle(state, state.currentStyle.withHexColor(rawTag.substring(1)))
            return close - index + 1
        }
        if (rawTag.startsWith("gradient:", ignoreCase = true)) {
            val consumed = consumeGradientTag(state, index, close, end, rawTag, depth)
            if (consumed > 0) {
                return consumed
            }
            return 0
        }

        val tagName = normalizeTagName(rawTag)
        if (tagName == "reset") {
            applyStyle(state, TextStyle.DEFAULT)
            return close - index + 1
        }
        if (state.stack.size >= state.limits.maxNestingDepth) {
            return 0
        }
        val nextStyle = styleForOpenTag(state.currentStyle, tagName) ?: return 0
        state.stack.addLast(StackEntry(tag = tagName, previousStyle = state.currentStyle))
        applyStyle(state, nextStyle)
        return close - index + 1
    }

    private fun consumeGradientTag(
        state: RenderState,
        openStart: Int,
        openEnd: Int,
        end: Int,
        rawTag: String,
        depth: Int,
    ): Int {
        if (state.stack.size >= state.limits.maxNestingDepth) {
            return 0
        }
        val parts = rawTag.split(':')
        if (parts.size != 3) {
            return 0
        }
        val startHex = parts[1].trim().removePrefix("#")
        val endHex = parts[2].trim().removePrefix("#")
        if (!isHexColor(startHex) || !isHexColor(endHex)) {
            return 0
        }
        val contentStart = openEnd + 1
        val closeTag = "</gradient>"
        val closeIndex = state.input.indexOf(closeTag, contentStart)
        if (closeIndex < 0 || closeIndex > end) {
            return 0
        }

        val originalStyle = state.currentStyle
        val plainInner = render(
            input = state.input.substring(contentStart, closeIndex),
            profile = RenderProfile.PLAIN,
            limits = state.limits,
        )
        val boundedText = if (plainInner.length > state.limits.maxGradientChars) {
            plainInner.substring(0, state.limits.maxGradientChars)
        } else {
            plainInner
        }
        appendGradientText(state, boundedText, startHex, endHex, originalStyle)
        applyStyle(state, originalStyle)
        return closeIndex + closeTag.length - openStart
    }

    private fun appendGradientText(
        state: RenderState,
        text: String,
        startHex: String,
        endHex: String,
        baseStyle: TextStyle,
    ) {
        if (text.isEmpty()) {
            return
        }
        val count = text.length
        for ((index, ch) in text.withIndex()) {
            val ratio = if (count <= 1) 0.0 else index.toDouble() / (count - 1).toDouble()
            val stepHex = lerpHex(startHex, endHex, ratio)
            applyStyle(state, baseStyle.withHexColor(stepHex))
            state.output.append(ch)
        }
    }

    private fun closeTag(state: RenderState, tagName: String): Boolean {
        if (state.stack.isEmpty()) {
            return false
        }
        val last = state.stack.last()
        if (last.tag != tagName) {
            return false
        }
        state.stack.removeLast()
        applyStyle(state, last.previousStyle)
        return true
    }

    private fun styleForOpenTag(base: TextStyle, tag: String): TextStyle? {
        val colorCode = NAMED_COLOR_CODES[tag]
        if (colorCode != null) {
            return base.withLegacyColor(colorCode)
        }
        return when (tag) {
            "bold" -> base.copy(bold = true)
            "italic" -> base.copy(italic = true)
            "underline", "underlined" -> base.copy(underline = true)
            "strikethrough" -> base.copy(strikethrough = true)
            "obfuscated" -> base.copy(obfuscated = true)
            else -> null
        }
    }

    private fun applyStyle(state: RenderState, style: TextStyle) {
        state.currentStyle = style
        when (state.profile) {
            RenderProfile.GAME -> state.output.append(renderGameStyle(style))
            RenderProfile.CONSOLE -> {
                state.output.append(renderConsoleStyle(style))
                state.consoleStyled = true
            }
            RenderProfile.PLAIN -> Unit
        }
    }

    private fun renderGameStyle(style: TextStyle): String {
        val output = StringBuilder(24)
        output.append(COLOR_CHAR).append('r')
        style.legacyColorCode?.let { output.append(COLOR_CHAR).append(it) }
        style.hexColor?.let { appendSectionHex(output, it) }
        if (style.bold) output.append(COLOR_CHAR).append('l')
        if (style.italic) output.append(COLOR_CHAR).append('o')
        if (style.underline) output.append(COLOR_CHAR).append('n')
        if (style.strikethrough) output.append(COLOR_CHAR).append('m')
        if (style.obfuscated) output.append(COLOR_CHAR).append('k')
        return output.toString()
    }

    private fun renderConsoleStyle(style: TextStyle): String {
        val output = StringBuilder(24)
        output.append(ANSI_RESET)
        val hex = style.hexColor ?: style.legacyColorCode?.let { LEGACY_HEX_COLORS[it] }
        if (hex != null) {
            val red = hex.substring(0, 2).toInt(16)
            val green = hex.substring(2, 4).toInt(16)
            val blue = hex.substring(4, 6).toInt(16)
            output.append("${ANSI_ESC}38;2;${red};${green};${blue}m")
        }
        if (style.bold) output.append("${ANSI_ESC}1m")
        if (style.italic) output.append("${ANSI_ESC}3m")
        if (style.underline) output.append("${ANSI_ESC}4m")
        if (style.strikethrough) output.append("${ANSI_ESC}9m")
        return output.toString()
    }

    private fun parseSectionHex(input: String, start: Int, marker: Char): String? {
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
            hex.append(nibble.lowercaseChar())
            cursor += 2
        }
        return hex.toString()
    }

    private fun appendSectionHex(output: StringBuilder, hex: String) {
        output.append(COLOR_CHAR).append('x')
        for (ch in hex.lowercase()) {
            output.append(COLOR_CHAR).append(ch)
        }
    }

    private fun normalizeTagName(tag: String): String {
        return tag.trim().lowercase().replace("-", "_")
    }

    private fun isHexColor(value: String): Boolean {
        return value.length == 6 && value.all(::isHexDigit)
    }

    private fun isHexDigit(ch: Char): Boolean {
        return ch.isDigit() || ch.lowercaseChar() in 'a'..'f'
    }

    private fun lerpHex(startHex: String, endHex: String, ratio: Double): String {
        val r1 = startHex.substring(0, 2).toInt(16)
        val g1 = startHex.substring(2, 4).toInt(16)
        val b1 = startHex.substring(4, 6).toInt(16)
        val r2 = endHex.substring(0, 2).toInt(16)
        val g2 = endHex.substring(2, 4).toInt(16)
        val b2 = endHex.substring(4, 6).toInt(16)
        val r = (r1 + (r2 - r1) * ratio).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * ratio).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * ratio).roundToInt().coerceIn(0, 255)
        return "%02x%02x%02x".format(r, g, b)
    }

    private data class RenderState(
        val profile: RenderProfile,
        val limits: RenderLimits,
        val input: String,
        val output: StringBuilder,
        val stack: ArrayDeque<StackEntry> = ArrayDeque(),
        var currentStyle: TextStyle = TextStyle.DEFAULT,
        var consoleStyled: Boolean = false,
    )

    private data class StackEntry(
        val tag: String,
        val previousStyle: TextStyle,
    )

    private data class TextStyle(
        val legacyColorCode: Char? = null,
        val hexColor: String? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val obfuscated: Boolean = false,
    ) {
        fun withLegacyColor(code: Char): TextStyle {
            return copy(
                legacyColorCode = code.lowercaseChar(),
                hexColor = null,
                bold = false,
                italic = false,
                underline = false,
                strikethrough = false,
                obfuscated = false,
            )
        }

        fun withHexColor(hex: String): TextStyle {
            return copy(
                legacyColorCode = null,
                hexColor = hex.lowercase(),
                bold = false,
                italic = false,
                underline = false,
                strikethrough = false,
                obfuscated = false,
            )
        }

        companion object {
            val DEFAULT = TextStyle()
        }
    }

    private val LEGACY_CODES = setOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
        'k', 'l', 'm', 'n', 'o', 'r',
    )
    private val LEGACY_COLORS = setOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f',
    )
    private val LEGACY_HEX_COLORS: Map<Char, String> = mapOf(
        '0' to "000000",
        '1' to "0000aa",
        '2' to "00aa00",
        '3' to "00aaaa",
        '4' to "aa0000",
        '5' to "aa00aa",
        '6' to "ffaa00",
        '7' to "aaaaaa",
        '8' to "555555",
        '9' to "5555ff",
        'a' to "55ff55",
        'b' to "55ffff",
        'c' to "ff5555",
        'd' to "ff55ff",
        'e' to "ffff55",
        'f' to "ffffff",
    )
    private val NAMED_COLOR_CODES: Map<String, Char> = mapOf(
        "black" to '0',
        "dark_blue" to '1',
        "dark_green" to '2',
        "dark_aqua" to '3',
        "dark_red" to '4',
        "dark_purple" to '5',
        "gold" to '6',
        "gray" to '7',
        "grey" to '7',
        "dark_gray" to '8',
        "dark_grey" to '8',
        "blue" to '9',
        "green" to 'a',
        "aqua" to 'b',
        "red" to 'c',
        "light_purple" to 'd',
        "yellow" to 'e',
        "white" to 'f',
    )
}

enum class RenderProfile {
    GAME,
    CONSOLE,
    PLAIN,
}

data class RenderLimits(
    val maxInputLength: Int = 4096,
    val maxNestingDepth: Int = 16,
    val maxGradientChars: Int = 512,
    val maxTagLength: Int = 96,
)
