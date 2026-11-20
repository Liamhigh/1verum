---
name: Signing diagnostic privacy
description: Avoid exposing signing credentials through diagnostic output.
---

Report allowlisted signing-error categories rather than printing raw Gradle or
keytool signing failures when the configuration comes from Secrets.

**Why:** A field intended as a key alias can accidentally contain a password.
Gradle can echo that field verbatim in signing errors, including outside quotation
marks, so merely redacting quoted strings does not reliably protect credentials.

**How to apply:** Capture signing output privately, then report only categories
such as alias not found, keystore password rejected, or key password rejected.
Inspect keystore entry metadata internally without returning aliases or
certificate identity fields to tool output. Never store secret values in memory.
