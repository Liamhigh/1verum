---
name: Native test memory limits
description: Host memory constraints for broad Robolectric and native-graphics verification.
---

Keep broad native Android test runs memory-bounded, with a single test worker
and a fresh worker process per class when running the full suite.

Robolectric on this host does not implement `android.graphics.pdf.PdfDocument`;
tests that write PDF bytes should be paired with renderer-pixel and vault-export
tests, and final byte generation should be verified on Android.

**Why:** The full suite exceeded this host's 8 GiB memory limit and the operating
system killed Gradle without an assertion failure. Isolating classes allowed the
same unchanged tests to finish successfully. JVM heap limits alone do not cover
all native-graphics memory. The native PDF writer is also unavailable in this
Robolectric environment, so an export test may be skipped without indicating a
device-side failure.
Actual report PDFs supplied from an Android device were readable and rendered
with the watermark despite those JVM skips. Do not replace the device PDF writer
solely to make unsupported Robolectric serialization pass.

PDF text-layer replays and synthetic tests with substituted OCR do not establish
parity with phone ML Kit recognition or an expected indicator count for real
evidence. Page-coverage correctness, phone recognition accuracy, and final native
PDF byte verification are separate claims.

**Why:** The user explicitly distinguished incomplete-reading warnings from
physical-device PDF verification; a local text-layer replay cannot validate
device OCR.

**How to apply:** Describe exactly which extraction boundary a test exercises.
Use synthetic evidence for regression fixtures and keep OCR-accuracy/device
verification claims separate from deterministic coverage tests.

**How to apply:** Use an opt-in local Gradle test init configuration with
`maxParallelForks = 1`, `forkEvery = 1`, and `maxHeapSize = "512m"`; a 1 GiB
Gradle heap was sufficient. Keep host-only test limits separate from app
behavior, release configuration, and the native inference library. Verify
watermark drawing and vault persistence on the JVM, then test final PDF bytes
on-device.

Robolectric's MIME registry can be empty even for standard Android PDF types.
**Why:** FileProvider returned the generic binary type in JVM tests despite a
valid `.pdf` filename; this did not establish a device-side MIME defect.
**How to apply:** Model the platform MIME registry explicitly in provider tests,
and distinguish that fixture setup from production MIME behavior.

Robolectric file-provider streams can fail on close because the host JDK does
not open `java.io.FileDescriptor` to the test sandbox.
**Why:** A sharesheet test could read its attachment but failed while closing the
descriptor with `InaccessibleObjectException`; this was not an Android sharing failure.
**How to apply:** Verify provider display names and URI grants separately from
the staged file bytes, or explicitly open the required JDK module in the host
test configuration when descriptor lifecycle testing is necessary.
