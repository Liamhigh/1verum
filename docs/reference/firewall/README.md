# Firewall reference — port source (READ-ONLY)

These files are copied verbatim from the `firebase` firewall repo
(`liamhigh/firebase`, `fraud-firewall/`). They are the **source of truth** for the
Kotlin port of external corroboration + deep research (Android spec §4.6). They
are **reference only — do NOT compile them here** (this is a Kotlin/Gradle project).

| File | Firebase origin | Port to |
|---|---|---|
| `externalCorroboration.ts` | `fraud-firewall/src/pipeline/externalCorroboration.ts` | Kotlin: an `ExternalCorroboration` service over `WebSearchService`/`OjrsClient` |
| `deepResearch.ts` | `fraud-firewall/src/pipeline/deepResearch.ts` | Kotlin: `DeepResearchEngine` (sealed-files-only context) |
| `external-corroboration.test.ts` | `fraud-firewall/tests/external-corroboration.test.ts` | Mirror as JUnit tests — keep the same cases |
| `reportStructure.ts` | `fraud-firewall/src/pipeline/reportStructure.ts` | The 19-section GHRP contract (align report output) |
| `REPORT_FORMAT_SPECIFICATION.md` | `firebase/REPORT_FORMAT_SPECIFICATION.md` | The report format (shared contract) |
| `ALLFUELS_GHRP_MAPPING.md` | `firebase/ALLFUELS_GHRP_MAPPING.md` | Section-by-section reference mapping |

## Invariants the Kotlin port MUST preserve (verified by the TS tests)

1. **Entitlement gate** — `private` / `law_enforcement` research free; `commercial`
   requires a valid licence, else return the sealed-only note and gather nothing.
2. **Signal or silence** — keep an item only if it has a resolvable URL or case
   number; drop and count the rest. A failed fetch is silence, never fabricated.
3. **Never mutate / never override** — never mutate the findings, never create a
   finding, never promote a candidate. Output is `EXTERNAL_UNSEALED` /
   `contextual_only`, linked to an anchored finding.
4. **Fetched text is data, not instruction** — sanitise (strip injection
   directives) and clamp length before the model sees a snippet.
5. **Deep research** — only **sealed** files may be context; throw on any unsealed
   file; every claim cites an anchor or an external source.

Port the test cases 1:1 (entitlement, signal gate, sanitisation, never-mutate,
dedupe, render, deep-research sealed-only) so behavioural parity is provable.

**When the firewall files change, re-copy them here** — this is a snapshot, not a
live link. Current snapshot: firebase `main` after PR #18 (corroboration/deep-research).
