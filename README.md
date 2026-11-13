# LifeDrop – Android blood donor application

LifeDrop is an Android blood donor application built with Kotlin, Jetpack Compose and Supabase.

## Cloud features
- Public donor search by blood group and location; no login required to search.
- Independent email/password sign-up and login through Supabase Auth.
- Authenticated users can publish their donor profile and blood requests.
- Donor records and blood requests are stored in the Supabase cloud database and are shared across devices.
- Call and WhatsApp contact actions.
- Row Level Security (RLS) policies restrict donor-profile changes and request updates to their owners.

## Supabase setup (required once)
1. Open the Supabase project dashboard.
2. Go to **SQL Editor → New query**.
3. Copy and run the complete script in `supabase/lifedrop_schema.sql`.
4. In **Authentication → Providers → Email**, configure email confirmation as desired. If confirmation is enabled, users must confirm their email before logging in.
5. Build and install the latest APK.

The Android app uses the project's URL and publishable key. Never put a Supabase secret/service-role key or database password in the Android app or GitHub.

## Build
Use JDK 17 and Android SDK 35. GitHub Actions builds the debug APK with Gradle 8.9. Download the `LifeDrop-debug-apk` artifact from the Actions tab after a successful run.

## Important
- The SQL schema and RLS policies must be installed before cloud data operations work.
- The publishable key is designed for client apps; database security depends on the RLS policies.
- Donor contact information is personal data. Obtain donor consent before publishing details and provide a way to remove or update records before public release.
- This is a testing build, not a medical eligibility or blood-compatibility decision system. Confirm donor eligibility and blood compatibility through qualified medical staff or a licensed blood bank.
