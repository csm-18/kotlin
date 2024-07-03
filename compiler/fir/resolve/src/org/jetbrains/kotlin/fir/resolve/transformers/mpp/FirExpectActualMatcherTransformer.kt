/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers.mpp

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isExpect
import org.jetbrains.kotlin.fir.declarations.utils.nameOrSpecialName
import org.jetbrains.kotlin.fir.expectActualMatchingContextFactory
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.dependenciesSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirAbstractTreeTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.FirTransformerBasedResolveProcessor
import org.jetbrains.kotlin.fir.visitors.FirTransformer

class FirExpectActualMatcherProcessor(
    session: FirSession,
    scopeSession: ScopeSession,
) : FirTransformerBasedResolveProcessor(session, scopeSession, FirResolvePhase.EXPECT_ACTUAL_MATCHING) {
    private val enabled = session.languageVersionSettings.supportsFeature(LanguageFeature.MultiPlatformProjects)

    override val transformer: FirTransformer<Nothing?> = FirExpectActualMatcherTransformer(session, scopeSession)

    override fun processFile(file: FirFile) {
        if (!enabled) return
        super.processFile(file)
    }
}

/**
 * This transformer populates [expectForActual] mapping for actual declarations.
 * Also, populates it [memberExpectForActual] mapping
 *
 * Should run before any kind of body resolution, since [expectForActual] is used there.
 *
 * See `/docs/fir/k2_kmp.md`
 */
open class FirExpectActualMatcherTransformer(
    final override val session: FirSession,
    private val actualScopeSession: ScopeSession,
) : FirAbstractTreeTransformer<Nothing?>(FirResolvePhase.EXPECT_ACTUAL_MATCHING) {

    private val expectActualMatchingContext = session.expectActualMatchingContextFactory.create(
        session, actualScopeSession,
        allowedWritingMemberExpectForActualMapping = true,
    )

    // --------------------------- classifiers ---------------------------
    override fun transformTypeAlias(typeAlias: FirTypeAlias, data: Nothing?): FirStatement {
        transformMemberDeclaration(typeAlias)
        return typeAlias
    }

    override fun transformRegularClass(regularClass: FirRegularClass, data: Nothing?): FirStatement {
        transformMemberDeclaration(regularClass)
        regularClass.transformChildren(this, data)
        return regularClass
    }

    // --------------------------- callable declaration ---------------------------
    override fun transformEnumEntry(enumEntry: FirEnumEntry, data: Nothing?): FirStatement {
        transformMemberDeclaration(enumEntry)
        return enumEntry
    }

    override fun transformProperty(property: FirProperty, data: Nothing?): FirStatement {
        transformMemberDeclaration(property)
        return property
    }

    override fun transformConstructor(constructor: FirConstructor, data: Nothing?): FirStatement {
        transformMemberDeclaration(constructor)
        return constructor
    }

    override fun transformErrorPrimaryConstructor(errorPrimaryConstructor: FirErrorPrimaryConstructor, data: Nothing?): FirStatement =
        transformConstructor(errorPrimaryConstructor, data)

    override fun transformSimpleFunction(simpleFunction: FirSimpleFunction, data: Nothing?): FirStatement {
        transformMemberDeclaration(simpleFunction)
        return simpleFunction
    }

    // --------------------------- other ---------------------------
    override fun transformAnonymousInitializer(anonymousInitializer: FirAnonymousInitializer, data: Nothing?): FirAnonymousInitializer {
        return anonymousInitializer
    }

    // ------------------------------------------------------

    fun transformMemberDeclaration(memberDeclaration: FirMemberDeclaration) {
        if (memberDeclaration.isExpect) return
        val actualSymbol = memberDeclaration.symbol

        val foo = session.moduleData.platform.joinToString()
        val bar = memberDeclaration.nameOrSpecialName
        val provider = session.symbolProvider
        val deps = session.dependenciesSymbolProvider
        println()

        // Regardless of whether any `expect` symbols are found for `memberDeclaration`, it must be assigned an `expectForActual` map.
        // Otherwise, `FirExpectActualDeclarationChecker` will assume that the symbol needs no checking and not report an
        // `EXPECT_WITHOUT_ACTUAL` error.
        val expectForActualData = FirExpectActualResolver.findExpectForActual(
            actualSymbol,
            session,
            expectActualMatchingContext,
        )
        memberDeclaration.expectForActual = expectForActualData
    }
}
