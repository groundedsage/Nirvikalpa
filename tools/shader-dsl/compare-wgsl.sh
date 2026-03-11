#!/bin/bash
# Compare generated WGSL with expected/reference WGSL
# Usage: ./compare-wgsl.sh <generated.wgsl> <expected.wgsl>

set -e

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <generated.wgsl> <expected.wgsl>"
  exit 1
fi

GENERATED="$1"
EXPECTED="$2"

echo "========================================="
echo "WGSL Comparison"
echo "========================================="
echo "Generated: $GENERATED"
echo "Expected:  $EXPECTED"
echo ""

# Validate both files first
echo "[1/3] Validating generated WGSL..."
if naga "$GENERATED" > /dev/null 2>&1; then
  echo "✅ Generated WGSL is valid"
else
  echo "❌ Generated WGSL has errors:"
  naga "$GENERATED" 2>&1
  exit 1
fi

echo ""
echo "[2/3] Validating expected WGSL..."
if naga "$EXPECTED" > /dev/null 2>&1; then
  echo "✅ Expected WGSL is valid"
else
  echo "❌ Expected WGSL has errors:"
  naga "$EXPECTED" 2>&1
  exit 1
fi

echo ""
echo "[3/3] Comparing files..."
if diff -u "$EXPECTED" "$GENERATED"; then
  echo ""
  echo "✅ Files are identical"
else
  echo ""
  echo "ℹ️  Files differ (see above)"
fi
