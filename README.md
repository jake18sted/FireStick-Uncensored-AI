# FireStick Uncensored AI

Native Android TV app for Fire TV Stick 4K that calls SpicyAPI (uncensored Qwen Image Edit and related models) for image editing/generation and video.

## Features
- Reference image selection (private storage)
- Free-text prompts via TV keyboard
- Create Image / Animate This / Create Another / Edit This
- Playlist with Previous/Next
- Full d-pad navigation (LEANBACK_LAUNCHER)
- minSdk 25

## Build
GitHub Actions builds the release APK. Add secrets:
- `SIGNING_KEYSTORE` (base64 of your keystore)
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`
- `SPICYAPI_KEY` (optional, can be entered in app)

Then run the workflow and download the APK artifact.

## SpicyAPI
Point the app at https://api.spicyapi.ai or the endpoint for Qwen Image Edit. Uncensored by design.
