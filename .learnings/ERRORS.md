## [ERR-20260628-001] presentations_artifact_tool_setup

**Logged**: 2026-06-28T00:00:00+08:00
**Priority**: medium
**Status**: resolved
**Area**: docs

### Summary
Presentation artifact-tool workspace setup looked for `@oai/artifact-tool` under the repository `.cache` path and failed because that package was absent there.

### Error
```text
Error: Expected the bundled Codex runtime @oai/artifact-tool package to point to @oai/artifact-tool.
Checked D:\Code\agent-auth-design\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules\@oai\artifact-tool; found missing package.json.
```

### Context
- Command attempted: `node ...\setup_artifact_tool_workspace.mjs --workspace <tmp>`
- Environment: Windows 11, project-backed Codex desktop workspace.
- Correct dependency location was available via `load_workspace_dependencies`: `C:\Users\HUAWEI\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules`.

### Suggested Fix
Use the bundled Node executable and set `NODE_PATH` or add the runtime `node_modules` directory when running presentation generation scripts.

### Metadata
- Reproducible: unknown
- Related Files: docs/cloud-agent-security-auth-delegation-ppt-outline.md

### Resolution
- **Resolved**: 2026-06-28T00:00:00+08:00
- **Notes**: Created a temporary `node_modules` junction to the bundled runtime dependencies so ESM package resolution could find `@oai/artifact-tool`. Avoided artifact-tool PNG rendering because it silently exited in this environment; used PowerPoint COM export for slide preview instead.

---

## [ERR-20260629-001] local_python_not_on_path

**Logged**: 2026-06-29T00:00:00+08:00
**Priority**: low
**Status**: pending
**Area**: docs

### Summary
Attempted to use `python` for temporary image upscaling, but `python.exe` was not available on PATH in this Windows workspace.

### Error
```text
Program 'python.exe' failed to run: The system cannot find the path specified
```

### Context
- Command attempted: inline PIL script piped to `python -`
- Purpose: upscale a low-resolution reference diagram for visual inspection.
- Environment: Windows 11, PowerShell.

### Suggested Fix
Use the bundled workspace Python from `load_workspace_dependencies` when Python libraries are needed, or use PowerShell/.NET `System.Drawing` for simple temporary image resizing.

### Metadata
- Reproducible: yes
- Related Files: outputs/app-interaction-security-vacuum-slide-10-human-in-loop-dynamic-delegation-prompt.md

---
