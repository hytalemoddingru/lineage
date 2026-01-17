/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import java.net.URL
import java.net.URLClassLoader

/**
 * Class loader that prefers mod classes while delegating core packages to the parent.
 */
class ModClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    private val parentFirstPrefixes = listOf(
        "java.",
        "javax.",
        "kotlin.",
        "kotlinx.",
        "org.slf4j.",
        "ru.hytalemodding.lineage.api.",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (parentFirstPrefixes.any { name.startsWith(it) }) {
            return super.loadClass(name, resolve)
        }
        synchronized(getClassLoadingLock(name)) {
            val loaded = findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }
            return try {
                val clazz = findClass(name)
                if (resolve) {
                    resolveClass(clazz)
                }
                clazz
            } catch (ex: ClassNotFoundException) {
                super.loadClass(name, resolve)
            }
        }
    }
}
