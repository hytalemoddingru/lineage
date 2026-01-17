/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.permission

import ru.hytalemodding.lineage.api.permission.PermissionChecker
import ru.hytalemodding.lineage.api.permission.PermissionSubject
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory permission checker with simple wildcard support.
 */
class PermissionCheckerImpl : PermissionChecker {
    private val permissions = ConcurrentHashMap<String, MutableSet<String>>()

    override fun hasPermission(subject: PermissionSubject, permission: String): Boolean {
        if (permission.isBlank()) {
            return true
        }
        if (isConsole(subject)) {
            return true
        }
        val granted = permissions[normalize(subject.name)] ?: return false
        return granted.any { matches(it, permission) }
    }

    fun grant(subject: PermissionSubject, permission: String) {
        if (permission.isBlank()) {
            return
        }
        permissions.computeIfAbsent(normalize(subject.name)) { mutableSetOf() }.add(permission)
    }

    fun revoke(subject: PermissionSubject, permission: String) {
        permissions[normalize(subject.name)]?.remove(permission)
    }

    fun clear(subject: PermissionSubject) {
        permissions.remove(normalize(subject.name))
    }

    fun list(subject: PermissionSubject): Set<String> {
        return permissions[normalize(subject.name)]?.toSet() ?: emptySet()
    }

    fun load(permissions: Map<String, Set<String>>) {
        this.permissions.clear()
        for ((subject, granted) in permissions) {
            val cleaned = granted.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            if (cleaned.isNotEmpty()) {
                this.permissions[normalize(subject)] = cleaned
            }
        }
    }

    fun snapshot(): Map<String, Set<String>> {
        return permissions.mapValues { it.value.toSet() }
    }

    private fun matches(granted: String, requested: String): Boolean {
        if (granted == "*") {
            return true
        }
        if (granted == requested) {
            return true
        }
        if (granted.endsWith(".*")) {
            val prefix = granted.removeSuffix(".*")
            return requested.startsWith(prefix)
        }
        return false
    }

    private fun isConsole(subject: PermissionSubject): Boolean {
        return normalize(subject.name) == "console"
    }

    private fun normalize(name: String): String {
        return name.trim().lowercase()
    }
}
