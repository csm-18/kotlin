// ALLOW_KOTLIN_PACKAGE
// STDLIB_COMPILATION
// ISSUE: KT-65841

// MODULE: m1-common
// FILE: common.kt

// FILE: internal.kt

package kotlin.internal

internal annotation class ActualizeByJvmBuiltinProvider()

// FILE: builtins.kt

package kotlin

import kotlin.internal.ActualizeByJvmBuiltinProvider

@ActualizeByJvmBuiltinProvider
expect open class Any() {
    public open operator fun equals(other: Any?): Boolean

    fun funWithoutActual()
}

@ActualizeByJvmBuiltinProvider
expect class Boolean

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt
