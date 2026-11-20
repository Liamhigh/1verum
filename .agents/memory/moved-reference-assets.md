---
name: Moved reference assets
description: A workspace migration can silently omit previously tracked uploaded reference files.
---

When relocating existing tracked uploads, check that the destination files are
still eligible for version control, not merely present on disk.

**Why:** This environment's global Git ignore excludes files inside
`attached_assets`. Moving an already tracked reference there from a migration
backup made it untracked and ignored, even though its contents were preserved.

**How to apply:** Use `git check-ignore -v --no-index` on relocated tracked
references. Preserve only the specific original files with explicit exceptions;
do not broadly allowlist uploaded inputs or expose them through a web artifact.
