@file:Suppress("unused", "DuplicatedCode")

// DO NOT EDIT MANUALLY!
// Generated by generators/tests/org/jetbrains/kotlin/generators/arguments/GenerateCompilerArgumentsCopy.kt
// To regenerate run 'generateCompilerArgumentsCopy' task

package org.jetbrains.kotlin.cli.common.arguments

@OptIn(org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI::class)
fun copyK2JSCompilerArguments(from: K2JSCompilerArguments, to: K2JSCompilerArguments): K2JSCompilerArguments {
    copyCommonKlibBasedCompilerArguments(from, to)

    to.cacheDirectory = from.cacheDirectory
    to.enableJsScripting = from.enableJsScripting
    to.extensionFunctionsInExternals = from.extensionFunctionsInExternals
    to.fakeOverrideValidator = from.fakeOverrideValidator
    to.friendModules = from.friendModules
    to.friendModulesDisabled = from.friendModulesDisabled
    to.generateDts = from.generateDts
    to.generatePolyfills = from.generatePolyfills
    to.includes = from.includes
    to.irBaseClassInMetadata = from.irBaseClassInMetadata
    to.irBuildCache = from.irBuildCache
    to.irDce = from.irDce
    to.irDceDumpDeclarationIrSizesToFile = from.irDceDumpDeclarationIrSizesToFile
    to.irDceDumpReachabilityInfoToFile = from.irDceDumpReachabilityInfoToFile
    to.irDcePrintReachabilityInfo = from.irDcePrintReachabilityInfo
    to.irDceRuntimeDiagnostic = from.irDceRuntimeDiagnostic
    to.irGenerateInlineAnonymousFunctions = from.irGenerateInlineAnonymousFunctions
    to.irKeep = from.irKeep
    to.irMinimizedMemberNames = from.irMinimizedMemberNames
    to.irModuleName = from.irModuleName
    to.irNewIr2Js = from.irNewIr2Js
    to.irOnly = from.irOnly
    to.irPerFile = from.irPerFile
    to.irPerModule = from.irPerModule
    to.irPerModuleOutputName = from.irPerModuleOutputName
    to.irProduceJs = from.irProduceJs
    to.irProduceKlibDir = from.irProduceKlibDir
    to.irProduceKlibFile = from.irProduceKlibFile
    to.irPropertyLazyInitialization = from.irPropertyLazyInitialization
    to.irSafeExternalBoolean = from.irSafeExternalBoolean
    to.irSafeExternalBooleanDiagnostic = from.irSafeExternalBooleanDiagnostic
    to.libraries = from.libraries
    to.main = from.main
    @Suppress("DEPRECATION")
    to.metaInfo = from.metaInfo
    to.moduleKind = from.moduleKind
    to.moduleName = from.moduleName
    to.noStdlib = from.noStdlib
    to.optimizeGeneratedJs = from.optimizeGeneratedJs
    to.outputDir = from.outputDir
    @Suppress("DEPRECATION")
    to.outputFile = from.outputFile
    to.platformArgumentsProviderJsExpression = from.platformArgumentsProviderJsExpression
    to.preserveIcOrder = from.preserveIcOrder
    to.sourceMap = from.sourceMap
    to.sourceMapBaseDirs = from.sourceMapBaseDirs
    to.sourceMapEmbedSources = from.sourceMapEmbedSources
    to.sourceMapNamesPolicy = from.sourceMapNamesPolicy
    to.sourceMapPrefix = from.sourceMapPrefix
    to.strictImplicitExportType = from.strictImplicitExportType
    to.target = from.target
    to.typedArrays = from.typedArrays
    to.useEsClasses = from.useEsClasses
    to.useEsGenerators = from.useEsGenerators
    to.wasm = from.wasm
    to.wasmDebug = from.wasmDebug
    to.wasmEnableArrayRangeChecks = from.wasmEnableArrayRangeChecks
    to.wasmEnableAsserts = from.wasmEnableAsserts
    to.wasmGenerateWat = from.wasmGenerateWat
    to.wasmKClassFqn = from.wasmKClassFqn
    to.wasmTarget = from.wasmTarget
    to.wasmUseNewExceptionProposal = from.wasmUseNewExceptionProposal
    to.wasmUseTrapsInsteadOfExceptions = from.wasmUseTrapsInsteadOfExceptions

    return to
}