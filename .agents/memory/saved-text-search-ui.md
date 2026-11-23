---
name: Saved page search placement
description: Why saved PDF text search stays inline in the page-review screen.
---

Keep saved PDF text search inline in page review, rather than introducing a
separate search window. The user explicitly chose inline search.

**Why:** Repeated native Robolectric checks would not settle for the search
dialog, including bounded and fixed-width layouts. A standalone labelled input
passed, and the same search state and input passed when placed inline. The exact
dialog failure was not established, so do not claim all Android dialogs or text
inputs are faulty, or that this proved a physical-device failure.

**How to apply:** Retain inline search when extending filters or match navigation.
Opening a hit may still use the existing saved-page review dialog. Keep original
page and exact extraction/retry identity intact; do not weaken interface checks
or substitute fresh OCR for saved text.
