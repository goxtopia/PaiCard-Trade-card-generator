# Android Card Generator

This is a native Android version of the Card Generator application. It functions as a standalone client, allowing you to generate trading cards from images using your own API Key and Model (e.g., OpenAI GPT-4o).

## Features

*   **Standalone**: Does not require the Python server to run.
*   **Custom Config**: Set your own API Endpoint, API Key, and Model Name in Settings.
*   **Native Performance**: Uses Native Android logic for image processing and networking.
*   **Visuals**: Uses Native Android Views (ConstraintLayout, GradientDrawables) for high performance and future 3D/Animation extensibility.

## How to Build

1.  Open **Android Studio**.
2.  Select **Open an existing project**.
3.  Navigate to this `Android` folder and click OK.
4.  Android Studio will sync the Gradle project.
5.  Connect an Android device or start an Emulator.
6.  Click **Run**.

## Requirements

*   Android SDK 33 (or newer)
*   JDK 1.8 or newer
*   An OpenAI-compatible API Key (e.g., OpenAI, DeepSeek, or a local LLM serving an OpenAI-compatible endpoint).

## Usage

1.  Open the App.
2.  Tap the **Settings** (Gear icon or Menu).
3.  Enter your **API URL** (default: `https://api.openai.com`), **API Key**, and **Model** (e.g., `gpt-4o-mini`).
4.  Save and go back.
5.  Tap **Pick Image** to select a photo from your gallery.
6.  Tap **Generate Card**.
7.  Wait for the AI to analyze the image and generate the card stats/flavor text.
