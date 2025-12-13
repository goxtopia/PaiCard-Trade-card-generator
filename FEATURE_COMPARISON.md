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
| **Visuals & UI** | **Card Rendering** | ✅ HTML/CSS | ✅ Native Views (XML) | **Functional** | Transitioned to Native UI for better performance and future 3D capabilities. |
| | **Rarity Colors** | ✅ Yes | ✅ Yes | **Synced** | N, R, SR, SSR, UR colors match using Native Drawables. |
| | **Theme Colors** | ✅ Yes | ✅ Yes | **Synced** | (e.g., Bronze, Gold, Galaxy) logic ported to Kotlin. |
| | **Visual Effects** | ✅ Yes | ✅ Yes | **Synced** | Android now supports "breathing" animations for High Rarity cards. |
| | **Summoning Animation**| ✅ Yes | ✅ Yes | **Synced** | Basic Scale/Fade animation implemented natively. |
| | **3D Flip Interaction**| ✅ Yes | ✅ Yes | **Synced** | Android implements 3D rotation with card back. |
| **Advanced Features** | **Card Library/History** | ✅ Yes | ✅ Yes | **Synced** | Android now persists generated cards (MVP scope). |
| | **Batch Generation** | ✅ Yes | ❌ No | *Missing* | Single image generation only on Android. |
| | **Card Packs** | ✅ Yes | ❌ No | *Missing* | |
| | **God's Draw** | ✅ Yes | ✅ Yes | **Synced** | Implemented fetching from random image API. |
| | **Card Back Selection**| ✅ Yes | ⚠️ Partial | *Partial* | Android uses a default card back for flip animations. |

## Summary

The **Android Version** successfully replicates the **Core Card Generation** experience. It allows users to take a photo or pick an image, configure their own AI backend, and generate a trading card with the exact same high-quality visuals (glows, rarities, themes) as the web version.

**Update:**
*   **God's Draw** has been added, allowing users to fetch random images and generate cards instantly.
*   **Card Library** is now available, saving generated cards locally.
*   **3D Effects** including card flipping and breathing animations are implemented.
*   **Card Packs** and **Batch Processing** are still exclusive to the Web version.
