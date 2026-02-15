/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.backend.auth

object AuthModePolicy {
    const val REQUIRED_AUTH_MESSAGE = "Server requires AUTHENTICATED mode."

    fun shouldRejectHandshake(requireAuthenticatedMode: Boolean, authModeAuthenticated: Boolean): Boolean {
        return requireAuthenticatedMode && !authModeAuthenticated
    }
}

