---
name: Public native dependency downloads
description: Why public native build sources use archives rather than Git cloning in this workspace.
---

Use checksum-pinned public source archives for native build dependencies rather
than relying on this workspace's imported GitHub connection.

**Why:** During setup, public GitHub clone requests were rewritten through an
import connection that supported REST access but not Git credentials. This failed
even though the source was public. Downloading the exact pinned revision through
GitHub's public archive endpoint worked without account changes.

**How to apply:** Preserve both the source revision and archive checksum when
updating native dependencies. Do not change global Git credentials or require a
new integration just to fetch public build sources.
