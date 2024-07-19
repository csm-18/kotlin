/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.utils.*
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl
import javax.inject.Inject

interface SwiftExportedModuleMetadata {
    /**
     * Configure name of the swift export module from this project.
     */
    @get:Input
    @get:Optional
    val moduleName: Property<String>

    /**
     * Configure package collapsing rule.
     */
    @get:Input
    @get:Optional
    val flattenPackage: Property<String>
}

interface SwiftExportedModuleVersionMetadata : SwiftExportedModuleMetadata {
    /**
     * Module version identifier
     */
    @get:Internal
    val moduleVersion: ModuleVersionIdentifier
}

/**
 * Represents an extension class for configuring Swift Export.
 *
 * @property project The Gradle project.
 * @property objects The Gradle object factory.
 */
@ExperimentalSwiftExportDsl
@Suppress("unused", "MemberVisibilityCanBePrivate") // Public API
abstract class SwiftExportExtension @Inject constructor(
    private val project: Project,
    private val objects: ObjectFactory,
) : SwiftExportedModuleMetadata {

    /**
     * Configure binaries of the Swift Export built from this project.
     */
    fun binaries(configure: AbstractNativeLibrary.() -> Unit) {
        forAllSwiftExportBinaries(configure)
    }

    /**
     * Configure binaries of the Swift Export built from this project.
     */
    fun binaries(configure: Action<AbstractNativeLibrary>) = binaries {
        configure.execute(this)
    }

    /**
     * Configure Swift Export modules export.
     */
    fun export(dependency: Any, configure: SwiftExportedModuleMetadata.() -> Unit = {}) {
        val dependencyProvider: Provider<Dependency> = when (dependency) {
            is Provider<*> -> dependency.map { dep ->
                when (dep) {
                    is Dependency -> dep
                    else -> project.dependencies.create(dep)
                }
            }
            else -> project.provider { project.dependencies.create(dependency) }
        }

        forAllSwiftExportBinaries { binary ->
            project.dependencies.addProvider(binary.exportConfigurationName, dependencyProvider)
            project.dependencies.addProvider(
                binary.compilation.internal.configurations.compileDependencyConfiguration.name,
                dependencyProvider
            )
        }

        val dependencyId = dependencyProvider.map { dep ->
            val moduleGroupProvider = project.provider { dep.group ?: "unspecified" }
            val moduleNameProvider = project.provider { dep.name }
            val moduleVersionProvider = project.provider { dep.version ?: "unspecified" }

            getCoordinatesFromProviders(moduleGroupProvider, moduleNameProvider, moduleVersionProvider)
        }

        project.objects.newInstance(
            ModuleExport::class.java,
            dependencyId
        ).apply {
            configure()
            addToExportedModules(this)
        }
    }

    /**
     * Configure Swift Export modules export.
     */
    fun export(dependency: Any, configure: Action<SwiftExportedModuleMetadata>) = export(dependency) {
        configure.execute(this)
    }

    /**
     * Returns a list of exported modules.
     */
    val exportedModules: Provider<Set<SwiftExportedModuleVersionMetadata>> = project.provider {
        _exportedModules
    }

    private val _swiftExportBinaries = objects.domainObjectSet(AbstractNativeLibrary::class.java)

    internal fun addBinary(binary: AbstractNativeLibrary) {
        _swiftExportBinaries.add(binary)
    }

    private val _exportedModules = project.container(ModuleExport::class.java)

    private fun addToExportedModules(module: ModuleExport) {
        check(_exportedModules.findByName(module.name) == null) {
            "Project already has Export module with name ${module.name}"
        }

        _exportedModules.add(module)
    }

    private fun forAllSwiftExportBinaries(configure: AbstractNativeLibrary.() -> Unit) {
        _swiftExportBinaries.all { it.configure() }
    }

    private fun forAllSwiftExportBinaries(action: Action<in AbstractNativeLibrary>) = forAllSwiftExportBinaries {
        action.execute(this)
    }

    internal abstract class ModuleExport @Inject constructor(
        moduleVersionProvider: Provider<ModuleVersionIdentifier>,
    ) : SwiftExportedModuleVersionMetadata, Named {
        @get:Internal
        override val moduleVersion: ModuleVersionIdentifier by moduleVersionProvider

        @Internal
        override fun getName(): String = moduleVersion.let {
            "${it.group}:${it.name}:${it.version}"
        }
    }
}