/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.api.command

import ru.hytalemodding.lineage.api.permission.PermissionSubject

/**
 * Entity that can execute commands.
 */
interface CommandSender : PermissionSubject {
    val type: SenderType
    fun sendMessage(message: String)
}
