/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parcelize

class AdditionalAnnotations<T>(
    val parcelize: List<T>,
    val ignoredOnParcel: List<T>,
) {
    fun <R> map(f: (T) -> R): AdditionalAnnotations<R> =
        AdditionalAnnotations(
            parcelize = parcelize.map(f),
            ignoredOnParcel = ignoredOnParcel.map(f),
        )
}