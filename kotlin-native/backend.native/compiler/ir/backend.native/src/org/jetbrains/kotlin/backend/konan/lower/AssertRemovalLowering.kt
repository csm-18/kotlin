/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.inlineFunction
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class AssertRemovalLowering(val context: Context) : BodyLoweringPass {
    private val asserts = context.ir.symbols.asserts.toSet()

    override fun lower(irBody: IrBody, container: IrDeclaration) {
        irBody.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitReturnableBlock(expression: IrReturnableBlock): IrExpression {
                val inlinedFunction = expression.inlineFunction ?: return super.visitReturnableBlock(expression)
                if (inlinedFunction.symbol in asserts) {
                    return IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type)
                }
                return super.visitReturnableBlock(expression)
            }
        })
    }
}

/**
 * Transforms `assert(someCondition()) {...}` call into
 *
 * ```
 * if (currentAssertionMode == AssertionMode.ENABLED || currentAssertionMode == AssertionMode.CONDITIONS_ONLY) {
 *     val tmp = someCondition()
 *     if (currentAssertionMode == AssertionMode.ENABLED) {
 *         assert(tmp) {...}
 *     }
 * }
 * ```
 */
internal class NativeAssertionWrapperLowering(val context: Context) : FileLoweringPass {
    private val asserts = context.ir.symbols.asserts.toSet()
    private val assertMode = context.ir.symbols.assertMode
    private val currentAssertionMode = context.ir.symbols.currentAssertMode.owner.getter!!
    private val conditionsOnlyMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "CONDITIONS_ONLY" } // TODO
    private val enabledMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "ENABLED" }
    private val disabledMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "DISABLED" }

    fun lower(function: IrFunction) {
        function.transformChildren(Transformer(), function.symbol)
    }

    override fun lower(irFile: IrFile) {
        irFile.transformChildren(Transformer(), irFile.symbol)
    }

    private inner class Transformer : IrElementTransformer<IrSymbol> {
        override fun visitElement(element: IrElement, data: IrSymbol): IrElement {
            return super.visitElement(element, if (element is IrSymbolOwner) element.symbol else data)
        }

        override fun visitCall(expression: IrCall, data: IrSymbol): IrElement {
            if (expression.symbol !in asserts) return super.visitCall(expression, data)

            val builder = context.createIrBuilder(data, expression.startOffset, expression.endOffset)
            return builder.irComposite(resultType = expression.type) {
                // currentAssertionMode == AssertionMode.ENABLED || currentAssertionMode == AssertionMode.CONDITIONS_ONLY
                val checkForConditions = irEquals(irCall(currentAssertionMode), irGetEnum(assertMode, conditionsOnlyMode.symbol))
                val checkForEnabled = irEquals(irCall(currentAssertionMode), irGetEnum(assertMode, enabledMode.symbol))
                val outerCheck = context.oror(checkForEnabled, checkForConditions)

                // currentAssertionMode == AssertionMode.ENABLED
                val innerCheck = irEquals(irCall(currentAssertionMode), irGetEnum(assertMode, enabledMode.symbol))

                // TODO can be rewritten as
                // outerCheck = currentAssertionMode != AssertionMode.DISABLED
                // innerCheck = currentAssertionMode == AssertionMode.ENABLED

                val condition = irTemporary(expression.getValueArgument(0))
                expression.putValueArgument(0, irGet(condition))
                val innerIf = irIfThen(innerCheck, expression)
                val outerIf = irIfThen(outerCheck, irBlock {
                    +condition
                    +innerIf
                })

                +outerIf
            }
        }
    }
}

/**
 * This lowering replaces `currentAssertionMode` intrinsic with actual value, depending on the assertion mode.
 */
internal class NativeAssertionRemoverLowering(val context: Context, val assertionsEnabled: Boolean) : FileLoweringPass {
    private val assertMode = context.ir.symbols.assertMode
    private val currentAssertionMode = context.ir.symbols.currentAssertMode.owner.getter!!
    private val conditionsOnlyMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "CONDITIONS_ONLY" } // TODO
    private val enabledMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "ENABLED" }
    private val disabledMode = assertMode.owner.declarations.filterIsInstance<IrEnumEntry>()
            .single { it.name.asString() == "DISABLED" }

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                if (expression.symbol != currentAssertionMode.symbol) return super.visitCall(expression)

                val builder = context.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset)
                return if (assertionsEnabled) {
                    builder.irGetEnum(assertMode, enabledMode.symbol)
                } else {
                    builder.irGetEnum(assertMode, disabledMode.symbol)
                }
            }
        })
    }
}
