/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.mod

/**
 * Base class for Lineage mods.
 */
abstract class LineageMod {
    lateinit var context: ModContext
        private set

    fun init(context: ModContext) {
        check(!this::context.isInitialized) { "Mod context already initialized" }
        this.context = context
        onLoad(context)
    }

    protected open fun onLoad(context: ModContext) {
    }

    open fun onEnable() {
    }

    open fun onDisable() {
    }
}
