# OS Maps

An Android app for viewing UK Ordnance Survey Leisure maps, with location tracking and place search.

## Prerequisites

- JDK 17
- Android SDK (API 34 + Build Tools)
- A Bing Maps key with `productSet=mmOS` access

## Configuration

Copy your API keys into `local.properties` (never committed):

```
sdk.dir=/path/to/Android/sdk
bingMapsKey=YOUR_BING_MAPS_KEY
osMapsKey=YOUR_OS_MAPS_API_KEY   # optional — app falls back to Bing if unset
```

## Build

```bash
# Debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug

# Release APK (requires signing config — see below)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
```

## Signing a release build

Generate a keystore once:

```bash
keytool -genkey -v -keystore ~/os-maps-release.jks \
  -alias os-maps -keyalg RSA -keysize 2048 -validity 10000
```

Add to `local.properties`:

```
storeFile=/absolute/path/to/os-maps-release.jks
storePassword=yourpassword
keyAlias=os-maps
keyPassword=yourpassword
```

## Install on a phone

Enable **USB debugging** on the phone (Settings → About phone → tap Build number 7 times → Developer options → USB debugging), then:

```bash
# Debug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release
adb install app/build/outputs/apk/release/app-release.apk

# Launch immediately
adb shell am start -n rs.wllrg/.MainActivity
```

## Code quality

```bash
# Lint
./gradlew :app:lint

# Kotlin style check
./gradlew :app:ktlintCheck

# Auto-fix style
./gradlew :app:ktlintFormat
```

Both lint and ktlint run automatically on every push via GitHub Actions.
[Pre-commit](https://pre-commit.com) hooks are also configured — install with `pre-commit install`.
