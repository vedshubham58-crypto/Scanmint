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
- **Pro** (Google Play subscription, product `scanmint_pro`, base plans `monthly` and `yearly`):
  no ads, password-locked PDFs (AES-128, opens in every PDF reader), and signing documents.
- Ads in the free version: banner on the home screen, a full-screen ad before Save and Share
  (at most one a minute), and an optional video that turns ads off for 24 hours.
  Google's consent form (UMP) shows in the UK/EEA; "Ad choices" sits on the Pro page.

## Test build

Every push builds a test APK. The latest one is always at
https://github.com/vedshubham58-crypto/Scanmint/releases/download/test-build/scanmint-test.apk

Test builds use Google's test ads only. Play billing only works in the store build, so the test
build has a "Test build: turn Pro on" switch on the Pro page to try the Pro features. The scanner needs Google Play services on the phone;
the first scan can take a few seconds while Play services downloads it.

## Building

Kotlin + Jetpack Compose, minSdk 26.

```
./gradlew testDebugUnitTest assembleDebug
```

## Store build

The **Store build (Google Play)** workflow (Actions tab → Run workflow) makes the signed `.aab`
for Play Console. One-time setup, in this order:

1. Settings → Secrets and variables → Actions → **Secrets**: add `SIGNING_PASSWORD`
   (at least 16 characters; write it down somewhere safe, it can't be recovered).
2. Actions → **Make upload key (run once)** → Run workflow. This saves a locked key in `signing/`.
3. Settings → Secrets and variables → Actions → **Variables**: add your real AdMob IDs as
   `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID`.
4. Actions → **Store build (Google Play)** → Run workflow. The file appears at
   https://github.com/vedshubham58-crypto/Scanmint/releases/download/store-build/scanmint-release.aab

Turn on **Play App Signing** when Play Console asks (it's the default). The privacy policy for the
store listing is [PRIVACY.md](PRIVACY.md).
