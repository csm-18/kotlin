open class A {
    open val foo: String = "1"
}

class B : A() {
    override val foo: String by lazy {
        // CHECK_CALLED_IN_SCOPE: function=B$foo$delegate$lambda scope=new_B_pyal3a_k$ TARGET_BACKENDS=JS_IR_ES6
        // CHECK_NOT_CALLED_IN_SCOPE: function=B$foo$delegate$lambda scope=B TARGET_BACKENDS=JS_IR
        super.foo + "2"
    }

    val bar: String by lazy {
        // CHECK_NOT_CALLED_IN_SCOPE: function=B$bar$delegate$lambda scope=new_B_pyal3a_k$ TARGET_BACKENDS=JS_IR_ES6
        // CHECK_NOT_CALLED_IN_SCOPE: function=B$bar$delegate$lambda scope=B TARGET_BACKENDS=JS_IR
        object : A() {
            fun foo2() =
                this@B.foo + // Make sure the lambda is contextful
                        super.foo + // This 'super' should not affect the generation of the lambda, because it's the local class's 'super'
                        "3"
        }.foo2()
    }
}

fun box(): String {
    var result = ""

    result += B().foo
    result += " "
    result += B().bar

    return if (result == "12 1213") {
        "OK"
    } else {
        result
    }
}
