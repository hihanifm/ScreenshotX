# Releasing Screenshot3

1. Build the release APK:
   `./scripts/build-release.sh`
2. Create and push the release tag:
   `git tag v2.1.1 && git push origin v2.1.1`
3. In GitHub, open Releases, draft a new release, choose `v2.1.1`, and upload the APK from `app/build/outputs/apk/release/`.
4. Publish the release and share the GitHub Release download link.
