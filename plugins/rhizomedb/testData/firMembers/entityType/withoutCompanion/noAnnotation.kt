// File generated by `org.jetbrains.rhizomedb.TestGeneratorKt`. DO NOT MODIFY MANUALLY
import com.jetbrains.rhizomedb.*

data class MyEntity(override val eid: EID) : Entity

fun foo() {
    MyEntity.<!UNRESOLVED_REFERENCE!>all<!>()
    MyEntity.<!UNRESOLVED_REFERENCE!>single<!>()
    MyEntity.<!UNRESOLVED_REFERENCE!>singleOrNull<!>()
}
