plugins {
    java
    id("jps-compatible")
}

dependencies {
    implementation(intellijRuntimeAnnotations())
}

sourceSets {
    "main" { generatedDir() }
    "test" { none() }
}
