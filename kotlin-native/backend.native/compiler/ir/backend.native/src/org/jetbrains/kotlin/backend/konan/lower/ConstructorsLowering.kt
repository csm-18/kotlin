/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.addDispatchReceiver
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.konan.ir.isAny
import org.jetbrains.kotlin.backend.konan.ir.isArray
import org.jetbrains.kotlin.backend.konan.isInlined
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.objcinterop.isExternalObjCClass
import org.jetbrains.kotlin.ir.objcinterop.isObjCConstructor
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.getOrSetIfNull

internal var IrConstructor.constructorImplFunction: IrSimpleFunction? by irAttribute(followAttributeOwner = false)
internal var IrSimpleFunction.constructor: IrConstructor? by irAttribute(followAttributeOwner = false)

internal val LOWERED_DELEGATING_CONSTRUCTOR_CALL by IrStatementOriginImpl

internal class ConstructorsLowering(private val context: Context) : FileLoweringPass, IrElementTransformer<IrDeclaration?> {
    private val irFactory = context.irFactory
    private val irBuiltIns = context.irBuiltIns
    private val createUninitializedInstance = context.ir.symbols.createUninitializedInstance
    private val createUninitializedArray = context.ir.symbols.createUninitializedArray
    private val initInstance = context.ir.symbols.initInstance

    override fun lower(irFile: IrFile) {
        irFile.transform(this, data = null)
    }

    override fun visitDeclaration(declaration: IrDeclarationBase, data: IrDeclaration?): IrStatement {
        return super.visitDeclaration(declaration, declaration)
    }

    override fun visitConstructor(declaration: IrConstructor, data: IrDeclaration?): IrStatement {
        declaration.transformChildren(this, declaration)

        val constructedClass = declaration.constructedClass
        if (declaration.isObjCConstructor || constructedClass.isInlined()) return declaration

        val implFunction = getConstructorImplFunction(declaration)
        val body = declaration.body
        if (body != null) {
            // TODO: Should we set the constructor's body to null?
            implFunction.body = body as IrBlockBody
            body.setDeclarationsParent(implFunction)
            val irBuilder = context.createIrBuilder(implFunction.symbol)
            body.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitReturn(expression: IrReturn): IrExpression {
                    expression.transformChildrenVoid()

                    return if (expression.returnTargetSymbol == declaration.symbol)
                        irBuilder.at(expression).irReturn(expression.value)
                    else expression
                }

                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    expression.transformChildrenVoid()

                    return when (val value = expression.symbol.owner) {
                        constructedClass.thisReceiver ->
                            irBuilder.at(expression).irGet(implFunction.dispatchReceiverParameter!!)
                        declaration.dispatchReceiverParameter -> {
                            require(constructedClass.isInner) { "Expected an inner class: ${constructedClass.render()}" }
                            irBuilder.at(expression).irGet(implFunction.valueParameters[0])
                        }
                        is IrValueParameter -> {
                            val shift = if (constructedClass.isInner) 1 else 0
                            irBuilder.at(expression).irGet(implFunction.valueParameters[value.index + shift])
                        }
                        else -> expression
                    }
                }

                override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                    expression.transformChildrenVoid()

                    return irBuilder.at(expression).run {
                        val callee = expression.symbol.owner
                        if (callee.constructedClass.isAny() || callee.constructedClass.isExternalObjCClass())
                            irComposite { }
                        else irCall(getConstructorImplFunction(callee), origin = LOWERED_DELEGATING_CONSTRUCTOR_CALL).apply {
                            dispatchReceiver = irGet(implFunction.dispatchReceiverParameter!!)
                            fillArgumentsFrom(expression)
                        }
                    }
                }
            })
        }
        return implFunction
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: IrDeclaration?): IrExpression {
        expression.transformChildren(this, data)

        // TODO: If wrapped in IMPLICIT_COERCION_TO_UNIT, no temp is needed.
        val constructor = expression.symbol.owner
        val constructedType = constructor.constructedClassType
        val implFunction = getConstructorImplFunction(constructor)
        val irBuilder = context.createIrBuilder(data!!.symbol, expression.startOffset, expression.endOffset)
        return when {
            constructor.constructedClass.isArray -> {
                irBuilder.irCall(createUninitializedArray, constructedType, listOf(constructedType)).apply {
                    putValueArgument(0, expression.getValueArgument(0)!!)
                }
            }
            constructedType.isString() -> irBuilder.run {
                irCall(createUninitializedArray, constructedType, listOf(constructedType)).apply {
                    putValueArgument(0, irInt(0))
                }
            }
            constructedType.isAny() -> {
                irBuilder.irCall(createUninitializedInstance, constructedType, listOf(constructedType))
            }
            else -> irBuilder.irBlock {
                val instance = irTemporary(irCall(createUninitializedInstance, constructedType, listOf(constructedType)), "inst")
                +irCall(implFunction).apply {
                    dispatchReceiver = irGet(instance)
                    fillArgumentsFrom(expression)
                }
                +irGet(instance)
            }
        }
    }

    override fun visitCall(expression: IrCall, data: IrDeclaration?): IrElement {
        val callee = expression.symbol.owner
        if (callee.symbol != initInstance)
            return super.visitCall(expression, data)

        val instance = expression.getValueArgument(0)
        val constructorCall = expression.getValueArgument(1) as IrConstructorCall
        val implFunction = getConstructorImplFunction(constructorCall.symbol.owner)
        val irBuilder = context.createIrBuilder(data!!.symbol, expression.startOffset, expression.endOffset)
        return irBuilder.irCall(implFunction).apply {
            dispatchReceiver = instance
            fillArgumentsFrom(constructorCall)

            transformChildren(this@ConstructorsLowering, data)
        }
    }

    private fun IrFunctionAccessExpression.fillArgumentsFrom(callSite: IrFunctionAccessExpression) {
        val shift: Int
        val dispatchReceiver = callSite.dispatchReceiver
        if (dispatchReceiver == null)
            shift = 0
        else {
            shift = 1
            putValueArgument(0, dispatchReceiver)
        }
        (0..<callSite.valueArgumentsCount).forEach {
            putValueArgument(it + shift, callSite.getValueArgument(it))
        }
//        (0..<callSite.typeArgumentsCount).forEach {
//            putTypeArgument(it, callSite.getTypeArgument(it))
//        }
    }

    /*
     * class A<T> constructor(x: T) {
     *     val x: T = x
     * }
     *
     * transforms to
     *
     * class A<T> {
     *     val x: T
     *
     *     fun <T> <init>(inst: A<T>, x: T) {
     *         inst.x = x
     *     }
     * }
     */
    private fun getConstructorImplFunction(irConstructor: IrConstructor): IrSimpleFunction =
            irConstructor::constructorImplFunction.getOrSetIfNull {
                irFactory.buildFun {
                    name = irConstructor.name
                    startOffset = irConstructor.startOffset
                    endOffset = irConstructor.endOffset
                    visibility = irConstructor.visibility
                    isExternal = irConstructor.isExternal
                    returnType = irBuiltIns.unitType
                }.apply {
                    val function = this
                    val parentClass = irConstructor.parentAsClass
                    parent = parentClass
                    constructor = irConstructor

                    // TODO: Do we actually need to copy type parameters from the parent class?
//                    typeParameters = copyTypeParametersFrom(parentClass)
//                    val map = makeTypeParameterSubstitutionMap(parentClass, function)
//
//                    addDispatchReceiver {
//                        startOffset = parentClass.startOffset
//                        endOffset = parentClass.startOffset
//                        type = parentClass.defaultType.substitute(map)
//                    }
//
//                    valueParameters = irConstructor.valueParameters.map { it.copyTo(function, type = it.type.substitute(map)) }

//                    typeParameters = parentClass.typeParameters

                    addDispatchReceiver {
                        startOffset = parentClass.startOffset
                        endOffset = parentClass.startOffset
                        type = parentClass.defaultType
                    }

                    val outerReceiverParameter = irConstructor.dispatchReceiverParameter
                    valueParameters = if (outerReceiverParameter == null)
                        irConstructor.valueParameters.map { it.copyTo(function, type = it.type) }
                    else {
                        require(parentClass.isInner) { "Expected an inner class: ${parentClass.render()}" }
                        listOf(outerReceiverParameter.copyTo(
                            function, index = 0, name = Name.identifier("\$outer"), type = outerReceiverParameter.type)
                        ) + irConstructor.valueParameters.map { it.copyTo(function, index = it.index + 1, type = it.type) }
                    }
                }
            }
}