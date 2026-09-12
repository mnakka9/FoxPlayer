# FoxPlayer

A lightweight Android audiobook player for local audio libraries. Pick a folder on your device—each folder becomes a book, and the audio files inside become chapters.

## Screenshots

### Library

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="screens/Library.png" alt="Library in dark theme" width="240" />
        <br />
        <strong>Dark theme</strong>
      </td>
      <td align="center">
        <img src="screens/Player.png" alt="Library in light theme" width="240" />
        <br />
        <strong>Light theme</strong>
      </td>
    </tr>
  </table>
</div>

### Player and bookmarks

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="screens/Bookmarks.png" alt="Player chapters in light theme" width="190" />
        <br />
        <strong>Chapters</strong>
      </td>
      <td align="center">
        <img src="screens/PlayerChaptersDark.png" alt="Player chapters in dark theme" width="190" />
        <br />
        <strong>Dark chapters</strong>
      </td>
      <td align="center">
        <img src="screens/DarkMode.png" alt="Player bookmarks in dark theme" width="190" />
        <br />
        <strong>Dark bookmarks</strong>
      </td>
      <td align="center">
        <img src="screens/PlayerBookmarksLight.png" alt="Player bookmarks in light theme" width="190" />
        <br />
        <strong>Light bookmarks</strong>
      </td>
    </tr>
  </table>
</div>

### Adding a bookmark

<div align="center">
  <table>
    <tr>
      <td align="center">
        <img src="screens/BookmarkDialogLight.png" alt="Add bookmark dialog in light theme" width="220" />
        <br />
        <strong>Light theme</strong>
      </td>
      <td align="center">
        <img src="screens/BookmarkDialogDark.png" alt="Add bookmark dialog in dark theme" width="220" />
        <br />
        <strong>Dark theme</strong>
      </td>
    </tr>
  </table>
</div>

## Features

- **Folder-based library** — Import audiobooks by selecting a folder. Subfolders are scanned for audio files.
- **Automatic metadata** — Titles, authors, and cover art are read from ID3 tags when available; folder names and image files are used as fallbacks.
- **Chapter playback** — Play, pause, seek, and jump between chapters. Skip back or forward 30 seconds.
- **Playback speed** — Choose from 0.8×, 1.0×, 1.2×, 1.5×, or 2.0×.
- **Bookmarks** — Save your place with optional notes; swipe to delete.
- **Progress tracking** — Resume where you left off. Library cards show per-book completion.
- **Background playback** — Continues playing with a media notification via Media3.
- **Light & dark themes** — Switch themes from the library screen.

## Getting started

1. Tap the folder button on the library screen.
2. Select a folder that contains your audiobook audio files (MP3, M4A, M4B, FLAC, OGG, WAV, and more).
3. Open a book from the grid to start listening.

Long-press a book in the library to remove it from FoxPlayer (files on disk are not deleted).

## Building

Requires Android SDK, JDK 17, and the Android Gradle Plugin (see `build.gradle.kts`).

```bash
# Debug APK
./gradlew assembleDebug

# Release APKs (per-ABI splits; requires signing config)
./gradlew assembleRelease
```

Release builds expect a `keystore.properties` file at the project root. Copy `keystore.properties.example` and fill in your signing details.

Release APKs are written to `app/build/outputs/apk/release/`.

## Tech stack

- Kotlin · Jetpack Compose · Material 3
- Room · Navigation Compose
- Media3 (ExoPlayer) · Coil

## License

AGPL 3.0 License — see [LICENSE](LICENSE).
