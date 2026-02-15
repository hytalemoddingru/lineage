/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.console

object StartupBanner {
    private const val RESET = "\u001B[0m"
    private const val PURPLE = "\u001B[95m"
    private const val BLUE = "\u001B[94m"

    private val codenameByVersion = mapOf(
        "0.4.0" to "Kiri",
        "0.5.0" to "Kage",
        "0.6.0" to "Sen",
        "0.7.0" to "Nami",
        "0.8.0" to "Yuki",
        "0.9.0" to "Sora",
        "1.0.0" to "Hayate",
        "1.1.0" to "Rai",
        "1.2.0" to "Jin",
        "1.3.0" to "Honō",
        "1.4.0" to "Tsuki",
        "1.5.0" to "Hoshi",
        "1.6.0" to "Arashi",
        "1.7.0" to "Sakura",
        "1.8.0" to "Hagane",
        "1.9.0" to "Tsubasa",
        "2.0.0" to "Tamashii",
        "2.1.0" to "Kizuna",
        "2.2.0" to "Yume",
        "2.3.0" to "Maboroshi",
    )

    fun print(version: String) {
        val codename = codenameByVersion[version] ?: "Unknown"
        val lines = listOf(
            "   ___                       ",
            "  / (_)__  ___ ___ ____ ____   $version 「$codename 」",
            " / / / _ \\/ -_) _ `/ _ `/ -_)",
            "/_/_/_//_/\\__/\\_,_/\\_, /\\__/ ",
            "   ___  _______ __/___/ __   ",
            "  / _ \\/ __/ _ \\\\ \\ / // /   ",
            " / .__/_/  \\___/_\\_\\\\_, /    ",
            "/_/                /___/     ",
        )
        lines.forEachIndexed { index, line ->
            val color = if (index % 2 == 0) PURPLE else BLUE
            println("$color$line$RESET")
        }
        println()
    }
}
