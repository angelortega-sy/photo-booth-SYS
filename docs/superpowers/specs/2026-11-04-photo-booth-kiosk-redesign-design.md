# Photo Booth Kiosk Redesign

**Status:** Draft for user review  
**Date:** 2026-11-04

## Purpose

Redesign Cabina de Fotos as a polished, welcoming, portrait-first event photo experience for a large touch kiosk. Preserve the existing Syscom identity and reliable offline capture pipeline while making the guest flow clearer, more tactile, and more memorable. Reorganize the administrator experience so it is easier to scan and no longer presents every section as a card.

This design includes a three-shot session: the guest manually starts three timed captures, receives three individually framed photos, and also receives a three-photo strip. It does not include hotspot/QR delivery or real-time background replacement.

## Goals

- Give guests an obvious, low-friction flow from welcome through photo delivery.
- Design specifically for an immersive portrait kiosk, large touch targets, and viewing at a distance; do not simply scale a phone camera UI.
- Retain the dark Ink / InkSoft surfaces, Cream text, SyscomBlue primary actions, and SyscomBlueLight accents.
- Let guests choose among the existing six offline booth backgrounds.
- Capture three photos one at a time, each after its own countdown.
- Produce three individual outputs with an event frame and one combined photo strip.
- Make it straightforward to review, save, and share all four outputs.
- Retain existing administrator functions and security while improving information hierarchy and navigation.
- Keep camera, segmentation, composition, and storage behavior robust on constrained Android hardware.

## Non-goals

- Building a local HTTP server, QR download flow, hotspot automation, or Wi-Fi onboarding.
- Adding a new transfer method. The design uses Android's existing native share sheet for the four outputs.
- Replacing the six current booth background assets or adding network image generation to the app.
- Segmenting/compositing every preview frame in real time. The camera preview remains the regular camera preview; the selected background is applied to still photos.
- Adding UVC camera support, printing, or changing kiosk provisioning/device-owner behavior.
- Replacing the existing camera/segmentation model or upgrading pinned platform dependencies.

## Design principles

1. **Photographic, not dashboard-like:** use the photo preview and finished work as the visual focus. Use cards sparingly; group information with typography, spacing, dividers, and restrained surfaces.
2. **Kiosk-first interaction:** use large, unambiguous controls, short Spanish copy, clear progress, and layouts responsive to available width and height. Validate physical sizing on the target 55-inch display.
3. **One obvious next action:** give each guest screen a dominant action and keep secondary actions visually subordinate.
4. **Offline by default:** all visual assets are bundled; capture, composition, saving, and sharing do not require a network connection.
5. **Graceful degradation:** camera or segmentation failures must show actionable feedback and preserve the photo using the existing original-image fallback.

## Guest experience

### 1. Welcome

- Keep the app immersive and portrait-oriented. The camera must remain off until the guest taps **Comenzar**.
- Present SYSCOM branding, a short invitation, and one prominent **Comenzar** action.
- Use a cinematic, restrained photographic treatment rather than the current isolated camera icon as the main hero. A dedicated decorative welcome asset may be added; if used, it must be bundled locally and must not replace or be confused with the six selectable booth backgrounds.
- Preserve administrator entry via long-press on SYSCOM; do not add a visible guest-facing admin button.

### 2. Camera and three-shot sequence

- Keep the live camera preview as the dominant surface with minimal overlays.
- Provide access to the background selector through a bottom sheet with a horizontal carousel, not a permanently open selector covering the preview.
- Make the selected background identifiable in the capture UI (name and/or compact thumbnail). Do not claim it is composited into the live preview.
- The guest chooses one background before the first capture; lock background selection for the three-shot sequence so the set remains consistent. Re-enable it on the result screen, where changing it re-composes all outputs.
- Show prominent progress such as **Foto 1 de 3**, **Foto 2 de 3**, and **Foto 3 de 3**.
- Each of the three captures is guest-triggered. Each trigger runs the existing visible three-second countdown and capture feedback before taking one still. The next still requires another guest action; the app must not silently capture all three back-to-back.
- Retain the camera error and retry state, processing feedback, and camera-permission explanation.
- After the third capture, show a processing state, compose the three outputs and the strip, then show the result screen.

### 3. Result and delivery

- Make the combined strip the primary preview. Show the three individual framed photos as selectable thumbnails/previews below or adjacent to it, adapting to the portrait viewport.
- Primary action: **Guardar todo**. It saves all three framed individual photos and the strip (four files total) to `Pictures/Cabina de Fotos`.
- Secondary action: **Compartir**. It shares all four files through Android's native multiple-item share sheet. If the share activity launches successfully, end the session and return to welcome, matching current behavior.
- **Cambiar fondo** re-composes all three individual photos and the strip from the same three original captures; it never asks the guest to pose again. All four outputs must use the same currently selected background.
- **Otra sesión** abandons the result and returns to welcome. Saving does not end the session.
- When segmentation fails for any capture, use the existing original-image fallback for that image, continue producing the remaining outputs, and clearly identify the fallback in the result state rather than crashing or losing the session.

### 4. Event frame and combined strip

- Frame each individual image with a clean white margin and a restrained Syscom-blue accent.
- Add **EXPO SYSCOM 2027** in the individual frame footer, with no additional logos or generated text baked into a photographic asset.
- Combine the three finished captures in a classic vertical photo-booth strip, with three stacked panels and a matching event footer. Fit each complete individual image into its panel so the full-body composition is not unintentionally cropped; use a consistent neutral fill where aspect ratios differ.
- Treat the frame and strip as deterministic bitmap composition, separate from person segmentation. Keep dimensions bounded and consistent with the existing 1080×1920 individual output target; the strip may have its own documented dimensions/aspect ratio so it does not distort individual photos.

## Administrator experience

- Keep the existing PIN creation, verification, and change flows. PIN storage remains salted SHA-256 in `SharedPreferences`; never store cleartext.
- Replace the single long, card-heavy list with four clearly navigable sections: **Resumen**, **Cámara**, **Fotos**, and **Sistema/Red**.
- Use a compact header with back/close and section title. On narrow screens, use compact scrollable section navigation; on wider layouts, use persistent section navigation with the selected section's content. Avoid cramped rows of long text actions.
- **Resumen** presents key device/app status and a concise camera/network status summary.
- **Cámara** retains system camera details, external/USB device discovery, and the live camera test, with an obvious way to stop the test.
- **Fotos** retains the MediaStore gallery, individual deletion, delete-all confirmation, permission handling, and responsive grid (two columns below 600 dp, four at/above 600 dp).
- **Sistema/Red** retains network type/IP information and buttons that only open Android network/hotspot settings.
- Preserve Spanish user-facing labels, destructive confirmations, and visible error feedback. Do not remove current diagnostic capabilities as part of the visual redesign.

## Session lifecycle and kiosk constraints

- Keep the current immersive full-screen behavior and keep the display awake.
- Keep the camera off at welcome and release it when leaving the guest session.
- Preserve the 60-second inactivity timeout; while processing, do not prematurely expire a session.
- Keep the existing behavior that sharing ends the guest session and saving leaves the result visible.
- Make layout adaptive rather than assuming one fixed density or screen size. Validate touch-target physical size, text scale, cutout handling, and preview composition on the actual 55-inch portrait kiosk.

## Technical shape

- Keep the Kotlin/Compose, single-module application and current pinned dependency versions.
- Keep CameraX/`CameraPreview` as the existing phone/test-device capture path; do not claim this solves external UVC camera support.
- Extend the session state in `PhotoBoothViewModel` to represent three ordered captures, progress, selected background, processing/result state, and the four output artifacts.
- Reuse `PhotoProcessor` for one still at a time. Preserve each original capture for later background changes. Process and release full-resolution bitmaps sequentially; use private temporary files or equivalent bounded storage for session inputs/outputs instead of retaining all full-size source, mask, and composite bitmaps in memory simultaneously.
- Add a focused frame/strip composition unit separate from person segmentation. It accepts the three framed photo outputs and returns the strip; it performs no network work.
- Extend the save/share path to handle a set of four images, including cleanup of temporary session data on home, timeout, share completion, or failure.
- Continue to use the existing MediaStore collection and platform-appropriate permissions for saving/admin gallery access. Handle partial save/share failures without claiming that all four files succeeded.
- Avoid unnecessary animations, continuous image analysis, new large dependencies, and runtime image generation.

## Error and edge-case behavior

- Missing camera permission: keep the existing explanatory permission screen and settings route; never crash.
- Camera initialization/capture error: show retry, do not advance the shot count until a capture succeeds.
- Segmentation failure for an individual capture: use that capture's original-image fallback and continue the session.
- Background change during result recomposition: show a processing state, prevent overlapping recompositions, and retain the prior valid result until replacements are ready.
- Partial save/share failure: report the outcome accurately and keep the result/session available unless a share activity successfully launches.
- Timeout, return home, or session completion: invalidate late camera/processing callbacks and release camera and temporary session resources.
- Gallery permission denied or MediaStore deletion failure in admin: preserve existing non-crashing feedback behavior.

## Acceptance criteria

1. App opens in welcome without starting the camera; the first guest action starts the session.
2. Welcome and all guest flows render in immersive portrait, retain Syscom colors, and expose large, legible touch controls.
3. Guest can select one of the existing six backgrounds in the bottom sheet; the preview does not run continuous segmentation.
4. Guest manually triggers exactly three successful captures, each with its own countdown and accurate progress. A failed capture does not increment progress.
5. Completing the sequence produces three individual framed images plus one vertical three-panel strip, with the event footer on all required frame outputs.
6. Changing background recomposes all outputs from the same captures without reopening the camera or taking new photos.
7. **Guardar todo** attempts to save four files to the existing Cabina de Fotos MediaStore location and reports partial failures accurately.
8. **Compartir** offers the four files through Android's native share sheet; successful launch returns to welcome and releases the session.
9. Admin PIN security and all current device, camera, USB, gallery, deletion, and network diagnostics remain available through the new sections.
10. The inactivity timeout, permission/error fallbacks, and camera release behavior remain intact.
11. No QR/hotspot flow, network image generation, live segmentation, UVC support, printing, or dependency upgrade is introduced by this redesign.
12. Build and available tests pass; behavior and visual scale are manually validated on a phone test device and, when available, the real portrait kiosk.

## Items to validate during implementation

- Final dp sizing and navigation treatment on the actual kiosk density and portrait panel.
- Final individual frame/strip dimensions and text sizing, while keeping the 1080×1920 individual output and avoiding full-body crops.
- Peak memory and composition time on the target Android hardware, including changing the background after all three captures.
- Native Android multiple-image share compatibility with the target Android version and common share targets.
- Whether an optional decorative welcome image improves the composition; if approved, bundle it as a UI-only asset and keep it separate from the booth background catalog.
