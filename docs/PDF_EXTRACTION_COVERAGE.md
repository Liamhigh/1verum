# PDF extraction coverage

PDF screening conclusions apply only to extracted text. An absent contradiction
indicator does not establish that the original PDF is clean, complete, or
accurately transcribed.

## Page statuses

All recorded page numbers refer to the **original PDF**, starting at 1.

- `TEXT_LAYER`: embedded text met the extraction threshold. It is not an
  assurance that drawings, images, annotations, or all visible text were read.
- `OCR`: the on-device recognizer returned nonblank text.
- `UNREAD`: recognition ran but returned no text. This does not prove a page is
  blank.
- `FAILED`: parsing, rendering, or OCR could not read the page. Failures are
  recorded without including raw exception messages or document contents.
- `SKIPPED`: the page deliberately received no OCR because the attempt budget
  was exhausted. The original page remains in the coverage record.

The default policy attempts OCR when the embedded text is blank or shorter than
30 characters. Each PDF has a limit of **200 page attempts, including failed
attempts**. Rendering targets 200 dpi, capped at 2600 pixels per dimension.
Recognition may omit or misread handwriting, tables, images, low-resolution
scans, unsupported scripts, or other content. Manual review is necessary even
when every page returned text.

## Persistence and presentation

Both document ingest and seal-with-forensic-report carry coverage alongside text.
Coverage records are saved in the private vault, including when no text can be
read and the report flow stops. Findings JSON adds `extraction_coverage` and
`coverage_warnings`; each document includes its evidence identity, original name,
SHA-512 and page records. Reports retain the structured records and include
coverage warnings and page provenance in the sealed report body.

Unknown page counts remain unknown rather than becoming zero. Older/text-only
PDF extractors receive an explicit unknown-coverage warning; existing saved
reports and findings remain deserializable. Incomplete coverage cannot be counted
as a verified result on the landing screen.

## Verification boundary

The tests generate synthetic PDFs with embedded-text and raster-image pages.
They exercise the real PDFBox text-layer parser, the file and byte-array paths,
ingest, saved findings and report serialization. Recognition is substituted to
exercise no-text returns, per-page errors, initialization failures and the OCR
cap deterministically. A native Compose screenshot test checks visible warnings.

These checks do **not** establish parity with ML Kit recognition on a physical
phone, any particular real document's indicator count, or final PDF bytes written
by Android's native PDF writer. No private uploads are test fixtures.
