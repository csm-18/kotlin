/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// This file was generated automatically. See compiler/fir/tree/tree-generator/Readme.md.
// DO NOT MODIFY IT MANUALLY.

@file:Suppress("DuplicatedCode", "unused")

package org.jetbrains.kotlin.fir.expressions.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.builder.FirAnnotationContainerBuilder
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.builder.toMutableOrEmpty
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirDestructuringAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirDestructuringAccessExpressionImpl
import org.jetbrains.kotlin.fir.references.FirReference
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.name.Name

@FirBuilderDsl
class FirDestructuringAccessExpressionBuilder : FirAnnotationContainerBuilder, FirExpressionBuilder {
    override var coneTypeOrNull: ConeKotlinType? = null
    override val annotations: MutableList<FirAnnotation> = mutableListOf()
    lateinit var calleeReference: FirReference
    val contextReceiverArguments: MutableList<FirExpression> = mutableListOf()
    val typeArguments: MutableList<FirTypeProjection> = mutableListOf()
    var explicitReceiver: FirExpression? = null
    var dispatchReceiver: FirExpression? = null
    var extensionReceiver: FirExpression? = null
    override var source: KtSourceElement? = null
    val nonFatalDiagnostics: MutableList<ConeDiagnostic> = mutableListOf()
    var position: Int by kotlin.properties.Delegates.notNull<Int>()
    lateinit var destructuredPropertyName: Name
    var entrySource: KtSourceElement? = null

    override fun build(): FirDestructuringAccessExpression {
        return FirDestructuringAccessExpressionImpl(
            coneTypeOrNull,
            annotations.toMutableOrEmpty(),
            calleeReference,
            contextReceiverArguments.toMutableOrEmpty(),
            typeArguments.toMutableOrEmpty(),
            explicitReceiver,
            dispatchReceiver,
            extensionReceiver,
            source,
            nonFatalDiagnostics.toMutableOrEmpty(),
            position,
            destructuredPropertyName,
            entrySource,
        )
    }

}

@OptIn(ExperimentalContracts::class)
inline fun buildDestructuringAccessExpression(init: FirDestructuringAccessExpressionBuilder.() -> Unit): FirDestructuringAccessExpression {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return FirDestructuringAccessExpressionBuilder().apply(init).build()
}
