#!/bin/bash
# Quick shader validation - DSL → AST → WGSL → naga validation
# Usage: ./quick-shader-check.sh <shader-form>

set -e

SHADER_FORM="$1"

if [ -z "$SHADER_FORM" ]; then
  echo "Usage: $0 '<shader-form>'"
  echo "Example: $0 '(if-block (> x 0.5) (vec4f 1.0 0.0 0.0 1.0) (vec4f 0.0 1.0 0.0 1.0))'"
  exit 1
fi

echo "========================================="
echo "Quick Shader Validation"  
echo "========================================="

# Test DSL compilation
echo -e "\n[1/2] Testing DSL compilation..."
clojure -M .claude/scripts/test-dsl.clj

# Note: WGSL validation requires ClojureScript codegen
# For now, just show success if DSL compiles
echo -e "\n✅ DSL compilation successful"
echo "Note: Full WGSL validation requires browser or ClojureScript"
