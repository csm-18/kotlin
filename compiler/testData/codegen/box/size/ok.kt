// TARGET_BACKEND: WASM

// RUN_THIRD_PARTY_OPTIMIZER
// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm 16_790
// WASM_DCE_EXPECTED_OUTPUT_SIZE:  mjs  6_084
// WASM_OPT_EXPECTED_OUTPUT_SIZE:       4_451

fun box() = "OK"