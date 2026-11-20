# MyNote Android — early native MVP

Native Android starter for tablets and phones, using Kotlin, Jetpack Compose, Room and Android's `PdfRenderer`.

## Implemented in this source snapshot
- Responsive Compose shell with Home, PDFs and Notes tabs
- Import PDF using Android Storage Access Framework
- Copy selected PDFs into app-private local storage
- Local Room database for document metadata, page annotations and text notes
- PDF page rendering, page navigation and freehand finger/stylus ink overlay
- Persist and reload per-page ink annotations
- GitHub Actions workflow to build a debug APK in the cloud

## Not yet implemented
- Cloud sync: local-first schema is ready, but a Supabase project, authentication, RLS policies, upload queue and conflict handling still need configuration and implementation. No cloud credentials are included.
- Text highlight/underline/strikeout, eraser, undo/redo, sticky notes anchored to PDF, OCR/search, export annotated PDF, rich-text note editor, backups, tablet-specific split-pane polish and release signing.

## Build APK in GitHub Actions (tablet-friendly)
1. Create a GitHub repository and upload the contents of this folder to its `main` branch.
2. In the repository, open **Actions** and enable workflows if prompted.
3. Select **Build MyNote Android APK** and press **Run workflow** (or push to `main`).
4. Open the completed workflow run and download the `MyNote-debug-apk` artifact. Extract `app-debug.apk` and install it on the tablet. You may need to allow installation from that browser/files app.

The debug APK is for testing. A production APK should be signed with a private release keystore before distribution.

## Local build
Requires JDK 17, Android SDK platform 35 and Gradle 8.11.1.

```bash
gradle :app:assembleDebug
```

Package: `com.mynote.app`; minimum Android version: Android 8.0 (API 26).
