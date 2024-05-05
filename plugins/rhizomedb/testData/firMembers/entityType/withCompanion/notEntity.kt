import com.jetbrains.rhizomedb.*

interface BaseInterface

interface DerivedInterface : BaseInterface

<!NOT_ENTITY!>@GeneratedEntityType
data class MyEntity(val eid: EID) : DerivedInterface {
    // OPTIONAL_COMPANION
    companion object {
        val X = 42
    }
}<!>

fun foo() {
    MyEntity.<!NONE_APPLICABLE!>all<!>()
    MyEntity.<!NONE_APPLICABLE!>single<!>()
    MyEntity.<!NONE_APPLICABLE!>singleOrNull<!>()
}
