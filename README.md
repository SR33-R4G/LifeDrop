# LifeDrop – Emergency Blood Donor App

Android app built with Kotlin, Jetpack Compose and local SQLite storage.

## Included in this foundation
- Donor registration: name, blood group, phone, city and availability.
- Donor search by blood group and city.
- Emergency blood request creation.
- Request feed with phone dialler contact and fulfilled status.
- Local request storage.
- Material 3 interface.
- GitHub Actions workflow to build a debug APK.

## Build
Open the repository in Android Studio with JDK 17 and Android SDK 35, sync Gradle, then run on a device or emulator.

The CI workflow builds with Gradle 8.9. After a successful run, download the `LifeDrop-debug-apk` artifact from the repository's Actions tab.

## Current limitations
This initial release stores data only on the device. It does not synchronize donors across devices. GPS-based nearby ranking, push notifications, authentication, donor verification, admin moderation and secure cloud services are not implemented yet.

Donor contact details are sensitive. Before a public launch, add explicit consent, access controls, privacy policy, abuse reporting and secure backend services. Blood compatibility and donor eligibility must be confirmed by qualified medical staff or a licensed blood bank. This app is not medical advice.
