/*
 * Lineage Proxy
 * Copyright (c) 2026 Hytale Modding Russia
 *
 * Licensed under the GNU Affero General Public License v3.0
 * https://www.gnu.org/licenses/agpl-3.0.html
 */
package ru.hytalemodding.lineage.proxy.mod

/**
 * Resolves mod load order based on dependencies.
 */
object ModDependencyResolver {
    fun resolve(descriptors: List<ModDescriptor>): List<ModDescriptor> {
        if (descriptors.isEmpty()) {
            return emptyList()
        }
        val byId = descriptors.associateBy { it.info.id }
        val versions = descriptors.associate { descriptor ->
            val parsed = SemVer.parse(descriptor.info.version)
                ?: throw ModLoadException("Invalid version for ${descriptor.info.id}: ${descriptor.info.version}")
            descriptor.info.id to parsed
        }

        val dependencies = descriptors.associate { descriptor ->
            val required = descriptor.info.dependencies.map { DependencySpec.parse(it) }
            val optional = descriptor.info.softDependencies.map { DependencySpec.parse(it) }
            descriptor.info.id to DependencySet(required, optional)
        }

        validateDependencies(byId, versions, dependencies)
        return topologicalSort(descriptors, dependencies, byId)
    }

    private fun validateDependencies(
        byId: Map<String, ModDescriptor>,
        versions: Map<String, SemVer>,
        dependencies: Map<String, DependencySet>,
    ) {
        for ((modId, set) in dependencies) {
            for (required in set.required) {
                val target = byId[required.id]
                    ?: throw ModLoadException("Missing dependency ${required.id} required by $modId")
                val version = versions[target.info.id]
                    ?: throw ModLoadException("Missing version for dependency ${required.id}")
                if (!required.constraint.matches(version)) {
                    throw ModLoadException(
                        "Dependency ${required.id} version ${version} does not satisfy ${required.constraint} for $modId",
                    )
                }
            }
            for (optional in set.optional) {
                val target = byId[optional.id] ?: continue
                val version = versions[target.info.id]
                    ?: throw ModLoadException("Missing version for dependency ${optional.id}")
                if (!optional.constraint.matches(version)) {
                    throw ModLoadException(
                        "Soft dependency ${optional.id} version ${version} does not satisfy ${optional.constraint} for $modId",
                    )
                }
            }
        }
    }

    private fun topologicalSort(
        descriptors: List<ModDescriptor>,
        dependencies: Map<String, DependencySet>,
        byId: Map<String, ModDescriptor>,
    ): List<ModDescriptor> {
        val indegree = mutableMapOf<String, Int>()
        val edges = mutableMapOf<String, MutableSet<String>>()

        for (descriptor in descriptors) {
            indegree[descriptor.info.id] = 0
            edges[descriptor.info.id] = mutableSetOf()
        }

        for ((modId, set) in dependencies) {
            val required = set.required
                .map { it.id }
            val optional = set.optional
                .mapNotNull { spec -> if (byId.containsKey(spec.id)) spec.id else null }
            val allDeps = (required + optional).distinct()
            for (dependencyId in allDeps) {
                edges[dependencyId]?.add(modId)
                indegree[modId] = (indegree[modId] ?: 0) + 1
            }
        }

        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys.sorted())
        val ordered = mutableListOf<ModDescriptor>()

        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            ordered.add(byId.getValue(id))
            val dependents = edges[id].orEmpty().sorted()
            for (dependent in dependents) {
                val next = (indegree[dependent] ?: 0) - 1
                indegree[dependent] = next
                if (next == 0) {
                    queue.addLast(dependent)
                }
            }
        }

        if (ordered.size != descriptors.size) {
            val remaining = indegree.filterValues { it > 0 }.keys.sorted()
            throw ModLoadException("Dependency cycle detected among: ${remaining.joinToString(", ")}")
        }

        return ordered
    }

    private data class DependencySet(
        val required: List<DependencySpec>,
        val optional: List<DependencySpec>,
    )
}
