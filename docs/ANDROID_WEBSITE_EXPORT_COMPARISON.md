# Android report export vs. website snapshot

## Scope and evidence

No forensic website source ZIP or implementation was present in the workspace.
The only website references available were these saved public-page snapshots:

- `attached_assets/Verum_Omnis_-_AI_Forensics_for_Truth_1795172409993.mht`
- `attached_assets/Seal_Document_—_Verum_Omnis_(2)_1795172520071.mht`

The workspace ZIP search found no website source archive. The ZIPs present were
unrelated Android symbols and dependency test fixtures. The website statements
below are therefore attributed to the snapshots; they are not verified behavior
of the live website or its generated files.

Android findings below are from the scan worker, report generator, PDF exporter,
renderer, vault, and report UI. No generated PDF bytes were inspected as part of
this comparison.

## Verified comparison

| Area | Website snapshot says | Android report path does |
|---|---|---|
| Scan and report | The sealing page offers a seal-only mode and a forensic-report mode. It describes a deterministic browser scan, with optional AI review that sends bounded material to Cloudflare Workers AI. | `ForensicScanWorker` runs `ForensicService.scan`, generates a report, saves the report and findings, and exports a PDF. `ReportScreen` offers report generation after a scan and sharing the resulting PDF. This path has no seal-only choice; the app has a separate document-sealing surface. |
| What the seal covers | The page describes SHA-256 hashes of the original upload and delivered file, a SHA-512 fingerprint, and OpenTimestamps anchoring of the delivered-file digest. | `ReportGenerator` seals the UTF-8 bytes of `report.body` with SHA-512 before the PDF is rendered. The generated narrative appendix is explicitly outside that body seal. The scan worker does not anchor the exported PDF in OpenTimestamps. The exporter stores the PDF but does not retain the SHA-512 returned by `storeSealedReport`. |
| Watermark and QR | The page describes a watermark and footer on every page and a QR code in the page margin. | The Android report PDF applies a watermark to the cover, report pages, and exhibit pages. The cover uses a faint watermark; regular and exhibit pages use the renderer's approximately 18% alpha setting. The QR is drawn on the cover only, near its bottom center. Regular and exhibit pages have footers but no QR. |
| Seal details | The page describes a certificate with original/delivered file hashes, seal ID, file size, and chain-of-custody details, plus downloadable PDF, `.ots` receipt, and anchor certificate. | The Android report seal record includes a full SHA-512 of the report body, a shortcode, Constitution version/ruleset, timestamp, and status. The cover QR contains a 32-hex-character hash prefix and encoded metadata; the repeating footer displays shortened hash information and page metadata. This scan/export path does not produce the website-described download set. |
| Vault and sharing | The snapshot offers browser downloads and directs users to website verification. | The report PDF is saved under the app's private `files/vault/reports/sealed` directory. The report screen shares it through an Android chooser using a read-only `FileProvider` URI. The vault can open a file in another app or fall back to sharing it. |

## PDF/A status

The main website snapshot claims reports are exported as PDF/A-3B. That is a
public-page claim, not evidence from the website's source or an output file.
The Android report exporter uses Android's `PdfDocument`; the inspected code
does not show a PDF/A conversion or validation step. Neither statement establishes
whether either platform's actual generated PDF conforms to PDF/A. No conformance
claim is made here.

## What remains unverified

- Website implementation details, including the actual scan and export pipeline,
  because its source ZIP was not available.
- Whether website-generated files match the behavior described by the snapshots.
- PDF/A conformance for either platform, because actual output bytes were not
  examined.
