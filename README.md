# Luma Gallery authenticity analysis

Luma is an on-device Android gallery. Its authenticity feature is evidence-based: it reports metadata and embedded-provenance signals, and deliberately does **not** claim that it can prove an image is real, fake, or AI-made.

## Current architecture

```
MediaStore / gallery-visible folders
        ↓
LibraryScanWorker + ContentObserver
        ↓
WorkManager (battery-not-low queue)
        ↓
Media type and screenshot classification
        ↓
EXIF/XMP/IPTC/PNG/JPEG + C2PA/JUMBF marker inspection
        ↓
Noise residual + sampled DCT/DFT/Haar + pixel/compression/region forensics
        ↓
Bundled visual model + real-camera counter-evidence
        ↓
Reliability-weighted fusion with independent-signal corroboration
        ↓
Room: media_analysis
        ↓
Gallery badges and local notification
```

## Compatibility and feature boundaries

- `core/compatibility` is the only package that reads the Android SDK level. It owns scoped-storage,
  MediaStore, trash, notification, and selected-photo feature gates.
- `core/permissions/PermissionManager` owns media and notification permission state, including
  full, image-only, video-only, selected-media, denied, and permanently-denied behavior.
- `repositories/MediaRepository` remains the only gallery data source that queries MediaStore.
- `features/aiDetection/AIImageDetector` is the stable Gallery-to-detector contract. Its current
  `WorkManagerAIImageDetector` implementation adapts the existing forensic detector, Room cache,
  and WorkManager queue without changing detection calculations or confidence scoring.

This is an additive compatibility foundation: existing activities and AI implementation classes
retain their APIs while callers migrate behind these contracts.

`AiMediaDetector` is the provider boundary. A vetted on-device model, a real C2PA validator, or a consent-gated cloud provider can implement it without changing the gallery or persistence layer. The included `LocalMetadataDetector` is intentionally local-only and explainable; it does not manufacture visual-model scores.

## Video pipeline

`ForensicVideoDetector` replaces the prior single-pass video check. It measures container metadata, creator signatures, C2PA/JUMBF markers, duration/resolution/bitrate quality, scene changes, representative frame fingerprints, duplicate-frame removal, and adjacent-frame consistency. WorkManager records stage progress for the video job.

Validated image-AI, face/deepfake, audio/lip-sync, optical-flow, and cryptographic-C2PA providers are deliberately reported as **Detector unavailable** until integrated. The app will therefore return `Unknown` for ordinary videos instead of turning compression, motion, or a filename into an AI claim.

## Privacy and accuracy

- Analysis stays on the device. No media is uploaded.
- Only Android MediaStore items and user-granted folders are read; other apps' private storage is never accessed.
- A detected C2PA/JUMBF container is only an indicator in this version, not cryptographic verification.
- SynthID/provider invisible-watermark detection is reported as unavailable unless a readable embedded marker is present; a missing marker is neutral.
- PRNU identity matching, anatomy/face semantics, OCR/typography, and physics models are reported as unavailable instead of receiving invented scores.
- A single visual model or artifact cannot produce a high-confidence AI label.
- 95–100% is reserved for cryptographically verified provenance or a verified watermark.
- Missing metadata, screenshots, social-app compression, edits, and downloads never count as AI evidence.
- “Likely AI generated” requires explicit AI-related creator metadata or provenance markers. Missing metadata results in “Unable to determine.”

## Build

Open the project in Android Studio (JDK 17 recommended) and run the `app` configuration, or use:

```powershell
.\gradlew.bat :app:assembleDebug
```

On Android 13+, grant Photos/Videos and notifications permissions. The background library scan runs daily with the battery-not-low constraint; visible media is also queued when the gallery loads.
