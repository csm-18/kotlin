/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.inline

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.inline.KlibSyntheticAccessorGenerator
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

/**
 * Generates synthetic accessor functions for private declarations that are referenced from non-private inline functions,
 * so that after those functions are inlined, there'll be no visibility violations.
 *
 * There are a few important assumptions that this lowering relies on:
 * - It's executed in a KLIB-based backend. It's not designed to work with the JVM backend because the visibility rules on JVM are
 *   stricter.
 * - By the point it's executed, all _private_ inline functions have already been inlined.
 */
class SyntheticAccessorLowering(context: CommonBackendContext) : FileLoweringPass {
    private data class GeneratedAccessor(
        val accessor: IrFunction,
        val targetSymbol: IrSymbol,
    )

    private class GeneratedAccessors {
        private val accessors = HashSet<GeneratedAccessor>()
        private var frozen = false

        operator fun plusAssign(accessor: GeneratedAccessor) {
            check(!frozen) { "An attempt to generate an accessor after all accessors have been already added to their containers" }
            accessors += accessor
        }

        fun freezeAndGetAccessors(): Set<GeneratedAccessor> {
            check(!frozen) { "An attempt to add the generated accessors to their containers once again" }
            frozen = true
            return accessors
        }
    }

    private val accessorGenerator = KlibSyntheticAccessorGenerator(context)

    override fun lower(irFile: IrFile) {
        val transformer = Transformer(irFile)
        irFile.transformChildren(transformer, null)

        addAccessorsToParents(transformer.generatedAccessors)
    }

    /**
     * Lower a single inline [IrFunction] from an [IrFile] that is not being lowered right now.
     *
     * Note: This function is supposed to be used in [InlineFunctionResolver] implementations that allow
     * deserializing bodiless inline functions and lowering them on demand. Example: [NativeInlineFunctionResolver].
     *
     * All the generated accessors are cached. This helps to avoid accidentally generating their duplicates on the next invocation
     * of [lowerWithoutAddingAccessorsToParents] for another inline function that may also exist in the same [IrFile].
     *
     * Note that the generated accessors are not added to their [IrDeclarationContainer]s. This is only possible when lowering the
     * whole [IrFile] through `lower(IrFile)`.
     * - It helps to keep the invariant that only the file that is being lowered is modified.
     * - Only during file-wise lowering it's possible to compute the proper order in which accessors should be placed.
     */
    fun lowerWithoutAddingAccessorsToParents(irFunction: IrFunction) {
        irFunction.accept(Transformer(irFunction.file), null)
    }

    /**
     * Add accessors to their containers repeating the order of the corresponding target declarations.
     * Example:
     * ```kotlin
     * class MyClass {
     *     // declarations that are used inside inline function:
     *     private val foo
     *         private get()
     *         private set(value)
     *     public val bar
     *         public get()
     *         private set(value)
     *     public fun baz()
     *     private fun qux()
     *     // their accessors in the "proper" order:
     *     fun access$<get-foo>()
     *     fun access$<set-foo>()
     *     fun access$<set-bar>()
     *     fun access$qux()
     * }
     * ```
     */
    private fun addAccessorsToParents(generatedAccessors: GeneratedAccessors) {
        val accessors = generatedAccessors.freezeAndGetAccessors()
        if (accessors.isEmpty()) return

        for ((parent, accessorsInParent) in accessors.groupBy { it.accessor.parent }) {
            addAccessorsToParent(parent as IrDeclarationContainer, accessorsInParent)
        }
    }

    private fun addAccessorsToParent(parent: IrDeclarationContainer, accessors: List<GeneratedAccessor>) {
        if (accessors.size == 1) {
            parent.declarations += accessors[0].accessor
            return
        }

        val targetToAccessor: Map<IrDeclaration, IrFunction> = accessors.associate { accessor ->
            accessor.targetSymbol.owner as IrDeclaration to accessor.accessor
        }

        val remainingAccessors: MutableSet<IrFunction> = accessors.mapTo(HashSet(), GeneratedAccessor::accessor)

        fun addAccessorToParent(maybeTarget: IrDeclaration?) {
            if (maybeTarget == null) return
            val accessor = targetToAccessor[maybeTarget] ?: return
            parent.declarations += accessor
            remainingAccessors -= accessor
        }

        for (maybeTarget in parent.declarations.toList()) {
            addAccessorToParent(maybeTarget)
            if (maybeTarget is IrProperty) {
                addAccessorToParent(maybeTarget.getter)
                addAccessorToParent(maybeTarget.setter)
            }
        }

        parent.declarations += remainingAccessors // unexpected, but...
    }

    private class TransformerData(/* to be used later */ @Suppress("unused") val currentInlineFunction: IrFunction)

    private inner class Transformer(irFile: IrFile) : IrElementTransformer<TransformerData?> {
        val generatedAccessors = irFile.generatedAccessors ?: GeneratedAccessors().also { irFile.generatedAccessors = it }

        override fun visitFunction(declaration: IrFunction, data: TransformerData?): IrStatement {
            val newData = if (declaration.isInline) {
                if (!declaration.isConsideredAsPrivateForInlining()) {
                    // By the time this lowering is executed, there must be no private inline functions, however, there are exceptions, for example,
                    // suspendCoroutineUninterceptedOrReturn, which are somewhat magical.
                    // If we encounter one, just ignore it.
                    TransformerData(declaration)
                } else null
            } else {
                data
            }

            // Wrap it to the stage controller to avoid JS BE failing with not found lowered declaration signature
            // in `IrDeclaration.signatureForJsIC` cache.
            return declaration.factory.stageController.restrictTo(declaration) {
                super.visitFunction(declaration, newData)
            }
        }

        override fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: TransformerData?): IrElement {
            if (data == null /*|| !insideBody*/ || !expression.symbol.owner.isAbiPrivate)
                return super.visitFunctionAccess(expression, data)

            // TODO(KT-69527): Set the proper visibility for the accessor (the max visibility of all the inline functions that reference it)
            val accessor = accessorGenerator.getSyntheticFunctionAccessor(expression, null)
            val accessorExpression = accessorGenerator.modifyFunctionAccessExpression(expression, accessor.symbol)

            generatedAccessors += GeneratedAccessor(accessor, expression.symbol)

            return super.visitFunctionAccess(accessorExpression, data)
        }
    }

    companion object {
        private var IrFile.generatedAccessors: GeneratedAccessors? by irAttribute(followAttributeOwner = false)

        // TODO: Take into account visibilities of containers
        // TODO(KT-69565): It's not enough to just look at the visibility, since the declaration may be private inside a local class
        //   and accessed only within that class. For such cases we shouldn't generate an accessor.
        private val IrDeclarationWithVisibility.isAbiPrivate: Boolean
            get() = DescriptorVisibilities.isPrivate(visibility) || visibility == DescriptorVisibilities.LOCAL
    }
}
