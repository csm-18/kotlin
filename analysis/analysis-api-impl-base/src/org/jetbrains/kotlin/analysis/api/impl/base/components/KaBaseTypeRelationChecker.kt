/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.impl.base.components

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaSubtypingErrorTypePolicy
import org.jetbrains.kotlin.analysis.api.components.KaTypeRelationChecker
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId

@KaImplementationDetail
abstract class KaBaseTypeRelationChecker<T : KaSession> : KaSessionComponent<T>(), KaTypeRelationChecker {
    override fun KaType.isSubtypeOf(classId: ClassId, errorTypePolicy: KaSubtypingErrorTypePolicy): Boolean {
        if (this is KaErrorType) return errorTypePolicy == KaSubtypingErrorTypePolicy.LENIENT
        if (this !is KaClassType) return false

        return isSubtypeOf(classId, errorTypePolicy)
    }

    protected abstract fun KaClassType.isSubtypeOf(classId: ClassId, errorTypePolicy: KaSubtypingErrorTypePolicy): Boolean
}
