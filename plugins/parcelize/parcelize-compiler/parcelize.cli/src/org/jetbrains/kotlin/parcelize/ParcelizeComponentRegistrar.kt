/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parcelize

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.parcelize.fir.FirParcelizeExtensionRegistrar
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

class ParcelizeComponentRegistrar : CompilerPluginRegistrar() {
    companion object {
        fun registerParcelizeComponents(
            extensionStorage: ExtensionStorage,
            additionalParcelizeAnnotation: List<String>,
            additionalIgnoredOnParcelAliases: List<String>,
            useFir: Boolean,
        ) = with(extensionStorage) {
            val parcelizeAnnotations = ParcelizeNames.PARCELIZE_CLASS_FQ_NAMES.toMutableList()
            val ignoredOnParcelAliases = ParcelizeNames.IGNORED_ON_PARCEL_FQ_NAMES.toMutableList()
            additionalParcelizeAnnotation.mapTo(parcelizeAnnotations) { FqName(it) }
            additionalIgnoredOnParcelAliases.mapTo(ignoredOnParcelAliases) { FqName(it) }
            val additionalAnnotations = AdditionalAnnotations(
                parcelize = parcelizeAnnotations,
                ignoredOnParcel = ignoredOnParcelAliases,
            )
            if (useFir) {
                IrGenerationExtension.registerExtension(ParcelizeFirIrGeneratorExtension(additionalAnnotations))
            } else {
                IrGenerationExtension.registerExtension(ParcelizeIrGeneratorExtension(additionalAnnotations))
            }
            SyntheticResolveExtension.registerExtension(ParcelizeResolveExtension(additionalAnnotations.parcelize))
            StorageComponentContainerContributor.registerExtension(
                ParcelizeDeclarationCheckerComponentContainerContributor(
                    additionalAnnotations
                )
            )
            FirExtensionRegistrarAdapter.registerExtension(FirParcelizeExtensionRegistrar(additionalAnnotations))
        }
    }

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val additionalAnnotation = configuration.get(ParcelizeConfigurationKeys.ADDITIONAL_ANNOTATION) ?: emptyList()
        val additionalIgnoredOnParcelAliases = configuration.get(ParcelizeConfigurationKeys.IGNORED_ON_PARCEL_ALIAS) ?: emptyList()
        registerParcelizeComponents(
            this,
            additionalAnnotation,
            additionalIgnoredOnParcelAliases,
            configuration.getBoolean(CommonConfigurationKeys.USE_FIR)
        )
    }

    override val supportsK2: Boolean
        get() = true
}

class ParcelizeDeclarationCheckerComponentContainerContributor(
    private val additionalAnnotations: AdditionalAnnotations<FqName>,
) : StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor,
    ) {
        if (platform.isJvm()) {
            container.useInstance(ParcelizeDeclarationChecker(additionalAnnotations.parcelize))
            container.useInstance(ParcelizeAnnotationChecker(additionalAnnotations))
        }
    }
}
