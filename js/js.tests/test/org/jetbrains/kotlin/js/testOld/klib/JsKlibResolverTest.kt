/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.testOld.klib

import junit.framework.TestCase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.js.K2JsIrCompiler
import org.jetbrains.kotlin.test.TestCaseWithTmpdir
import org.jetbrains.kotlin.test.services.StandardLibrariesPathProviderForKotlinProject
import org.jetbrains.kotlin.test.util.JUnit4Assertions
import org.jetbrains.kotlin.test.utils.assertCompilerOutputHasKlibResolverIncompatibleAbiMessages
import org.jetbrains.kotlin.test.utils.patchManifestToBumpAbiVersion
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class JsKlibResolverTest : TestCaseWithTmpdir() {
    val DUPLICATED_UNIQUE_NAME = "DUPLICATED_UNIQUE_NAME"

    fun testWarningAboutRejectedLibraryIsNotSuppressed() {
        val testDataDir = File("compiler/testData/klib/resolve/mismatched-abi-version")

        val lib1V1 = createKlibDir("lib1", 1)

        compileKlib(
            sourceFile = testDataDir.resolve("lib1.kt"),
            outputFile = lib1V1
        ).assertSuccess() // Should compile successfully.

        compileKlib(
            sourceFile = testDataDir.resolve("lib2.kt"),
            dependencies = arrayOf(lib1V1),
            outputFile = createKlibDir("lib2", 1)
        ).assertSuccess() // Should compile successfully.

        // Now patch lib1:
        val lib1V2 = createKlibDir("lib1", 2)
        lib1V1.copyRecursively(lib1V2)
        patchManifestToBumpAbiVersion(JUnit4Assertions, lib1V2)

        val result = compileKlib(
            sourceFile = testDataDir.resolve("lib2.kt"),
            dependencies = arrayOf(lib1V2),
            outputFile = createKlibDir("lib2", 2)
        )

        result.assertFailure() // Should not compile successfully.

        assertCompilerOutputHasKlibResolverIncompatibleAbiMessages(JUnit4Assertions, result.output, missingLibrary = "/v2/lib1", tmpdir)
    }

    fun testWarningAboutDuplicatedUniqueNames() {
        val result = compilationResultOfModulesWithDuplicatedUniqueNames(arrayOf("-Xklib-disallow-duplicated-unique-names"))
        result.assertFailure()

        val compileroOutputLines = result.output.lines()
        TestCase.assertTrue(compileroOutputLines.any {
            it.startsWith("warning: KLIB resolver: The same 'unique_name=$DUPLICATED_UNIQUE_NAME' found in more than one library")
        })
        TestCase.assertTrue(compileroOutputLines.any {
            it.contains("error: unresolved reference")
        })
    }

    fun testAllKlibsUsedDespiteWarningAboutDuplicatedUniqueNames() {
        val result = compilationResultOfModulesWithDuplicatedUniqueNames(emptyArray())
        result.assertSuccess()

        val compileroOutputLines = result.output.lines()
        TestCase.assertTrue(compileroOutputLines.any {
            it.startsWith("warning: KLIB resolver: The same 'unique_name=$DUPLICATED_UNIQUE_NAME' found in more than one library")
        })
        TestCase.assertTrue(compileroOutputLines.none {
            it.contains("error: unresolved reference")
        })
    }

    private fun compilationResultOfModulesWithDuplicatedUniqueNames(extraArg: Array<String>): CompilationResult {
        val testDataDir = File("compiler/testData/klib/resolve/duplicate-unique-name")

        val dirA = createKlibDir(DUPLICATED_UNIQUE_NAME, 1)
        compileKlib(
            sourceFile = testDataDir.resolve("a.kt"),
            outputFile = dirA,
        ).assertSuccess() // Should compile successfully.

        val dirB = createKlibDir(DUPLICATED_UNIQUE_NAME, 2)
        compileKlib(
            sourceFile = testDataDir.resolve("b.kt"),
            outputFile = dirB
        ).assertSuccess() // Should compile successfully.

        return compileKlib(
            sourceFile = testDataDir.resolve("c.kt"),
            dependencies = arrayOf(dirA, dirB),
            outputFile = createKlibDir("c", 1),
            extraArgs = extraArg,
        )
    }

    private fun createKlibDir(name: String, version: Int): File =
        tmpdir.resolve("v$version").resolve(name).apply(File::mkdirs)

    private fun compileKlib(
        sourceFile: File,
        dependencies: Array<File> = emptyArray(),
        outputFile: File,
        extraArgs: Array<String> = emptyArray(),
    ): CompilationResult {
        val libraries = listOfNotNull(
            StandardLibrariesPathProviderForKotlinProject.fullJsStdlib(),
            *dependencies
        ).joinToString(File.pathSeparator) { it.absolutePath }

        val args = arrayOf(
            "-Xir-produce-klib-dir",
            "-libraries", libraries,
            "-ir-output-dir", outputFile.absolutePath,
            "-ir-output-name", outputFile.nameWithoutExtension,
            *extraArgs,
            sourceFile.absolutePath
        )

        val compilerXmlOutput = ByteArrayOutputStream()
        val exitCode = PrintStream(compilerXmlOutput).use { printStream ->
            K2JsIrCompiler().execFullPathsInMessages(printStream, args)
        }

        return CompilationResult(exitCode, compilerXmlOutput.toString())
    }

    private data class CompilationResult(val exitCode: ExitCode, val output: String) {
        fun assertSuccess() = JUnit4Assertions.assertTrue(exitCode == ExitCode.OK) {
            buildString {
                appendLine("Expected exit code: ${ExitCode.OK}, Actual: $exitCode")
                appendLine("Compiler output:")
                appendLine(output)
            }
        }

        fun assertFailure() = JUnit4Assertions.assertTrue(exitCode != ExitCode.OK) {
            buildString {
                appendLine("Expected exit code: any but ${ExitCode.OK}, Actual: $exitCode")
                appendLine("Compiler output:")
                appendLine(output)
            }
        }
    }
}
