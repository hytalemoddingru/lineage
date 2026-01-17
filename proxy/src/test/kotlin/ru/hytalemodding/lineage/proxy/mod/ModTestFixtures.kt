/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import ru.hytalemodding.lineage.api.backend.BackendInfo
import ru.hytalemodding.lineage.api.backend.BackendRegistry
import ru.hytalemodding.lineage.api.command.CommandRegistry
import ru.hytalemodding.lineage.api.config.ConfigManager
import ru.hytalemodding.lineage.api.event.EventBus
import ru.hytalemodding.lineage.api.messaging.Messaging
import ru.hytalemodding.lineage.api.mod.ModContext
import ru.hytalemodding.lineage.api.mod.ModInfo
import ru.hytalemodding.lineage.api.permission.PermissionChecker
import ru.hytalemodding.lineage.api.player.PlayerManager
import ru.hytalemodding.lineage.api.schedule.Scheduler
import ru.hytalemodding.lineage.api.schedule.TaskHandle
import ru.hytalemodding.lineage.api.service.ServiceRegistry
import ru.hytalemodding.lineage.proxy.command.CommandRegistryImpl
import ru.hytalemodding.lineage.proxy.config.ConfigManagerImpl
import ru.hytalemodding.lineage.proxy.event.EventBusImpl
import ru.hytalemodding.lineage.proxy.messaging.NoopMessaging
import ru.hytalemodding.lineage.proxy.permission.PermissionCheckerImpl
import ru.hytalemodding.lineage.proxy.player.PlayerManagerImpl
import ru.hytalemodding.lineage.proxy.service.ServiceRegistryImpl
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

object TestHooks {
    private val events = CopyOnWriteArrayList<String>()

    @JvmStatic
    fun record(value: String) {
        events.add(value)
    }

    fun reset() {
        events.clear()
    }

    fun snapshot(): List<String> = events.toList()
}

fun createModJar(dir: Path, className: String, info: ModInfo): Path {
    val bytes = buildModClass(className, info)
    val jarPath = dir.resolve("${info.id}.jar")
    Files.createDirectories(dir)
    JarOutputStream(Files.newOutputStream(jarPath)).use { jar ->
        val entry = JarEntry(className.replace('.', '/') + ".class")
        jar.putNextEntry(entry)
        jar.write(bytes)
        jar.closeEntry()
    }
    return jarPath
}

fun createModContext(modInfo: ModInfo, baseDir: Path): ModContext {
    val dataDir = baseDir.resolve(modInfo.id)
    Files.createDirectories(dataDir)
    return TestModContext(modInfo, dataDir)
}

private fun buildModClass(className: String, info: ModInfo): ByteArray {
    val internalName = className.replace('.', '/')
    val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
    writer.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        internalName,
        null,
        "ru/hytalemodding/lineage/api/mod/LineageMod",
        null,
    )
    val annotation = writer.visitAnnotation("Lru/hytalemodding/lineage/api/mod/LineageModInfo;", true)
    annotation.visit("id", info.id)
    annotation.visit("name", info.name)
    annotation.visit("version", info.version)
    annotation.visit("apiVersion", info.apiVersion)
    if (info.description.isNotBlank()) {
        annotation.visit("description", info.description)
    }
    info.website?.let { annotation.visit("website", it) }
    info.license?.let { annotation.visit("license", it) }
    visitArray(annotation, "authors", info.authors)
    visitArray(annotation, "dependencies", info.dependencies)
    visitArray(annotation, "softDependencies", info.softDependencies)
    annotation.visitEnd()

    val ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    ctor.visitCode()
    ctor.visitVarInsn(Opcodes.ALOAD, 0)
    ctor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "ru/hytalemodding/lineage/api/mod/LineageMod",
        "<init>",
        "()V",
        false,
    )
    ctor.visitInsn(Opcodes.RETURN)
    ctor.visitMaxs(1, 1)
    ctor.visitEnd()

    addHookMethod(writer, "onEnable", "enable:${info.id}")
    addHookMethod(writer, "onDisable", "disable:${info.id}")

    writer.visitEnd()
    return writer.toByteArray()
}

private fun visitArray(
    annotation: org.objectweb.asm.AnnotationVisitor,
    name: String,
    values: List<String>,
) {
    if (values.isEmpty()) {
        return
    }
    val array = annotation.visitArray(name)
    for (value in values) {
        array.visit(null, value)
    }
    array.visitEnd()
}

private fun addHookMethod(writer: ClassWriter, name: String, marker: String) {
    val method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()V", null, null)
    method.visitCode()
    method.visitLdcInsn(marker)
    method.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "ru/hytalemodding/lineage/proxy/mod/TestHooks",
        "record",
        "(Ljava/lang/String;)V",
        false,
    )
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(1, 1)
    method.visitEnd()
}

private class TestModContext(
    override val modInfo: ModInfo,
    override val dataDirectory: Path,
) : ModContext {
    override val logger = LoggerFactory.getLogger("test")
    override val configManager: ConfigManager = ConfigManagerImpl(dataDirectory)
    override val eventBus: EventBus = EventBusImpl()
    override val commandRegistry: CommandRegistry = CommandRegistryImpl()
    override val scheduler: Scheduler = NoopScheduler
    override val messaging: Messaging = NoopMessaging()
    override val players: PlayerManager = PlayerManagerImpl()
    override val backends: BackendRegistry = EmptyBackendRegistry
    override val permissionChecker: PermissionChecker = PermissionCheckerImpl()
    override val serviceRegistry: ServiceRegistry = ServiceRegistryImpl()
}

private object EmptyBackendRegistry : BackendRegistry {
    override fun get(id: String): BackendInfo? = null
    override fun all(): Collection<BackendInfo> = emptyList()
}

private object NoopScheduler : Scheduler {
    private val handle = NoopTaskHandle

    override fun runSync(task: Runnable): TaskHandle {
        task.run()
        return handle
    }

    override fun runAsync(task: Runnable): TaskHandle {
        task.run()
        return handle
    }

    override fun runLater(delay: java.time.Duration, task: Runnable): TaskHandle {
        task.run()
        return handle
    }

    override fun runRepeating(interval: java.time.Duration, task: Runnable): TaskHandle {
        return handle
    }

    override fun runRepeating(delay: java.time.Duration, interval: java.time.Duration, task: Runnable): TaskHandle {
        return handle
    }
}

private object NoopTaskHandle : TaskHandle {
    override val isCancelled: Boolean = false
    override fun cancel() = Unit
}
