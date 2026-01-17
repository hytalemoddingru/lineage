/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

/**
 * Thrown when a mod cannot be discovered or validated.
 */
class ModLoadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
