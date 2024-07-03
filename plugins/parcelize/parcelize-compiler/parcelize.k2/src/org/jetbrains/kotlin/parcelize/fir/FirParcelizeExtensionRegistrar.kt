/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parcelize.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.parcelize.AdditionalAnnotations

class FirParcelizeExtensionRegistrar(
    private val additionalAnnotations: AdditionalAnnotations<FqName>,
) :
    FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirParcelizeDeclarationGenerator.bind(additionalAnnotations.parcelize)
        +::firParcelizeCheckerExtension
    }

    private fun firParcelizeCheckerExtension(session: FirSession) =
        FirParcelizeCheckersExtension(
            session,
            additionalAnnotations.map(ClassId::topLevel)
        )

}
