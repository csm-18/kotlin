// TARGET_BACKEND: JVM_IR
// WITH_STDLIB

@file:JvmName("TestKt")
package test

import android.os.Parcelable
import kotlinx.parcelize.*

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class TriggerIgnore

@Parcelize
data class User(val x: Int, @TriggerIgnore val name: String = "default") : Parcelable

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