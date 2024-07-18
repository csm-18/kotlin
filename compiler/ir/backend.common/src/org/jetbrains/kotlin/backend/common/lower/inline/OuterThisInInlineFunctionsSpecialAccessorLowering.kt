/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower.inline

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.Companion.INSTANCE_RECEIVER
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.visitors.*

class OuterThisInInlineFunctionsSpecialAccessorLowering(context: CommonBackendContext) : FileLoweringPass {
    private val accessorGenerator = KlibSyntheticAccessorGenerator(context)

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(OuterThisInInlineFunctionsSpecialAccessorTransformer(accessorGenerator))
    }
}

private class OuterThisInInlineFunctionsSpecialAccessorTransformer(
    private val accessorGenerator: KlibSyntheticAccessorGenerator
) : IrElementTransformerVoid() {

    private class ClassData(val level: Int) {
        val accessors: MutableSet<IrSimpleFunction> = hashSetOf()
    }

    private class InlineFunctionData(val innerClass: IrClass, val inlineFunction: IrFunction)

    private val classes: MutableMap<IrClass, ClassData> = hashMapOf()
    private var inlineFunctionData: InlineFunctionData? = null

    override fun visitClass(declaration: IrClass): IrStatement {
        val classData = ClassData(level = classes.size)
        if (classes.put(declaration, classData) != null)
            error("Already visited class: $declaration")

        super.visitClass(declaration)

        val pendingAccessors = classData.accessors
        when (pendingAccessors.size) {
            0 -> Unit
            1 -> declaration.declarations += pendingAccessors.first()
            else -> {
                // Sort accessors to always have a stable order.
                declaration.declarations += pendingAccessors.sortedBy { it.name }
            }
        }

        classes.remove(declaration)

        return declaration
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        val previousInlineFunctionData = inlineFunctionData
        return try {
            inlineFunctionData = (declaration.parent as? IrClass)?.takeIf(IrClass::isInner)?.let { InlineFunctionData(it, declaration) }
            super.visitFunction(declaration)
        } finally {
            inlineFunctionData = previousInlineFunctionData
        }
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val inlineFunctionData = inlineFunctionData
            ?: // Don't inspect value parameters inside non-inline functions.
            return expression

        val valueParameter = expression.symbol.owner as? IrValueParameter
        if (valueParameter?.origin != INSTANCE_RECEIVER)
            return expression

        val outerClass = valueParameter.parent as? IrClass
            ?: return expression

        val innerClass = inlineFunctionData.innerClass
        if (outerClass == innerClass)
            return expression

        val innerClassData = classes.getValue(innerClass)

        val accessor = accessorGenerator.getSyntheticOuterThisParameterAccessor(
            expression,
            valueParameter,
            levelDifference = innerClassData.level - classes.getValue(outerClass).level,
            innerClass
        )

        innerClassData.accessors += accessor

        return IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, accessor.symbol).apply {
            dispatchReceiver = IrGetValueImpl(
                startOffset,
                endOffset,
                inlineFunctionData.inlineFunction.dispatchReceiverParameter!!.symbol,
                origin
            )
        }
    }
}
