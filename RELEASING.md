# Releasing Screenshot3

**One command** (build + tag + GitHub release): `./scripts/publish-release.sh`
- Derives the tag from `versionName` in `app/build.gradle.kts`, so bump that first.
- Refuses to run on a dirty tree or if the release already exists.
- `./scripts/publish-release.sh notes.md` to supply release notes; `DRAFT=1 ./scripts/publish-release.sh` for a draft.

Manual steps (equivalent):

1. Build the release APK:
   - Terminal: `./scripts/build-release.sh`
   - Android Studio: Gradle tool window -> `app` -> `Tasks` -> `distribution` -> `buildInternalRelease`
   - Install and launch on a device: `./run_release.sh`
2. Create and push the release tag:
   `git tag v2.1.2 && git push origin v2.1.2`
3. In GitHub, open Releases, draft a new release, choose `v2.1.2`, and upload the APK from `app/build/outputs/apk/release/`.
4. Publish the release and share the GitHub Release download link.
