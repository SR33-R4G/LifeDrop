# LifeDrop – Android testing build

LifeDrop is an Android blood donor directory prototype built with Kotlin, Jetpack Compose and on-device SQLite.

## Current testing-phase behavior
- App opens without requiring login.
- Home screen provides public donor search by blood group and location (city, area or district).
- Donor results show Call and WhatsApp actions.
- Settings contains local Login / Sign up.
- Only a signed-in local account can add/update donor details or post a blood request.
- Blood requests can be viewed without login; signed-in users can mark them fulfilled.
- Accounts, donors and requests are stored only in the app's local SQLite database on that phone.

## Build
Use JDK 17 and Android SDK 35. GitHub Actions builds the debug APK with Gradle 8.9. Download the `LifeDrop-debug-apk` artifact from the Actions tab after a successful run.

## Important testing limitations
This is a local-only prototype. A donor entered on one phone is not visible on another phone. Creating an account on one device does not create a shared online account. Search results only include records saved on that same device. No cloud backend, GPS-based radius search, push notifications or admin moderation is configured.

Local sign-up is only a testing gate, not production-grade identity or access control. Before public release, implement a secure backend, proper authentication, donor consent and privacy controls. Blood compatibility and donor eligibility must be confirmed by qualified medical staff or a licensed blood bank; this app is not medical advice.
