# Learnings

## [LRN-20260629-001] correction

**Logged**: 2026-06-29T00:00:00+08:00
**Priority**: high
**Status**: pending
**Area**: docs

### Summary
When a user asks for a PPT/image prompt to follow a reference diagram, preserve the diagram topology exactly instead of abstracting it into a cleaner generic layout.

### Details
The slide 10 prompt originally emphasized "minimal text" and "clean layout", which caused the generator to produce a large-title, icon-card architecture page rather than a refined version of the user's hand-drawn reference diagram. The correct approach is to explicitly lock node coordinates, node style, arrow directions, color semantics, labels, and numbered steps, while only allowing visual cleanup.

### Suggested Action
For future reference-image prompt work, state whether the reference is a strict layout source or only a style source. If strict, include "do not redesign", fixed relative positions, required lines/labels, and examples of wrong outputs to avoid.

### Metadata
- Source: user_feedback
- Related Files: outputs/app-interaction-security-vacuum-slide-10-human-in-loop-dynamic-delegation-prompt.md
- Tags: ppt-prompt, reference-image, diagram-layout

---
