# AGENTS.md

Kiosko de fotos para Android. El scaffold ya existe: un módulo `app/` que compila con el Gradle wrapper.
Paquete: `com.photobooth.kiosk`. App visible: “Cabina de Fotos”.

## Stack y alcance (decidido)

- Kotlin + Jetpack Compose, un solo módulo `app/`.
- Versiones fijadas para AGP 8.13.2 / `compileSdk 36`: Kotlin 2.3.21, Compose BOM 2025.09.01, core-ktx 1.17.0, activity-compose 1.11.0, lifecycle 2.9.4, CameraX 1.6.2, MediaPipe 0.10.35.
- No subas core/compose/lifecycle a versiones que exijan `compileSdk 37` o AGP 9.1 sin actualizar AGP y Gradle primero.
- Pantalla kiosko en **vertical (portrait)**; salida y fondos a 1080×1920. En producción la cámara es una **webcam USB**, no una cámara integrada. Para pruebas en teléfono, CameraX usa la cámara frontal si existe (selfie) y la trasera como fallback.
- Segmentación de la persona **en el dispositivo** (preferir MediaPipe Image Segmenter; ML Kit solo si el equipo tiene Google Play Services) y composición sobre un fondo predefinido.
- Fondos locales como fallback/offline. El flujo nuevo usa **IA vía backend propio**; OpenRouter se llama solo desde el servidor y la app nunca lleva la API key.
- Salida: QR para iPhone/Android + Quick Share Android. El flujo nuevo quita “Guardar”.
- Offline: fallback local (fondos + QR local); la generación IA requiere red.

## Convenciones de código

- Código, comentarios, nombres de archivos, clases, funciones y logs en **inglés**.
- Solo los textos visibles para el usuario (UI, errores en pantalla) van en **español**.

## Flujo

captura (webcam USB; CameraX en teléfono) → 3 fotos → estilo global + fondo por foto → backend IA (OpenRouter) → tira local con marca/hashtag/fecha → QR cross-platform + Quick Share Android.

## Experiencia de uso

- La app abre en una **pantalla de bienvenida**; la cámara no se enciende hasta tocar “Comenzar”.
- Si el invitado no interactúa por **60 s**, la sesión termina y vuelve a bienvenida (se libera la cámara).
- En cámara, el botón “Fondos” abre un **bottom sheet con carrusel** de fondos; no dejar el selector fijo sobre el preview.
- El resultado incluye un wizard de personalización; el CTA grande es **“Descargar · 4 fotos”** (3 fotos + tira) y “Guardar” queda como acción secundaria.
- Después de descargar/compartir, se cierra la sesión y vuelve a bienvenida. Guardar mantiene el resultado en pantalla.
- Paleta **azul Syscom** sobre fondo oscuro: `SyscomBlue #0B63CE`, `SyscomBlueLight #5EA8FF`, `Ink #050B14`, `InkSoft #0B1727`, `Cream #F3F8FF`.

## Modo administrador

- Se entra con **long-press sobre “SYSCOM”** en la pantalla de bienvenida; no poner un botón visible para invitados.
- PIN de **4 a 8 dígitos**, guardado como hash SHA-256 con salt en `SharedPreferences` (`admin_auth`); nunca guardar el PIN en claro.
- `AdminAuth` maneja el PIN; `AdminGate` pide crearlo la primera vez y verificarlo después.
- `AdminScreen` muestra: resumen del dispositivo, cámaras del sistema (frontal/trasera/externa, MP, resolución y nivel de hardware), dispositivos USB, prueba de cámara en vivo, fotos guardadas con borrado individual/todas, red/IP local y accesos a ajustes de red/hotspot.
- Fotos: `PhotoLibrary` consulta `MediaStore` en `Pictures/Cabina de Fotos`, usa `READ_MEDIA_IMAGES` en API 33+ y `READ_EXTERNAL_STORAGE` en API ≤32. El borrado directo puede fallar si la app ya no es dueña del archivo; mostrar error, no crashear.
- El botón de hotspot solo abre los ajustes del sistema. El hotspot programático + QR de descarga es la siguiente fase.
- Layout admin responsive: header compacto (atrás + título + cerrar), botones de red a ancho completo, fotos en **2 columnas bajo 600 dp** y 4 arriba. No volver a meter dos botones de texto largo en un `Row` angosto.
- La lista de fondos del admin se lee de `state.adminBackgrounds` (estado observable), no del getter `repository.allOptions`; así importar/borrar/renombrar refresca la lista sin salir de la pestaña.

## Fondos y etiquetas del evento

- **Admin → Evento** permite importar fondos desde el almacenamiento con SAF (`OpenMultipleDocuments("image/*")`), ocultar/mostrar fondos integrados y renombrar/eliminar los personalizados.
- Los fondos importados se copian a `filesDir/backgrounds/custom_<timestamp>.webp`, normalizados a **1080×1920**. No van a `assets/` ni al repo; se pierden si se borran los datos de la app.
- El catálogo se persiste en `SharedPreferences("background_catalog")`; los invitados solo ven los fondos habilitados + “Cámara real”.
- `EventBranding` se persiste en `SharedPreferences("event_branding")`: marca, tagline, título/subtítulo de bienvenida, headline/subheadline/tag del badge. Admin → Evento los edita.
- La bienvenida y `PhotoFrameComposer` leen `eventBranding`; `EventBadgePreset.CUSTOM` conserva los textos personalizados. **No volver a hardcodear “EXPO SYSCOM 2027”** en la UI.
- Al cambiar de fondo en el resultado, `requestPreviewUpdate` recompone con el catálogo actual; si el fondo elegido se oculta o borra, el ViewModel cae al primer fondo visible.

## IA y servidor (MVP aprobado)

- **La API key de OpenRouter vive solo en el backend** (`.env`/secret manager). No en el APK, ni “encriptada”; cualquier llave embebida se puede extraer.
- Hosting: servidor de la empresa si tiene HTTPS público; **Supabase** como MVP gestionado (Postgres + Storage + Edge Functions); Cloudflare Workers + R2 para escala; Vercel solo no sirve para jobs largos de imágenes.
- Modelo objetivo: `openai/gpt-image-2.6-sunburst` vía `POST https://openrouter.ai/api/v1/images`, `aspect_ratio=9:16`, `quality=medium`, `output_format=png`, `background=opaque`, `n=1`.
- **Dos referencias validadas** (persona + fondo exacto) con `input_references: [{type:"image_url", image_url:{url:"data:image/jpeg;base64,..."}}]`. Prueba real: 25.9 s, **$0.047035/foto**, fondo Gala preservado y persona transformada a Ghibli. Usar dos referencias también para fondos integrados; una sola si no hay fondo.
- Flujo backend: `POST /v1/sessions` → subir 3 originales → `POST /generate {styleId, modelId, eventId, photos:[{index, backgroundId, backgroundMode, backgroundPrompt?, backgroundAssetId?}]}` → poll job → URLs de las 3 imágenes + `usage.cost`. La app arma la tira localmente.
- El fondo se elige **antes** de generar; no hay “cambiar fondo” después porque cada llamada cuesta. La tira lleva marca, título, hashtag y fecha del evento.
- Pendiente: URL/token del backend del compañero; el backend provisional de pruebas vive fuera del repo y usa la key de `tools/generate-backgrounds/.env`.
- La app envía `stylePrompt` además de `styleId`; el backend no debe depender de prompts hardcodeados.
- `app/src/debug/AndroidManifest.xml` permite HTTP en debug para el backend provisional local. **Producción debe usar HTTPS** y no habilitar cleartext.
- MVP app-side: estilos globales, tarjeta “Generar con IA”, progreso/costo, hashtag editable, sin “Guardar”; el fondo se elige antes y se bloquea durante/después de generar.

## Descarga / Quick Share

- `LocalDeliveryServer.start()` acepta **1 a 4 archivos** (no solo 4): el flujo IA de 1 foto sirve una sola imagen; el collage sirve 3 + tira. La página y el ZIP adaptan textos y nombres según la cantidad.

- El CTA “Descargar · 4 fotos” intenta abrir **Quick Share directo** con el componente Samsung `com.samsung.android.app.sharelive/.presentation.main.MainActivity`; si falla, prueba el componente de Google Nearby Sharing y al final cae al share sheet del sistema.
- No hay API pública para forzar Quick Share: el componente explícito es *best-effort*. Si no existe en el kiosko, el fallback debe seguir funcionando.
- El intent usa `ACTION_SEND_MULTIPLE`, tipo `image/*`, `EXTRA_STREAM` con las 4 URIs de `FileProvider`, `ClipData` y `FLAG_GRANT_READ_URI_PERMISSION`. No volver a `image/jpeg` ni a URIs `file://`.
- “Guardar” guarda los 4 JPEG en `Pictures/Cabina de Fotos` (MediaStore); no vuelve a bienvenida. Descargar/compartir sí vuelve a bienvenida.
- En el kiosko sin Quick Share, validar que el share sheet muestre opciones útiles o agregar el servidor QR local como fallback.
- **Quick Share no existe en iPhone** y Bluetooth no sirve para mandar fotos a iOS. No usar Bluetooth como flujo principal. La solución cross-platform es un **servidor HTTP local + QR** (ver “QR y salida”), pendiente para el final.
- Mantener Quick Share como opción rápida para Android y ofrecer QR como opción “iPhone / otros”.
- El CTA “Descargar · 4 fotos” abre un menú con **Quick Share** y **Código QR**. El QR levanta `LocalDeliveryServer`, un servidor HTTP sin dependencias que sirve solo la sesión actual con token aleatorio: página móvil, 4 JPEG y ZIP.
- `LocalDeliveryServer` usa el IP local real (prefiere wlan/ap/eth), puerto efímero y se detiene al cerrar el QR o terminar la sesión. No expone galería ni rutas arbitrarias.
- QR generado con ZXing (`com.google.zxing:core`); requiere `INTERNET` en el manifiesto. La página funciona en Safari/Chrome sin app.
- Probado desde la red local: página 200, imagen 200, ZIP 200; el servidor se apaga al cerrar el diálogo. Falta probar con un iPhone real y con hotspot dedicado.

## Git

- Trabajo activo en la rama **`angel-ortega`**; otro compañero trabaja en `main`. No commitear directo a `main`.
- `origin/main` ya incluye el rediseño de 3 fotos/tira y el wizard (PR de `dittrich`). Hacer `git pull origin main` sobre la rama antes de abrir PR.

## Cámara (webcam USB)

- CameraX solo funciona si el equipo expone la webcam como cámara externa (`LENS_FACING_EXTERNAL`). En la mayoría de boxes Android no ocurre.
- Abstraer `CameraSource` con dos implementaciones (`CameraXSource`, `UvcSource`) y elegir en runtime. Para UVC usar una librería tipo AndroidUSBCamera/UVCCamera.
- Pedir permiso con `UsbManager` al iniciar y manejar la desconexión de la webcam sin crashear.
- El preview UVC se renderiza con `AndroidView` (SurfaceView/TextureView), no directamente en Compose.
- Capturar al menos 1080p; componer a 1080×1920 (vertical).

## Kiosko

- Dos candidatos Syscom/Hikvision de 55" 4K, Android 13, Cortex-A55, 4 GB RAM, 32 GB eMMC; **ninguna ficha lista cámara integrada**:
  - **239971 / DS-D6055UN-D/S**: **no táctil**. Si es el equipo, hace falta mouse/touch USB para operar la app, además de webcam USB.
  - **240573 / DS-D6055UT-D/S**: táctil 10 puntos.
- Validar el equipo prestado antes de asumir cámara o touch.
- Panel nativo 3840×2160; en vertical son 2160×3840, así que 1080×1920 es exactamente la mitad. Mantener ese tamaño por RAM.
- Validar en el equipo real: instalación de APK, cámara integrada, UVC, Play Services, device owner/LockTask y escala de la UI en 55".
- Pantalla completa inmersiva, sin barras del sistema; mantener pantalla encendida.
- Kiosko real: `LockTask` + **device owner** (`adb shell dpm set-device-owner ...`) o provisión MDM. El screen pinning solo no alcanza.
- El permiso de cámara debe concederse en provisioning; si falta, mostrar pantalla de error, no crashear.

## QR y salida

- Flujo de dos QR: (1) unirse al hotspot con `WIFI:T:WPA;S:<ssid>;P:<pass>;;`; (2) descargar la foto con `http://<ip-del-kiosko>:<puerto>/p/<token>`. Es la ruta recomendada para **iPhone**, sin app ni internet; implementar al final.
- El kiosko corre un servidor HTTP local (NanoHTTPD/Ktor) y funciona sin internet, con hotspot propio o con el Wi-Fi del lugar.
- Leer el IP real del hotspot en runtime; no hardcodear `192.168.43.1`.
- Android avisa "sin internet" y puede saltar a datos móviles: indicar en pantalla que el invitado elija "mantener conexión" y desactive el cambio automático.
- Fallback: share sheet (`ACTION_SEND` + `FileProvider`) si el invitado no puede entrar a la red.
- Servir solo la foto de la sesión con token aleatorio; no exponer toda la galería. Guardar copia local en `MediaStore`.

## Fondos (contenido, no runtime)

- La app **nunca** llama a OpenRouter directamente; el backend sí. Los fondos del pack se generan una sola vez con `tools/generate-backgrounds/` (Node 18+, `POST https://openrouter.ai/api/v1/images`).
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

- `assembleDebug` compila correctamente con el flujo de 3 fotos/tira, el wizard, el modo administrador, eventos personalizables y Quick Share.
- APK debug: ~96.9 MB (MediaPipe + modelos de segmentación/gesto + fondos).
- Probado en un Samsung A55: flujo de bienvenida, cámara, captura, resultado, guardado, modo administrador, PIN, borrado de fotos, layout responsive, pestaña Evento y edición de marca en vivo. Hay un fondo personalizado persistido de prueba en el catálogo.
- Falta validar Quick Share, el gesto de palma, la importación/ocultado de fondos y el cutout con buena luz en el A55; también la escala en la pantalla de 55".
- Rama `angel-ortega`; el gesto de palma está en `2c9b619`, el merge de `origin/main` en `94ee19d` y Quick Share en `989af84`. Los cambios de eventos/fondos aún no se commitean.

## Pendientes

- Probar Quick Share, el gesto de palma, la importación de fondos y el guardado de etiquetas en el A55 desbloqueado.
- Probar la UI en la pantalla de 55" y ajustar escala/tamaños táctiles.
- Agregar `UvcSource` cuando llegue el kiosko (validar UVC y ausencia de Play Services).
- Validar `startLocalOnlyHotspot` en el equipo; fallback: router de viaje sin internet.
- Servidor HTTP local + QR de descarga; conectar el botón de hotspot del admin.
- Impresión queda para después.
