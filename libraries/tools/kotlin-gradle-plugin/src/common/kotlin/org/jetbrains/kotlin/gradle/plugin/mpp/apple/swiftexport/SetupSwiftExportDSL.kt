/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.supportedAppleTargets
import org.jetbrains.kotlin.gradle.plugin.KotlinProjectSetupAction
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.addExtension
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.plugin.diagnostics.reportDiagnosticOncePerBuild
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XcodeEnvironment
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.registerEmbedSwiftExportTask
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl

internal object SwiftExportDSLConstants {
    const val SWIFT_EXPORT_LIBRARY_PREFIX = "swiftExport"
    const val SWIFT_EXPORT_EXTENSION_NAME = "swiftexport"
    const val TASK_GROUP = "SwiftExport"
}

@ExperimentalSwiftExportDsl
internal val SetUpSwiftExportAction = KotlinProjectSetupAction {
    if (!kotlinPropertiesProvider.swiftExportEnabled) return@KotlinProjectSetupAction
    warnAboutExperimentalSwiftExportFeature(project)
    val kotlinExtension = project.multiplatformExtension
    val swiftExportExtension = project.objects.newInstance(SwiftExportExtension::class.java, this)

    kotlinExtension.addExtension(SwiftExportDSLConstants.SWIFT_EXPORT_EXTENSION_NAME, swiftExportExtension)

    registerSwiftExportPipeline(project, swiftExportExtension)
}

internal fun warnAboutExperimentalSwiftExportFeature(project: Project) {
    project.reportDiagnosticOncePerBuild(
        KotlinToolingDiagnostics.ExperimentalFeatureWarning("Swift Export", "https://jb.gg/3s5ngl")
    )
}

@ExperimentalSwiftExportDsl
private fun registerSwiftExportPipeline(
    project: Project,
    swiftExportExtension: SwiftExportExtension,
) {
    val environment = XcodeEnvironment(project)

    project
        .multiplatformExtension
        .supportedAppleTargets()
        .all { target ->
            target.binaries.staticLib(SwiftExportDSLConstants.SWIFT_EXPORT_LIBRARY_PREFIX) {
                swiftExportExtension.addBinary(this)
                project.registerEmbedSwiftExportTask(this, environment, swiftExportExtension)
            }
        }
}