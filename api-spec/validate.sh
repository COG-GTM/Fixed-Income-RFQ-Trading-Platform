#!/usr/bin/env bash
#
# Lightweight OpenAPI spec validation / lint step.
#
# Validates every OpenAPI document under api-spec/ using the Redocly CLI with
# the ruleset in api-spec/redocly.yaml. Intended to be run locally and in CI so
# spec correctness can be enforced independently of the application build.
#
# Usage:
#   ./api-spec/validate.sh
#
# Requires Node.js (npx). The Redocly CLI version is pinned for reproducibility.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REDOCLY_VERSION="1.25.11"

SPECS=("${SCRIPT_DIR}/openapi.yaml")

echo "Validating OpenAPI spec(s) with @redocly/cli@${REDOCLY_VERSION}..."
for spec in "${SPECS[@]}"; do
  echo ">> ${spec}"
  npx --yes "@redocly/cli@${REDOCLY_VERSION}" lint \
    --config "${SCRIPT_DIR}/redocly.yaml" \
    "${spec}"
done

echo "OpenAPI spec validation passed."
