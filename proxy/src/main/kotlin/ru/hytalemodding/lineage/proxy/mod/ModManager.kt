/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import ru.hytalemodding.lineage.api.mod.LineageMod
import ru.hytalemodding.lineage.api.mod.ModContext
import ru.hytalemodding.lineage.api.mod.ModInfo
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and manages mod lifecycle.
 */
class ModManager(
    private val modsDirectory: Path,
    private val contextProvider: (ModInfo) -> ModContext,
) {
    private val loader = ModLoader(modsDirectory)
    private val containers = ConcurrentHashMap<String, ModContainer>()
    private val loadOrder = mutableListOf<ModContainer>()

    fun discover(): List<ModDescriptor> = loader.discover()

    fun loadAll(): List<ModContainer> {
        unloadAll()
        val descriptors = ModDependencyResolver.resolve(loader.discover())
        for (descriptor in descriptors) {
            val container = load(descriptor)
            loadOrder.add(container)
            containers[descriptor.info.id] = container
        }
        return loadOrder.toList()
    }

    fun reloadAll(): List<ModContainer> {
        unloadAll()
        val loaded = loadAll()
        enableAll()
        return loaded
    }

    fun reload(id: String) {
        if (!containers.containsKey(id)) {
            throw ModLoadException("Mod not loaded: $id")
        }
        reloadAll()
    }

    fun enableAll() {
        for (container in loadOrder) {
            if (container.state == ModState.ENABLED) {
                continue
            }
            container.instance.onEnable()
            container.state = ModState.ENABLED
        }
    }

    fun disableAll() {
        for (container in loadOrder.asReversed()) {
            if (container.state == ModState.DISABLED) {
                continue
            }
            container.instance.onDisable()
            container.state = ModState.DISABLED
        }
    }

    fun unloadAll() {
        disableAll()
        for (container in loadOrder.asReversed()) {
            container.classLoader.close()
        }
        loadOrder.clear()
        containers.clear()
    }

    fun all(): List<ModContainer> = loadOrder.toList()

    fun get(id: String): ModContainer? = containers[id]

    private fun load(descriptor: ModDescriptor): ModContainer {
        val url = descriptor.sourcePath.toUri().toURL()
        val classLoader = ModClassLoader(arrayOf(url), javaClass.classLoader)
        val instance = createInstance(descriptor, classLoader)
        val context = contextProvider(descriptor.info)
        instance.init(context)
        return ModContainer(
            info = descriptor.info,
            sourcePath = descriptor.sourcePath,
            instance = instance,
            classLoader = classLoader,
            state = ModState.LOADED,
        )
    }

    private fun createInstance(descriptor: ModDescriptor, classLoader: ModClassLoader): LineageMod {
        val clazz = Class.forName(descriptor.mainClassName, true, classLoader)
        if (!LineageMod::class.java.isAssignableFrom(clazz)) {
            throw ModLoadException(
                "Main class ${descriptor.mainClassName} does not extend LineageMod",
            )
        }
        val constructor = clazz.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance() as LineageMod
    }
}
