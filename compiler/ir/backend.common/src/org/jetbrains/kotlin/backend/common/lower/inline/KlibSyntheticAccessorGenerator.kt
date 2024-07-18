/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower.inline

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.ScopeWithIr
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.Name

class KlibSyntheticAccessorGenerator(
    context: CommonBackendContext,
) : SyntheticAccessorGenerator<CommonBackendContext>(context, addAccessorToParent = true) {

    private data class OuterThisAccessorKey(val innerClass: IrClass)

    companion object {
        const val TOP_LEVEL_FUNCTION_SUFFIX_MARKER = "t"

        private var IrValueParameter.outerThisSyntheticAccessors: MutableMap<OuterThisAccessorKey, IrSimpleFunction>? by irAttribute(
            followAttributeOwner = false
        )
    }

    override fun accessorModality(parent: IrDeclarationParent) = Modality.FINAL
    override fun IrDeclarationWithVisibility.accessorParent(parent: IrDeclarationParent, scopes: List<ScopeWithIr>) = parent

    override fun AccessorNameBuilder.buildFunctionName(
        function: IrSimpleFunction,
        superQualifier: IrClassSymbol?,
        scopes: List<ScopeWithIr>,
    ) {
        contribute(function.name.asString())

        val parent = function.parent
        if (parent is IrPackageFragment) {
            // This is a top-level function. Include the sanitized .kt file name to avoid potential clashes.
            check(parent is IrFile) {
                "Unexpected type of package fragment for top-level function ${function.render()}: ${parent::class.java}, ${parent.render()}"
            }

            contribute(TOP_LEVEL_FUNCTION_SUFFIX_MARKER + parent.packagePartClassName)
        }
    }

    override fun AccessorNameBuilder.buildFieldGetterName(field: IrField, superQualifierSymbol: IrClassSymbol?) {
        contribute("<get-${field.name}>")
        contribute(PROPERTY_MARKER)
    }

    override fun AccessorNameBuilder.buildFieldSetterName(field: IrField, superQualifierSymbol: IrClassSymbol?) {
        contribute("<set-${field.name}>")
        contribute(PROPERTY_MARKER)
    }

    /**
     * Note: This kind of accessor is never added to the parent declaration even [addAccessorToParent] is `true`.
     * This is done intentionally. Adding to parent is handled in [OuterThisInInlineFunctionsSpecialAccessorLowering].
     */
    fun getSyntheticOuterThisParameterAccessor(
        expression: IrGetValue,
        valueParameter: IrValueParameter,
        levelDifference: Int,
        innerClass: IrClass
    ): IrSimpleFunction {
        val functionMap = valueParameter.outerThisSyntheticAccessors
            ?: hashMapOf<OuterThisAccessorKey, IrSimpleFunction>().also { valueParameter.outerThisSyntheticAccessors = it }

        return functionMap.getOrPut(OuterThisAccessorKey(innerClass)) {
            makeSyntheticThisParameterAccessor(expression, levelDifference, innerClass)
        }
    }

    private fun makeSyntheticThisParameterAccessor(
        expression: IrGetValue,
        levelDifference: Int,
        innerClass: IrClass
    ): IrSimpleFunction {
        // "<outer-this-0>" for the closest outer class, "<outer-this-1>" for the next one, and so on.
        // Note: The static public accessor for call sites of this accessor in non-private inline functions would
        // get a derived name with the "access" prefix. Example: "access$<outer-this-1>".
        val accessorName = Name.identifier("<outer-this-${levelDifference - 1}>")

        return innerClass.factory.buildFun {
            startOffset = innerClass.startOffset
            endOffset = innerClass.startOffset
            origin = IrDeclarationOrigin.SYNTHETIC_ACCESSOR
            name = accessorName
            visibility = DescriptorVisibilities.PRIVATE
        }.apply {
            parent = innerClass
            dispatchReceiverParameter = innerClass.thisReceiver!!.copyTo(this, origin = origin)
            returnType = expression.type
            body = context.irFactory.createExpressionBody(startOffset, startOffset, expression)
        }
    }
}
