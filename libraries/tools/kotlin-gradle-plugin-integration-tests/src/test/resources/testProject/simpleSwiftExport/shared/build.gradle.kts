plugins {
    kotlin("multiplatform")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl::class)
    swiftexport {
        moduleName.set("Shared")
        flattenPackage.set("com.github.jetbrains.swiftexport")

        export(project(":subproject")) {
            moduleName.set("Subproject")
            flattenPackage.set("com.subproject.library")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":subproject"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":subproject"))
        }
    }
}
