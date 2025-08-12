# YouTubeShortsRedirector

**Android app and Chrome extension for opening YouTube Shorts videos as normal videos.**

---

## Table of Contents
1. [Overview](#overview)
2. [Features](#features)
   - [Android App](#android-app)
   - [Chrome Extension](#chrome-extension)
3. [How It Works](#how-it-works)
   - [Android](#android)
   - [Chrome Extension](#chrome-extension-1)
4. [Installation](#installation)
   - [Android App](#android-app-1)
   - [Chrome Extension](#chrome-extension-2)
5. [Code Structure](#code-structure)
6. [Disclaimer & Privacy Notice](#disclaimer--privacy-notice)
7. [Contributing](#contributing)
8. [License](#license)
9. [Credits](#credits)

---

## Overview

YouTubeShortsRedirector is a dual-solution designed to automatically redirect YouTube Shorts videos to their standard video pages for improved viewing and usability. It provides both:

- **Android Accessibility Service:** Seamlessly transforms Shorts into regular YouTube videos within the native YouTube app.
- **Chrome Extension:** Instantly redirects Shorts URLs to standard video URLs in your browser.

---

## Features

### Android App

- **Accessibility Service**: Detects when a Shorts video is playing in the YouTube app (using accessibility APIs).
- **Automatic Redirection**: Finds the Shorts player, triggers the Share > Copy Link actions, extracts the video ID, and opens the normal video page.
- **Clipboard Access Activity**: Reads the copied URL from the clipboard and initiates the redirect.
- **Settings Sync**: Enables/disables redirect functionality from the app UI.
- **Multi-language Support**: Works with multiple "Copy link" texts (e.g., English, Hebrew).
- **Timeouts and State Management**: Prevents stuck states with robust flag and event handling.

### Chrome Extension

- **URL Monitoring**: Listens for tabs navigating to `https://www.youtube.com/shorts/VIDEO_ID`.
- **Instant Redirect**: If enabled, automatically rewrites the tab URL to `https://www.youtube.com/watch?v=VIDEO_ID`.
- **User Toggle**: Extension popup allows enabling/disabling the redirect.

---

## How It Works

### Android

1. **Activate Accessibility Service**: The service monitors YouTube app UI events.
2. **Detect Shorts Player**: Checks for specific view IDs or Share button content descriptions to identify Shorts.
3. **Automation**: Programmatically clicks the Share button, then Copy Link within the share sheet.
4. **Clipboard Read**: A transparent activity reads the copied URL, extracts the video ID, and opens the standard video.
5. **User Control**: You can toggle redirection on/off in the app.

### Chrome Extension

1. **Background Listener**: Monitors tab URL changes.
2. **Shorts URL Detection**: Recognizes Shorts video URLs.
3. **Redirect Logic**: Changes the tab to the normal video format.
4. **Popup UI**: Checkbox to enable/disable redirect.

---

## Installation

### Android App

1. **Download APK:**  
   Get the latest `.apk` file from the [Releases](../../releases) page.
2. **Install:**  
   Transfer the APK to your Android device and open it to install.  
   (Enable "Install from unknown sources" in your device settings if required.)
3. **Enable Accessibility Service:**  
   Open the app and follow the on-screen instructions to activate the service.

### Chrome Extension

1. **Download Extension:**  
   Get the latest `.zip` file from the [Releases](../../releases) page and extract it.
2. **Load in Chrome:**  
   - Open `chrome://extensions/`
   - Enable **Developer Mode** (top right)
   - Click **Load unpacked** and select the extracted folder
3. **Configure:**  
   Use the extension popup to toggle redirect functionality.

---

## Code Structure

- `app/src/main/java/com/example/youtubeshortsredirector/service/RedirectShortsService.java`: Main logic for Android accessibility redirection.
- `app/src/main/java/com/example/youtubeshortsredirector/ClipboardAccessActivity.java`: Handles clipboard reading for URL extraction.
- `youtube-shorts-redirector/background.js`: Chrome extension redirect logic.
- `youtube-shorts-redirector/popup.js`: Extension popup UI for enabling/disabling.

---

## Disclaimer & Privacy Notice

YouTubeShortsRedirector App uses Android’s Accessibility Service **only** to detect when a YouTube Shorts video is playing and automatically redirect it to the standard YouTube video page for improved viewing. The app **does not collect, store, or transmit any personal or sensitive user data**.

By using this app, you acknowledge that Accessibility permissions are necessary for this functionality and that you enable them voluntarily. Please only install the APK from trusted sources and use it responsibly.

---

## Contributing

Pull requests and issues are welcome! Please follow conventional code style and describe your changes clearly.

---

## License

MIT

---

## Credits

Created by [ariel-242](https://github.com/ariel-242)
