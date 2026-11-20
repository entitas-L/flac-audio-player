# 🎵 FLAC Audio Player for Android

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

A modern, lightweight, high-fidelity local FLAC audio player built natively for Android using **Kotlin**, **Jetpack Compose**, and **Jetpack Media3 (ExoPlayer)** — featuring an offline Japanese **Romaji & Furigana** lyrics engine, synced LRC playback, hardware 5-band Equalizer v2, and full FLAC library browsing.

---

## Features

- **High-Resolution FLAC Playback:** Powered by Jetpack Media3 (ExoPlayer) with live audio spec readout (bit depth, sample rate, channels, bitrate) and MediaSession notification artwork.
- **Pure-Kotlin Binary FLAC Reader:** Directly reads `STREAMINFO`, `VORBIS_COMMENT` (including embedded lyrics), and `METADATA_BLOCK_PICTURE` cover art from `ContentResolver` streams (with ID3v2 header skip).
- **Device FLAC Library & Queue:** Automatic `MediaStore` scanning, album grouping, search & filter chips (`Semua`, `Per Album`, `Ada Lirik`), lazy cached cover thumbnails, SAF file picker (`OpenDocument`), `ACTION_VIEW` `.flac` support, Shuffle, Repeat (`OFF` / `ALL` / `ONE`), and a persistent mini-player bar.
- **Offline Japanese Romaji & Furigana Engine:** Bundled compressed IPADic + Kanwa dictionary (`assets/jp_dict.tsv.gz`) converting Kanji/Hiragana/Katakana to Hepburn Romaji and per-Kanji Furigana ruby annotations offline, plus a standalone **Quick Romaji Converter** dialog.
- **Multi-Source Synced Lyrics & Translation:** Supports embedded FLAC lyrics, sidecar `.lrc` files, automatic online **LRCLIB** lookup, manual LRC/plain lyrics editor, tap-to-seek auto-scrolling lyrics, Fokus Romaji mode, and optional ID/EN line translation with local caching.
- **Hardware 5-Band Equalizer v2:** `ModalBottomSheet` AudioFX DSP equalizer with real-time Bezier curve Canvas visualizer, lag-free draggable sliders, and presets (`Flat`, `Bass Boost`, `Vocal / J-Pop`, `Rock / Metal`, `Pop`, `Hi-Res Air`).
- **Dynamic Material 3 Dark Theme:** Adaptive glow & accent color derived automatically from album artwork via Android Palette API.

---

## Tech Stack & Dependencies

- **Language:** [Kotlin](https://kotlinlang.org/) (`2.3.21`)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material Design 3)
- **Audio Engine:** [Android Jetpack Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Artwork & Dynamic Color:** Pure-Kotlin FLAC Picture Decoder + [Android Palette API](https://developer.android.com/training/material/palette-colors)
- **Equalizer Engine:** Android `AudioFx` Equalizer API

---

## Getting Started

### Prerequisites
- Android Studio / AndroidIDE / Code On The Go (or GitHub Actions Cloud Build)
- JDK 17 or higher
- Android SDK 26 (Android 8.0) or higher (`compileSdk` / `targetSdk` 34)

### Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/entitas-L/flac-audio-player.git
   cd flac-audio-player
   ```

2. **Build Debug APK via Gradle:**
   ```bash
   ./gradlew assembleDebug
   ```

3. The generated APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Cloud Build (GitHub Actions)

Every push to `main` (or manual run from the **Actions** tab) runs **Android Cloud Build** on GitHub Actions and publishes `flac-audio-player-debug-apk` and `android-lint-report` as downloadable workflow artifacts.

---

## Download & Installation

You can download APKs from [GitHub Actions](../../actions) artifacts or [GitHub Releases](../../releases):

1. Open the latest successful run under **Actions** (or **Releases**) in this repository.
2. Download the APK artifact (`app-debug.apk` / release APK).
3. Install the APK on your Android device (ensure "Install from Unknown Sources" is enabled).

---

## Contributing

Contributions, bug reports, and feature requests are welcome! Feel free to check the [Issues](../../issues) page.

---

> [!WARNING]
> **Work in Progress (WIP)**
> This application is currently under active development. You may encounter bugs, incomplete features, or performance issues. Feel free to test it out and report any bugs via GitHub Issues!

---
## License

This project is licensed under the [MIT License](LICENSE).
