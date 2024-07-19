/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportedModuleVersionMetadata
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportTaskParameters
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportAction
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportedModule
import org.jetbrains.kotlin.gradle.utils.LazyResolvedConfiguration
import org.jetbrains.kotlin.gradle.utils.dashSeparatedToUpperCamelCase
import org.jetbrains.kotlin.gradle.utils.getFile
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Swift Export is experimental, so no caching for now")
internal abstract class SwiftExportTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    private val fileSystem: FileSystemOperations,
) : DefaultTask() {

    internal abstract class ModuleInput {
        @get:Input
        abstract val moduleName: Property<String>

        @get:Input
        @get:Optional
        abstract val flattenPackage: Property<String>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val artifact: RegularFileProperty
    }

    @get:Internal
    abstract val configuration: Property<LazyResolvedConfiguration>

    @get:Nested
    abstract val mainModuleInput: ModuleInput

    @get:Nested
    abstract val exportedModules: SetProperty<SwiftExportedModuleVersionMetadata>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val swiftExportClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val parameters: SwiftExportTaskParameters

    @TaskAction
    fun run() {
        cleanup()

        val swiftExportQueue = workerExecutor.classLoaderIsolation { workerSpec ->
            workerSpec.classpath.from(swiftExportClasspath)
        }

        swiftExportQueue.submit(SwiftExportAction::class.java) { workParameters ->
            workParameters.bridgeModuleName.set(parameters.bridgeModuleName)
            workParameters.konanDistribution.set(parameters.konanDistribution)
            workParameters.outputPath.set(parameters.outputPath)
            workParameters.stableDeclarationsOrder.set(parameters.stableDeclarationsOrder)
            workParameters.swiftModules.set(swiftExportedModules())
            workParameters.swiftModulesFile.set(parameters.swiftModulesFile)
        }
    }

    private fun cleanup() {
        try {
            fileSystem.delete {
                it.delete(parameters.outputPath)
            }
        } catch (e: Exception) {
            logger.warn("Can't delete Swift Export output folder", e)
        }
    }

    internal fun swiftExportedModules(): Provider<List<SwiftExportedModule>> {
        return configuration.map { configuration ->
            configuration.swiftExportedModules(exportedModules.get())
        }.map { modules ->
            modules.toMutableList().apply {
                add(
                    SwiftExportedModule(
                        mainModuleInput.moduleName.get(),
                        mainModuleInput.flattenPackage.orNull,
                        mainModuleInput.artifact.getFile()
                    )
                )
            }
        }
    }
}

private val File.isCinteropKlib get() = extension == "klib" && nameWithoutExtension.contains("cinterop-interop")

private fun Collection<File>.filterCinteropKlibs(): List<File> = filterNot(File::isCinteropKlib)

private fun LazyResolvedConfiguration.swiftExportedModules(exportedModules: Set<SwiftExportedModuleVersionMetadata>): List<SwiftExportedModule> {
    return allResolvedDependencies.filterNot { dependencyResult ->
        dependencyResult.resolvedVariant.owner.let { id -> id is ModuleComponentIdentifier && id.module == "kotlin-stdlib" }
    }.map { dependencyResult ->
        findAndCreateSwiftExportedModule(exportedModules, dependencyResult)
    }
}

private fun LazyResolvedConfiguration.findAndCreateSwiftExportedModule(
    exportedModules: Set<SwiftExportedModuleVersionMetadata>,
    resolvedDependency: ResolvedDependencyResult
): SwiftExportedModule {
    val resolvedModule = resolvedDependency.selected.moduleVersion
        ?: throw AssertionError("Missing module version for dependency: $resolvedDependency")

    val module = exportedModules.firstOrNull {
        resolvedModule.name == it.moduleVersion.name &&
                resolvedModule.group == it.moduleVersion.group &&
                resolvedModule.version == it.moduleVersion.version
    }

    return if (module != null) {
        val dependencyArtifacts = getArtifacts(resolvedDependency)
            .map { it.file }
            .filterCinteropKlibs()

        if (dependencyArtifacts.isEmpty() || dependencyArtifacts.size > 1) {
            throw AssertionError(
                "Dependency $resolvedDependency ${
                    if (dependencyArtifacts.isEmpty())
                        "doesn't have suitable artifacts"
                    else
                        "has too many artifacts: $dependencyArtifacts"
                }"
            )
        }

        SwiftExportedModule(
            module.moduleName.orElse(dashSeparatedToUpperCamelCase(module.moduleVersion.name)).get(),
            module.flattenPackage.orNull,
            dependencyArtifacts.single()
        )
    } else {
        throw AssertionError("Couldn't find matching dependency for: $resolvedModule")
    }
}
