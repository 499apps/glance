# Contributing to Glance

Open an issue or pull request at [499apps/glance](https://github.com/499apps/glance). Keep changes focused and describe the user-visible behavior and relevant validation.

Use JDK 17 and Android SDK 35. On Windows, `./scripts/bootstrap.ps1` prepares tools and `./scripts/build.ps1` runs checks and builds the app. On other systems, configure `ANDROID_HOME` or `local.properties`, then run `./gradlew testDebugUnitTest lintDebug assembleDebug`.

For recognition or language changes, test a real phrase in each affected writing system. For overlay changes, check selection, cancelling, moving, collapsing, settings, and typing in the underlying app. Use the debug-only chat fixture rather than personal conversations when saving screenshots. Describe which phones or emulator versions were checked.

Never commit signing keys, passwords, local configuration, downloaded models, screenshots of personal data, or device logs. The official release signing key stays private. Your locally generated release key cannot update an official APK; debug builds also use a separate key.

Source languages must support both on-device translation and the OCR writing systems packaged in the app. Keep the Google attribution visible and preserve third-party notices. Contributions are provided under the project's MIT license for original code.
