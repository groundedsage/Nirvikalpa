#!/bin/bash
# WGSL Validator using naga
# Usage: ./validate-wgsl.sh <wgsl-file>
# Or pipe WGSL: echo "$wgsl" | ./validate-wgsl.sh

if [ -t 0 ]; then
  # File input
  if [ -z "$1" ]; then
    echo "Usage: $0 <wgsl-file>"
    echo "   or: echo \"\$wgsl\" | $0"
    exit 1
  fi
  naga "$1"
else
  # Stdin input
  naga --stdin-path shader.wgsl
fi
