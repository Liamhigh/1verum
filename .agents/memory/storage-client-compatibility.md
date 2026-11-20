---
name: Replit storage client compatibility
description: Sidecar authentication compatibility with the supplied object-storage template
---

Use the supplied Replit sidecar credential configuration with Google Cloud Storage 7.x and google-auth-library 9.x unless the platform template has been updated.

**Why:** Storage 9.x with auth-library 11.x rejected the template's empty project ID and could not authenticate in this environment; the compatible major versions worked without changing sidecar authentication.

**How to apply:** When updating storage dependencies, verify a real object read/upload before changing these major versions. Do not work around authentication by requesting user credentials.
