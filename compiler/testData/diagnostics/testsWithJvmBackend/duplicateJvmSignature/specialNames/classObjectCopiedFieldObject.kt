// FIR_IDENTICAL
// DONT_STOP_ON_FIR_ERRORS

class B {
    companion object <!REDECLARATION!>A<!> {
    }

    val <!REDECLARATION!>A<!>: A = B.A
}

class C {
    companion object A {
        val A: A = C.A
    }
}

<!CONFLICTING_JVM_DECLARATIONS!>class D {
    companion object A {
        <!CONFLICTING_JVM_DECLARATIONS!>lateinit var A: A<!>
    }
}<!>
