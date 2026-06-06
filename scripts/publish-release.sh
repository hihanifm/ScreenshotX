#!/bin/bash
# Build the signed release APK and publish it as a GitHub Release.
#
# Version comes from versionName in app/build.gradle.kts -> tag vX.Y.Z.
# Usage:
#   ./scripts/publish-release.sh                 # build, tag, release (auto notes)
#   ./scripts/publish-release.sh notes.md        # use notes.md as the release body
#   DRAFT=1 ./scripts/publish-release.sh         # create the release as a draft
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

notes_file="${1:-}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found. Install it and run 'gh auth login'." >&2
  exit 1
fi
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated. Run 'gh auth login'." >&2; exit 1; }

# Refuse to publish a tag from a dirty tree so the release matches a real commit.
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is dirty. Commit or stash changes before publishing." >&2
  exit 1
fi

version_name="$(grep -E 'versionName\s*=\s*"' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')"
if [[ -z "${version_name:-}" ]]; then
  echo "Could not read versionName from app/build.gradle.kts" >&2
  exit 1
fi
tag="v${version_name}"
echo "Publishing release ${tag}"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release ${tag} already exists. Bump versionName in app/build.gradle.kts first." >&2
  exit 1
fi

# Build the signed, shrunk APK (also prints size delta).
./scripts/build-release.sh

release_apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' | sort | tail -n 1)"
if [[ -z "${release_apk:-}" ]]; then
  echo "Release APK not found in app/build/outputs/apk/release/" >&2
  exit 1
fi

# Tag the current commit (locally + remote) if not already tagged.
if ! git rev-parse -q --verify "refs/tags/${tag}" >/dev/null; then
  git tag -a "$tag" -m "Screenshot3 ${tag}"
fi
git push origin "$tag"

notes_args=(--generate-notes)
if [[ -n "$notes_file" ]]; then
  [[ -f "$notes_file" ]] || { echo "Notes file not found: $notes_file" >&2; exit 1; }
  notes_args=(--notes-file "$notes_file")
fi

draft_args=()
[[ "${DRAFT:-0}" == "1" ]] && draft_args=(--draft)

gh release create "$tag" \
  "${release_apk}#Screenshot3 ${tag} (APK)" \
  --title "Screenshot3 ${tag}" \
  ${draft_args[@]+"${draft_args[@]}"} \
  "${notes_args[@]}"

echo "Done: $(gh release view "$tag" --json url -q .url)"
