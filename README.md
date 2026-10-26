# ScanMint

A simple, fast document scanner for Android. Scan pages with the camera, clean them up,
and save or share them as PDF. Everything stays on the phone.

## What it does

- **Scan** with Google's on-device scanner: finds page edges, auto capture, crop, rotate, filters, many pages.
- **ID card** mode (front and back in one PDF).
- **To text**: read the words on a page (English and Hindi), fix them, copy or share.
- **Import** photos from the gallery or an existing PDF.
- **Merge** several documents into one, **compress** a PDF for WhatsApp or email.
- **Share**, save to the phone (Downloads, Drive), rename, delete, search.
- Ads: banner on the home screen, a full-screen ad after every second save, and an optional
  video that turns ads off for 24 hours. Pro (no ads, password PDFs, signing) comes next.

## Test build

Every push builds a test APK. The latest one is always at
https://github.com/vedshubham58-crypto/Scanmint/releases/download/test-build/scanmint-test.apk

Test builds use Google's test ads only. The scanner needs Google Play services on the phone;
the first scan can take a few seconds while Play services downloads it.

## Building

Kotlin + Jetpack Compose, minSdk 26.

```
./gradlew testDebugUnitTest assembleDebug
```

Real AdMob IDs go in `gradle.properties` (or environment variables) before a release build.
