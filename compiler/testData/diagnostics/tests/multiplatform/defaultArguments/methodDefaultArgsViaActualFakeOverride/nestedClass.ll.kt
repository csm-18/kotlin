// DONT_STOP_ON_FIR_ERRORS
// MODULE: m1-common
// FILE: common.kt
expect class Foo {
    class Bar() {
        fun foo(p: Int = 1)
    }
}

// MODULE: m2-jvm()()(m1-common)
// FILE: jvm.kt
open class Base {
    fun foo(p: Int) {}
}

actual class Foo {
    actual class Bar : <!DEFAULT_ARGUMENTS_IN_EXPECT_ACTUALIZED_BY_FAKE_OVERRIDE!>Base()<!>
}
