// TARGET_BACKEND: JVM_IR
// LANGUAGE: +MultiPlatformProjects
// WITH_STDLIB

// Metadata compilations do not see through the expect/actuals and therefore,
// there are parcelize errors in metadata compilations.
// IGNORE_FIR_DIAGNOSTICS_DIFF

// MODULE: m1-common
// FILE: common.kt

package test

expect interface MyParcelable

annotation class TriggerParcelize

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class TriggerIgnore

@TriggerParcelize
data class User(val x: Int, @TriggerIgnore val name: String = "default") : MyParcelable

// MODULE: m2-jvm()()(m1-common)
// FILE: android.kt

@file:JvmName("TestKt")
package test

import kotlinx.parcelize.*

actual typealias MyParcelable = android.os.Parcelable

fun box() = parcelTest { parcel ->
    val user = User(1, "John")
    user.writeToParcel(parcel, 0)

    val bytes = parcel.marshall()
    parcel.unmarshall(bytes, 0, bytes.size)
    parcel.setDataPosition(0)

    val user2 = parcelableCreator<User>().createFromParcel(parcel)
    assert(user2.name == "default")
    assert(user2.x == user.x)
}