# Shader DSL Development Tools

Agent-generated scripts for shader DSL development and debugging.

## Quick Reference

### DSL Testing (Pure Clojure - No Browser)

```bash
# Test DSL macro compilation
clojure -M .claude/scripts/test-dsl.clj

# Returns AST structure - catches macro bugs before browser
```

### WGSL Validation

```bash
# Validate WGSL file
.claude/scripts/validate-wgsl.sh shader.wgsl

# Validate WGSL from stdin
echo "fn main() { return vec4<f32>(1.0, 0.0, 0.0, 1.0); }" | .claude/scripts/validate-wgsl.sh
```

### Generated Shader Inspection

```bash
# In browser console:
console.log(starter.samples.rendering_2d.curves.quadratic_bezier_v2.bezier_fragment)

# Or use shadow-cljs REPL:
(js/console.log starter.samples.rendering-2d.curves.quadratic-bezier-v2/bezier-fragment)
```

## Development Workflow

1. **Write shader in DSL** (src/main/starter/samples/...)
2. **Test macro compilation** (run test-dsl.clj with your pattern)
3. **Check shadow-cljs build** (look for errors in logs/shadow-*.log)
4. **Test in browser** (verify rendering)
5. **Validate WGSL** (if needed, extract and run through naga)

## Debugging Tips

### Macro Not Expanding?
- Check syntax: params should be flat `[uv :vec2f]` not nested `[[uv :vec2f]]`
- Verify `:require-macros` for dsl and dsl-v2
- Touch the .clj file to force macro reload

### WGSL Parse Errors?
- Check integer literals have `.0` (1.0 not 1)
- Verify variable names converted (hyphens → underscores)
- Look for `return res = ...` (should be just `res = ...` for assign)

### Runtime Errors?
- Check browser console for exact WGSL error
- Print generated WGSL: `console.log(namespace/shader-var)`
- Verify AST structure matches expected keywords

## Tool Locations

- `test-dsl.clj` - Pure Clojure DSL tester
- `validate-wgsl.sh` - Naga WGSL validator wrapper
- `validate-shader.clj` - Full pipeline tester (WIP)
- `quick-shader-check.sh` - One-command validation

All tools are executable and documented with usage examples.
