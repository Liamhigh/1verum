#!/bin/bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
pnpm install --frozen-lockfile
# Native Android has no shared web database. Do not run scaffold migrations
# or compile Android as part of starting/merging a web artifact.
