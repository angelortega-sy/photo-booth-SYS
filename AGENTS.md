# AGENTS.md

Kiosko de fotos para Android. El scaffold ya existe: un módulo `app/` que compila con el Gradle wrapper.
Paquete: `com.photobooth.kiosk`. App visible: “Cabina de Fotos”.

## Stack y alcance (decidido)

- Kotlin + Jetpack Compose, un solo módulo `app/`.
- Versiones fijadas para AGP 8.13.2 / `compileSdk 36`: Kotlin 2.3.21, Compose BOM 2025.09.01, core-ktx 1.17.0, activity-compose 1.11.0, lifecycle 2.9.4, CameraX 1.6.2, MediaPipe 0.10.35.
- No subas core/compose/lifecycle a versiones que exijan `compileSdk 37` o AGP 9.1 sin actualizar AGP y Gradle primero.
- Pantalla kiosko en **vertical (portrait)**; salida y fondos a 1080×1920. En producción la cámara es una **webcam USB**, no una cámara integrada. Para pruebas en teléfono, CameraX usa la cámara frontal si existe (selfie) y la trasera como fallback.
- Segmentación de la persona **en el dispositivo** (preferir MediaPipe Image Segmenter; ML Kit solo si el equipo tiene Google Play Services) y composición sobre un fondo predefinido.
- Fondos **locales** incluidos como assets. **Sin IA, sin OpenRouter, sin backend ni API key en runtime.** No agregues generación de imágenes por red a la app.
- Salida: guardar la foto + compartir/QR.
- Todo debe funcionar **offline**.

## Convenciones de código

- Código, comentarios, nombres de archivos, clases, funciones y logs en **inglés**.
- Solo los textos visibles para el usuario (UI, errores en pantalla) van en **español**.

## Flujo

captura (webcam USB; CameraX en teléfono) → máscara de persona → recorte con alfa → componer sobre el fondo elegido → bitmap final → guardar + QR/share.

## Experiencia de uso

- La app abre en una **pantalla de bienvenida**; la cámara no se enciende hasta tocar “Comenzar”.
- Si el invitado no interactúa por **60 s**, la sesión termina y vuelve a bienvenida (se libera la cámara).
- En cámara, el botón “Fondos” abre un **bottom sheet con carrusel** de fondos; no dejar el selector fijo sobre el preview.
- En el resultado, botón grande “Guardar foto” + “Compartir” + “Fondos”; cambiar fondo recompone la misma captura sin volver a tomar la foto (`capturedPhoto` en `PhotoBoothViewModel`).
- Después de compartir, se cierra la sesión y vuelve a bienvenida. Guardar mantiene el resultado en pantalla.
- Paleta **azul Syscom** sobre fondo oscuro: `SyscomBlue #0B63CE`, `SyscomBlueLight #5EA8FF`, `Ink #050B14`, `InkSoft #0B1727`, `Cream #F3F8FF`.

## Modo administrador

- Se entra con **long-press sobre “SYSCOM”** en la pantalla de bienvenida; no poner un botón visible para invitados.
- PIN de **4 a 8 dígitos**, guardado como hash SHA-256 con salt en `SharedPreferences` (`admin_auth`); nunca guardar el PIN en claro.
- `AdminAuth` maneja el PIN; `AdminGate` pide crearlo la primera vez y verificarlo después.
- `AdminScreen` muestra: resumen del dispositivo, cámaras del sistema (frontal/trasera/externa, MP, resolución y nivel de hardware), dispositivos USB, prueba de cámara en vivo, fotos guardadas con borrado individual/todas, red/IP local y accesos a ajustes de red/hotspot.
- Fotos: `PhotoLibrary` consulta `MediaStore` en `Pictures/Cabina de Fotos`, usa `READ_MEDIA_IMAGES` en API 33+ y `READ_EXTERNAL_STORAGE` en API ≤32. El borrado directo puede fallar si la app ya no es dueña del archivo; mostrar error, no crashear.
- El botón de hotspot solo abre los ajustes del sistema. El hotspot programático + QR de descarga es la siguiente fase.
- Layout admin responsive: header compacto (atrás + título + cerrar), botones de red a ancho completo, fotos en **2 columnas bajo 600 dp** y 4 arriba. No volver a meter dos botones de texto largo en un `Row` angosto.

## Cámara (webcam USB)

- CameraX solo funciona si el equipo expone la webcam como cámara externa (`LENS_FACING_EXTERNAL`). En la mayoría de boxes Android no ocurre.
- Abstraer `CameraSource` con dos implementaciones (`CameraXSource`, `UvcSource`) y elegir en runtime. Para UVC usar una librería tipo AndroidUSBCamera/UVCCamera.
- Pedir permiso con `UsbManager` al iniciar y manejar la desconexión de la webcam sin crashear.
- El preview UVC se renderiza con `AndroidView` (SurfaceView/TextureView), no directamente en Compose.
- Capturar al menos 1080p; componer a 1080×1920 (vertical).

## Kiosko

- Equipo objetivo probable: Syscom/Hikvision **DS-D6055UT-D/S**, 55" 4K táctil, Android 13, Cortex-A55, 4 GB RAM, 32 GB eMMC. La ficha **no lista cámara integrada**; validar el equipo prestado antes de asumirla.
- Panel nativo 3840×2160; en vertical son 2160×3840, así que 1080×1920 es exactamente la mitad. Mantener ese tamaño por RAM.
- Validar en el equipo real: instalación de APK, cámara integrada, UVC, Play Services, device owner/LockTask y escala de la UI en 55".
- Pantalla completa inmersiva, sin barras del sistema; mantener pantalla encendida.
- Kiosko real: `LockTask` + **device owner** (`adb shell dpm set-device-owner ...`) o provisión MDM. El screen pinning solo no alcanza.
- El permiso de cámara debe concederse en provisioning; si falta, mostrar pantalla de error, no crashear.

## QR y salida

- Flujo de dos QR: (1) unirse al hotspot con `WIFI:T:WPA;S:<ssid>;P:<pass>;;`; (2) descargar la foto con `http://<ip-del-kiosko>:<puerto>/p/<token>`.
- El kiosko corre un servidor HTTP local (NanoHTTPD/Ktor) y funciona sin internet, con hotspot propio o con el Wi-Fi del lugar.
- Leer el IP real del hotspot en runtime; no hardcodear `192.168.43.1`.
- Android avisa "sin internet" y puede saltar a datos móviles: indicar en pantalla que el invitado elija "mantener conexión" y desactive el cambio automático.
- Fallback: share sheet (`ACTION_SEND` + `FileProvider`) si el invitado no puede entrar a la red.
- Servir solo la foto de la sesión con token aleatorio; no exponer toda la galería. Guardar copia local en `MediaStore`.

## Fondos (contenido, no runtime)

- La app **nunca** llama a OpenRouter. Los fondos se generan una sola vez con `tools/generate-backgrounds/` (Node 18+, `POST https://openrouter.ai/api/v1/images`).
- La key va en `tools/generate-backgrounds/.env`, que está gitignored. No debe terminar en el APK ni en el repo.
- `out/` está gitignored: se revisan las imágenes y solo las aprobadas se copian a `app/src/main/assets/backgrounds/`.
- **Pack aprobado:** 6 temas verticales generados con Grok Imagine 2.0 (`x-ai/grok-imagine-image-2.0`), 9:16 a 2K, ~$0.08/imagen, en `out/grok-pack-portrait/`: `gala`, `neon`, `playa`, `estudio`, `retro80s`, `jardin`.
- Personas de fondo: `gala`, `neon`, `playa`, `retro80s` y `jardin` incluyen invitados/siluetas desenfocadas; `estudio` va sin personas. Centro libre y sin caras reconocibles.
- Assets de la app: 1080×1920 WebP en `app/src/main/assets/backgrounds/` (6 archivos). El pack horizontal anterior (`out/grok-pack/`) ya no se usa.
- Prompts: vertical 9:16, centro libre, espacio para cuerpo completo, personas de fondo desenfocadas y sin caras reconocibles, sin texto/logos.

## Gotchas

- Muchos boxes Android no traen Google Play Services: MediaPipe es la opción segura para segmentar; ML Kit puede no estar disponible.
- Segmentación y composición fuera del hilo principal; objetivo < 1 s.
- Fondos en `assets/` o `res/drawable-nodpi` con el aspect ratio objetivo (vertical 9:16); no escalar en runtime.
- Si falla la cámara o la segmentación, no romper el flujo: fallback a la foto original.
- La máscara del modelo es 256×256. No hacer threshold duro otra vez: usar las *confidence masks* (clases 1..5) y el **guided filter** en `PhotoProcessor` para que el borde siga pelo/orejas en vez de la cuadrícula del modelo.
- Los fondos se generan **offline, fuera del repo**; solo las versiones aprobadas se copian a `assets/`. Revisar que no traigan personas, texto ni logos.
- MediaPipe 0.10.35: `ImageSegmenterResult.categoryMask()` devuelve `Optional<MPImage>`, no `MPMask`. Leer con `ByteBufferExtractor.extract(mask)` y `mask.width/height`; `ImageSegmenterResult` no implementa `close()`, la máscara sí.

## Comandos

- `.\gradlew.bat assembleDebug` — APK en `app/build/outputs/apk/debug/app-debug.apk`.
- `.\gradlew.bat installDebug` — requiere dispositivo conectado.
- `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- `adb shell dpm set-device-owner <paquete>/.<AdminReceiver>`

## Estado de build (2026-11-04)

- `assembleDebug` compila correctamente después del rediseño, el pack vertical y el modo administrador.
- APK debug: ~88.2 MB (MediaPipe + modelo 16 MB + fondos).
- Probado en un Samsung A55: flujo de bienvenida, cámara, captura, resultado, guardado, modo administrador, PIN, borrado de fotos y layout responsive. Falta validar el pack vertical/cutout con buena luz y la escala en la pantalla de 55".
- El repo se subió a `origin/main` en el commit `14c48c8` antes del modo administrador.

## Pendientes

- Probar la UI en la pantalla de 55" y ajustar escala/tamaños táctiles.
- Agregar `UvcSource` cuando llegue el kiosko (validar UVC y ausencia de Play Services).
- Validar `startLocalOnlyHotspot` en el equipo; fallback: router de viaje sin internet.
- Servidor HTTP local + QR de descarga; conectar el botón de hotspot del admin.
- Impresión queda para después.
