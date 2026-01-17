/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import ru.hytalemodding.lineage.api.mod.ModInfo
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Reads mod metadata from a jar by scanning for @LineageModInfo.
 */
object ModMetadataReader {
    private const val MOD_INFO_DESC = "Lru/hytalemodding/lineage/api/mod/LineageModInfo;"

    fun read(path: Path): ModDescriptor {
        if (!Files.isRegularFile(path)) {
            throw ModLoadException("Mod path is not a file: $path")
        }
        JarFile(path.toFile()).use { jar ->
            var descriptor: ModDescriptor? = null
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.endsWith(".class")) {
                    continue
                }
                jar.getInputStream(entry).use { stream ->
                    val candidate = readFromStream(path, stream)
                    if (candidate != null) {
                        if (descriptor != null) {
                            throw ModLoadException("Multiple @LineageModInfo annotations found in $path")
                        }
                        descriptor = candidate
                    }
                }
            }
            return descriptor ?: throw ModLoadException("No @LineageModInfo found in $path")
        }
    }

    private fun readFromStream(path: Path, stream: InputStream): ModDescriptor? {
        val reader = ClassReader(stream)
        val collector = ModInfoCollector()
        reader.accept(collector, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        val data = collector.data ?: return null
        val className = collector.className
            ?: throw ModLoadException("Failed to read class name for $path")

        val info = ModInfo(
            id = data.id ?: throw ModLoadException("Missing id in @LineageModInfo for $className"),
            name = data.name ?: throw ModLoadException("Missing name in @LineageModInfo for $className"),
            version = data.version ?: throw ModLoadException("Missing version in @LineageModInfo for $className"),
            apiVersion = data.apiVersion ?: throw ModLoadException("Missing apiVersion in @LineageModInfo for $className"),
            authors = data.authors,
            description = data.description ?: "",
            dependencies = data.dependencies,
            softDependencies = data.softDependencies,
            website = data.website?.ifBlank { null },
            license = data.license?.ifBlank { null },
        )
        return ModDescriptor(info, className, path)
    }

    private class ModInfoCollector : ClassVisitor(Opcodes.ASM9) {
        var className: String? = null
        var data: AnnotationData? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            className = name.replace('/', '.')
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            if (descriptor != MOD_INFO_DESC) {
                return null
            }
            if (data != null) {
                throw ModLoadException("Multiple @LineageModInfo annotations found in class $className")
            }
            val data = AnnotationData()
            this.data = data
            return ModInfoAnnotationVisitor(data)
        }
    }

    private class AnnotationData {
        var id: String? = null
        var name: String? = null
        var version: String? = null
        var apiVersion: String? = null
        var description: String? = null
        val authors: MutableList<String> = mutableListOf()
        val dependencies: MutableList<String> = mutableListOf()
        val softDependencies: MutableList<String> = mutableListOf()
        var website: String? = null
        var license: String? = null
    }

    private class ModInfoAnnotationVisitor(
        private val data: AnnotationData,
    ) : AnnotationVisitor(Opcodes.ASM9) {
        override fun visit(name: String, value: Any) {
            when (name) {
                "id" -> data.id = value as String
                "name" -> data.name = value as String
                "version" -> data.version = value as String
                "apiVersion" -> data.apiVersion = value as String
                "description" -> data.description = value as String
                "website" -> data.website = value as String
                "license" -> data.license = value as String
            }
        }

        override fun visitArray(name: String): AnnotationVisitor {
            val target = when (name) {
                "authors" -> data.authors
                "dependencies" -> data.dependencies
                "softDependencies" -> data.softDependencies
                else -> mutableListOf()
            }
            return object : AnnotationVisitor(Opcodes.ASM9) {
                override fun visit(name: String?, value: Any) {
                    target.add(value as String)
                }
            }
        }
    }
}
