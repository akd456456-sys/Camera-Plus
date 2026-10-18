# Camera + (Camera Plus) — GitHub Actions starter

Includes CameraX live preview/capture, GPS coordinates/date/address watermark, QR code encoding a Google Maps URL, stamp toggles, stamp color choices, natural/monochrome/sepia post-capture effects, exposure compensation slider, flash toggle, front/back camera switch, Gallery launcher, and small `App by AKT` stamp footer.

**Important limitations / testing notes**
- A printed QR code is the practical way to make a location in a flattened photo actionable. Scan it to open Google Maps. Text on a JPG is not a tappable hyperlink.
- Reverse geocoding (human-readable address) may require connectivity and may be unavailable offline; GPS coordinates are shown if a location fix exists.
- Brightness control is implemented as camera exposure compensation, not an arbitrary screen brightness slider.
- This is an early starter build; test photo orientation/quality, permission behavior, QR readability, and device-specific camera behavior on the intended Android phone.
- The Gallery control opens the phone's gallery app; an in-app gallery and direct "Open in Maps" photo-detail action can be added in a later iteration.

## Build via GitHub Actions
1. Upload/extract this project into a GitHub repository (keep folder contents at repository root).
2. Commit to `main`, or go to **Actions → Build Camera Plus APK → Run workflow**.
3. Open the completed workflow run, then download artifact **Camera-Plus-debug-apk**.
4. Extract `app-debug.apk` and install on your Android phone. For release distribution, create a signed release APK/AAB with your own keystore.
