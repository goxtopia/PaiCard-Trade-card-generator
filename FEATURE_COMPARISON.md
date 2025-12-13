# Feature Comparison: Web vs Android

This document tracks the synchronization status of features between the original Python/FastAPI Web Application and the new Standalone Android Application.

| Feature Category | Feature | Web (Python) | Android (Native) | Sync Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Core Functionality** | **Generate Card** | ✅ Yes | ✅ Yes | **Synced** | |
| | **Analyze Image (VLM)** | ✅ Yes | ✅ Yes | **Synced** | Android uses direct OpenAI API call. |
| | **Calculated Rarity** | ✅ Yes | ✅ Yes | **Synced** | Based on AI analysis. |
| | **Stats (ATK/DEF)** | ✅ Yes | ✅ Yes | **Synced** | |
| **Configuration** | **Custom API Key** | ✅ Yes (Env/Settings) | ✅ Yes (Settings UI) | **Synced** | |
| | **Custom Model Name** | ✅ Yes (Settings) | ✅ Yes (Settings UI) | **Synced** | |
| | **Custom API URL** | ✅ Yes (Settings) | ✅ Yes (Settings UI) | **Synced** | Allows local LLMs/Alternative providers. |
| | **Custom Prompt** | ✅ Yes (Granular Fields) | ✅ Yes (Unified Block) | **Synced** | Android uses a single prompt block for efficiency/mobile latency. |
| **Visuals & UI** | **Card Rendering** | ✅ HTML/CSS | ✅ WebView (HTML/CSS) | **Synced** | Reuses `style.css` for 1:1 visual match. |
| | **Rarity Colors** | ✅ Yes | ✅ Yes | **Synced** | N, R, SR, SSR, UR colors match. |
| | **Theme Colors** | ✅ Yes | ✅ Yes | **Synced** | (e.g., Bronze, Gold, Galaxy) logic ported to Kotlin. |
| | **Visual Effects** | ✅ Yes | ✅ Yes | **Synced** | (e.g., Lightning, Holographic) CSS animations work in WebView. |
| | **Summoning Animation**| ✅ Yes | ✅ Yes | **Synced** | Added `is-summoning` trigger to Android view. |
| | **3D Flip Interaction**| ✅ Yes | ❌ No | *Different* | Android displays the generated card face-up immediately. |
| **Advanced Features** | **Card Library/History** | ✅ Yes | ❌ No | *Missing* | Android does not persist generated cards (MVP scope). |
| | **Batch Generation** | ✅ Yes | ❌ No | *Missing* | Single image generation only on Android. |
| | **Card Packs** | ✅ Yes | ❌ No | *Missing* | |
| | **God's Draw** | ✅ Yes | ❌ No | *Missing* | |
| | **Card Back Selection**| ✅ Yes | ❌ No | *Missing* | Android uses default card back (hidden). |

## Summary

The **Android Version** successfully replicates the **Core Card Generation** experience. It allows users to take a photo or pick an image, configure their own AI backend, and generate a trading card with the exact same high-quality visuals (glows, rarities, themes) as the web version.

Advanced features like the **Card Database/Library**, **Packs System**, and **Batch Processing** are currently exclusive to the Server-based Web version, as they require persistent storage and background processing that were out of scope for the initial standalone Android client.
