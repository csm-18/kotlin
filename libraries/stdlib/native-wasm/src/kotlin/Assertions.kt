/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin

/**
 * Throws an [AssertionError] if the [value] is false.
 */
internal expect fun assert(value: Boolean)

/**
 * Throws an [AssertionError] calculated by [lazyMessage] if the [value] is false.
 */
internal expect fun assert(value: Boolean, lazyMessage: () -> Any)

// TODO consider to change these two fields into enum
//@SinceKotlin("2.1")
@PublishedApi
internal enum class AssertionMode {
    ENABLED, DISABLED, CONDITIONS_ONLY
}

//@SinceKotlin("2.1") // TODO enable when version will be changed. Otherwise we get erorr because if -Werrro
@PublishedApi
internal val evaluateAssertionArguments: Boolean
    get() {
        throw NotImplementedError("Implemented as intrinsic")
    }

//@SinceKotlin("2.1")
@PublishedApi
internal val evaluateAssertionBody: Boolean
    get() {
        throw NotImplementedError("Implemented as intrinsic")
    }
