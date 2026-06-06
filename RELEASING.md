# Releasing Screenshot3

1. Build the release APK:
   - Terminal: `./scripts/build-release.sh`
   - Android Studio: Gradle tool window -> `app` -> `Tasks` -> `distribution` -> `buildInternalRelease`
   - Install and launch on a device: `./run_release.sh`
2. Create and push the release tag:
   `git tag v2.1.1 && git push origin v2.1.1`
3. In GitHub, open Releases, draft a new release, choose `v2.1.1`, and upload the APK from `app/build/outputs/apk/release/`.
4. Publish the release and share the GitHub Release download link.
