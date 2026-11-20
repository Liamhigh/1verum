# Verum Omnis palette refinement

Focused styling-only pass on the native Jetpack Compose UI. The canonical palette and typography are unchanged.

## Roles shifted

- Shared cards now use off-white serif headings and blue icons; confirmation-dialog titles are off-white. Eyebrow kickers and primary gold buttons remain gold.
- Top-bar back/navigation icons and the shared action-sheet heading/icons use muted blue instead of gold.
- Landing hero wash, sealed count, recent-item initials, and “View All” use neutral/blue roles; the Seal CTA remains gold.
- Scan-home section label, case-file chrome, process arrows, capability icons, and step numerals use blue. Active pipeline progress and primary actions stay gold.
- Chat action chrome, input focus/cursor, prompt labels, expand link, and thinking progress use blue. Seal/Send actions remain gold.
- Report card identifiers, report metadata, neutral finding links, and digest/supporting values now use blue, off-white, or body text. Gold remains for primary actions and selected/high-signal statuses.
- Vault empty-state icon, helper link, vault kicker, initials, and artifact-kind labels use blue. Gold remains on selected folder chips and the primary FAB.
- Sealing page neutral section headings and the attachment/verification controls use off-white or blue; gold remains on the seal CTA, selected mode/type, and active/status emphasis.
- Email audit filenames and export link use blue; primary send and warning verdict remain gold.

No copy, navigation, state, or data-flow behavior was changed. Chat message expansion, Report finding expansion, and the Vault empty state were left intact.
