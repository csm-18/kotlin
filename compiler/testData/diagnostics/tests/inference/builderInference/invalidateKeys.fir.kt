// WITH_STDLIB
fun foo(libraryInfoCache: LibraryInfoCache<String, String>, outdated: List<String>) {
    val droppedLibraryInfos = libraryInfoCache.invalidateKeys(outdated).<!NONE_APPLICABLE!>flatMapTo<!>(hashSetOf()) { <!UNRESOLVED_REFERENCE!>it<!> }
}

class LibraryInfoCache<Key, Value> {
    fun invalidateKeys(
        keys: Collection<Key>,
        validityCondition: ((Key, Value) -> Boolean)? = null
    ) {}
}
