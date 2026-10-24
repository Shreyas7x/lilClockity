# LilClockity

A cute table clock Android app for Android 7 tablets. The app displays a minimalist clock with a playful cat mascot. Built with pure Android SDK (no external libraries) and fully compatible with API 24+.

## Build
```bash
./gradlew clean assembleRelease
```
The generated APK is located at `app/build/outputs/apk/release/app-release.apk`.

## Publishing
- **GitHub**: This repository is set up with a GitHub Actions workflow that builds the APK on every push to `main`/`master` and creates a GitHub Release automatically.
- **F‑Droid**: The project includes a `metadata.yml` file and a deterministic signing key (`fdroid-release.keystore`). Follow the F‑Droid submit guide to add the app to the store.

## License
MIT – see the `LICENSE` file.
