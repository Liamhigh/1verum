# VS Code Build Prompt — Android UI Redesign (Verum Omnis)

Tracked so it survives sessions and nothing regresses. Paste the block below to
the VS Code Claude instance (it has the Android SDK / Gradle). Branch:
`claude/placeholder-rbqx8w`.

**Authoritative references (read before building):**
- `docs/ANDROID_UI_REDESIGN_SPEC.md` — screen-by-screen spec (incl. §4.6 shared GHRP contract).
- `docs/design/Verum-Vault-Android.dc.html` — pixel-reference mock-up.
- `ON_DEVICE_LLM_ARCHITECTURE.md`, `core/DeviceTier.kt`, `engine/llm/ModelCatalog.kt` — model roster (already correct; verify, don't change).
- `docs/reference/firewall/` — the actual firewall TS source + tests to port for external corroboration + deep research (parity, not prose).

---

## Prompt

> Verum Omnis — Android (`1verum`), branch `claude/placeholder-rbqx8w`.
>
> **Reset first** (previous work is merged to main):
> ```
> git fetch origin main
> git checkout -B claude/placeholder-rbqx8w origin/main
> ```
>
> **Read before building — don't rebuild what exists.** Confirm the model roster in
> `DeviceTier.kt`/`ON_DEVICE_LLM_ARCHITECTURE.md` (Gemma 3 writer always-on; Phi-3
> communicator ≥2GB; Gemma 4 communicator flagship ≥8GB; Legal + R&D ≥8GB; **no
> Mistral on-device**; 9-Brain always on ⇒ verification always triple; communicator
> `UNRESTRICTED` but constitution-bound). Verify; change only if wrong.
>
> **Build in green-building tranches, not one giant diff:**
> 1. **Theme + chat branding + standalone chat + GPS.** Website theme (navy `#040D1B`,
>    gold `#D4A843`, blue `#4A7EC7`, Cormorant + Courier) across all screens. Chat
>    branded **"Verum Omnis"**, avatar = `vo_badge.png` clipped to a circle; remove
>    every "Phi-3"/"Forensic Engine" responder label. Chat works **standalone with an
>    empty vault**. Capture + record **real GPS** at seal time.
> 2. **Vault:** per-item delete, Empty Vault, folder chips, and **grouped scan-sets**
>    ({sealed original + sealed report PDF + findings JSON} as ONE expandable entry).
> 3. **Compose/Seal:** password-protect toggle; seal **with vs without** forensic scan;
>    add-details (parties/jurisdiction) + add-identity/GPS-in-QR. Fix the scan hang —
>    drive the green-lights pipeline from **real `NineBrainEngine` progress** with a
>    visible failure state.
> 4. **External corroboration + deep research** — port `docs/reference/firewall/`
>    (`externalCorroboration.ts`, `deepResearch.ts`) to Kotlin via
>    `WebSearchService`/`OjrsClient`/`DeepResearchEngine`, mirroring the test cases
>    1:1. Preserve the five invariants (entitlement gate; signal-or-silence;
>    never-mutate/never-override/EXTERNAL-UNSEALED/contextual-only; sanitise fetched
>    text; sealed-only deep-research context). Report follows the 19-section GHRP.
>
> **Per tranche:** commit to `claude/placeholder-rbqx8w`, run `./gradlew assembleDebug`
> (report `BUILD SUCCESSFUL` or exact errors) + unit tests, push, and tell Liam's
> session — it opens the PR and merges on your local green (GitHub Actions "Build &
> Test" is failing in ~3s with no logs = the Actions billing/infra issue, not code).
>
> **Rules:** develop only on `claude/placeholder-rbqx8w`; don't touch the website; the
> AI's user-facing name is "Verum Omnis" only (never surface model names); keep the
> deterministic engine — Gemma is the writer/hybrid layer over it.

---

## Notes for the coordinating session
- Merge each tranche as its own PR on local `BUILD SUCCESSFUL` while Actions is down; re-check CI when it recovers.
- The firewall reference is a **snapshot**; if `firebase` changes those files, re-copy into `docs/reference/firewall/`.
