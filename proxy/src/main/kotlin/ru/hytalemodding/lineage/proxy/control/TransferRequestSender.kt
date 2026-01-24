/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.control

import ru.hytalemodding.lineage.shared.control.TransferRequest

interface TransferRequestSender {
    fun sendTransferRequest(request: TransferRequest): Boolean
}
