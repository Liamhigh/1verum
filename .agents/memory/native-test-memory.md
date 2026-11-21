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

**How to apply:** Use an opt-in local Gradle test init configuration with
`maxParallelForks = 1`, `forkEvery = 1`, and `maxHeapSize = "512m"`; a 1 GiB
Gradle heap was sufficient. Keep host-only test limits separate from app
behavior, release configuration, and the native inference library. Verify
watermark drawing and vault persistence on the JVM, then test final PDF bytes
on-device.
