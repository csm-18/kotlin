// !DIAGNOSTICS: -UNUSED_PARAMETER
// DONT_STOP_ON_FIR_ERRORS
// MODULE: m1-common
// FILE: common.kt

expect open class Container {
    internal open fun internalFun()
}

// MODULE: m2-jvm()()(m1-common)

// FILE: foo/Foo.java

package foo;

public class Foo {
    public void internalFun() {}
}

// FILE: jvm.kt

actual typealias <!NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS!>Container<!> = foo.Foo
