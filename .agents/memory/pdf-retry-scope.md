---
name: PDF retry scope
description: Why OCR retries are separate PDF-only analyses instead of revisions to an active mixed-evidence case.
---

Keep OCR retries explicitly labelled as PDF-only derivative analyses. Do not silently
replace the active case, merge new page text into an earlier sealed report, or
suggest that other case evidence has been re-analysed.

**Why:** Retrying extraction changes the text available to the engine, not the
original evidence. A PDF-only retry cannot stand in for a revised mixed-evidence
case containing other documents, audio or media. Keeping it separate protects the
meaning of earlier sealed findings and avoids silently changing the user's case.

**How to apply:** Any future case-wide revision must be an explicit new analysis
with the full intended evidence corpus. Preserve the earlier reports and source
hashes, and state the scope of the new report.
