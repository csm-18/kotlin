plugins {
    java
    id("jps-compatible")
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.0")
}

sourceSets {
    "main" { generatedDir() }
    "test" { none() }
}
