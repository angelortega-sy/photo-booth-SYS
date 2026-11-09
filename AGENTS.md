# AGENTS.md

Kiosko de fotos para Android. El scaffold ya existe: un módulo `app/` que compila con el Gradle wrapper.
Paquete: `com.photobooth.kiosk`. App visible: “Cabina de Fotos”.

## Stack y alcance (decidido)

- Kotlin + Jetpack Compose, un solo módulo `app/`.
- Versiones fijadas para AGP 8.13.2 / `compileSdk 36`: Kotlin 2.3.21, Compose BOM 2025.09.01, core-ktx 1.17.0, activity-compose 1.11.0, lifecycle 2.9.4, CameraX 1.6.2, MediaPipe 0.10.35.
- No subas core/compose/lifecycle a versiones que exijan `compileSdk 37` o AGP 9.1 sin actualizar AGP y Gradle primero.
- Pantalla kiosko en **vertical (portrait)**; salida y fondos a 1080×1920. En producción la cámara es una **webcam USB**, no una cámara integrada. Para pruebas en teléfono, CameraX usa la cámara frontal si existe (selfie) y la trasera como fallback.
- Segmentación de la persona **en el dispositivo** (preferir MediaPipe Image Segmenter; ML Kit solo si el equipo tiene Google Play Services) y composición sobre un fondo predefinido.
- Fondos locales como fallback/offline. La app puede llamar **solo** a `https://110-238-85-51.sslip.io` (cabeceras firmadas con la llave del tótem). Nunca llama a OpenRouter y la API key no entra al APK, a `BuildConfig` ni a los recursos.
- Salida: QR para iPhone/Android + Quick Share Android. El flujo nuevo quita “Guardar”.
- Offline: fallback local (fondos + QR local de `LocalDeliveryServer`). Si el tótem no está registrado o no hay red, el QR local sigue funcionando. La generación IA requiere red.

## Convenciones de código

- Código, comentarios, nombres de archivos, clases, funciones y logs en **inglés**.
- Solo los textos visibles para el usuario (UI, errores en pantalla) van en **español**.

## Flujo

captura (webcam USB; CameraX en teléfono) → 3 fotos → estilo del servidor o “así está bien” → diseño local → QR de Drive (o QR local si no hay red) + Quick Share.

## Experiencia de uso

- La app abre en una **pantalla de bienvenida**; la cámara no se enciende hasta tocar “Comenzar”.
- Si el invitado no interactúa por **60 s**, la sesión termina y vuelve a bienvenida (se libera la cámara).
- En cámara, el botón “Fondos” abre un **bottom sheet con carrusel** de fondos; no dejar el selector fijo sobre el preview.
- El resultado incluye un wizard de personalización; el CTA grande es **“Descargar · 4 fotos”** (3 fotos + tira) y “Guardar” queda como acción secundaria.
- Después de descargar/compartir, se cierra la sesión y vuelve a bienvenida. Guardar mantiene el resultado en pantalla.
- Paleta **azul Syscom** sobre fondo oscuro: `SyscomBlue #0B63CE`, `SyscomBlueLight #5EA8FF`, `Ink #050B14`, `InkSoft #0B1727`, `Cream #F3F8FF`.
- Los dos modos comparten el **mismo shell** (`AiSingleFlow` y `ui/collage/CollageFlow`): barra superior con “Paso X de N + nombre”, progreso lineal, contenido scrolleable, barra inferior fija Regresar / Continuar y QR en línea en el último paso. **No volver a usar modales para la entrega ni contadores distintos**: el collage usa el mismo 3-2-1 de 700 ms por segundo que el modo IA.
- `CollageFlow` pasos: Captura (un toque = 3 tomas con pausa “Posa 2 de 3”; rail lateral 1/2/3 para ver o repetir una foto, **nunca muestra la tira ni marcos**) → Fondo (las 3 fotos en un pager grande de una en una y los fondos como cuadritos de 3 columnas; selector híbrido “Las 3 fotos” o una foto en particular) → Diseño (marca del evento si hay más de una, marco, adorno, formato y borde) → Entrega (pager con foto 1/2/3 y la tira, QR automático, copiar liga y Quick Share). `StudioWizardScreen` y los diálogos de entrega se eliminaron.

## Modo administrador

- Se entra con **long-press sobre “SYSCOM”** en la pantalla de bienvenida; no poner un botón visible para invitados.
- PIN de **4 a 8 dígitos**, guardado como hash SHA-256 con salt en `SharedPreferences` (`admin_auth`); nunca guardar el PIN en claro.
- `AdminAuth` maneja el PIN; `AdminGate` pide crearlo la primera vez y verificarlo después.
- El admin vive en `ui/admin/`: `AdminShell` (PIN, back stack con `rememberSaveable` + `BackHandler`, header) y una pantalla por área (`AdminHomeScreen`, `AdminBrandsScreen`, `AdminBrandEditorScreen`, `AdminEventScreen`, `AdminCameraScreen`, `AdminStylesScreen`, `AdminPhotosScreen`, `AdminUsageScreen`, `AdminNetworkScreen`, `AdminSystemScreen`). `ui/AdminScreen.kt` es solo el wrapper de `AdminGate`. **No volver a meter todas las secciones en un solo scroll.**
- `AdminHomeScreen` es un grid de tarjetas grandes (Marcas, Fondos, Cámara, Estilos, Fotos, Red, Uso IA) con resumen; cada una abre su pantalla. Sirve para pantalla de 55". **No volver a poner una tarjeta “Evento” que abra lo mismo que Fondos**: los textos de la marca se editan dentro de su editor.
- El admin muestra: resumen del dispositivo, cámaras del sistema (frontal/trasera/externa, MP, resolución y nivel de hardware), dispositivos USB, prueba de cámara en vivo, fotos guardadas con borrado individual/todas, red/IP local y accesos a ajustes de red/hotspot.
- Fotos: `PhotoLibrary` consulta `MediaStore` en `Pictures/Cabina de Fotos`, usa `READ_MEDIA_IMAGES` en API 33+ y `READ_EXTERNAL_STORAGE` en API ≤32. El borrado directo puede fallar si la app ya no es dueña del archivo; mostrar error, no crashear.
- Guardar copia en el dispositivo es **opt-in**: `PhotoSettingsStore` (`SharedPreferences("photo_settings")`, clave `save_to_device`, default `false`). Cuando está activo, `PhotoArchive.save()` copia el resultado final (3 fotos + tira, o la foto IA) a la galería. El switch vive en Admin → Fotos.
- El botón de hotspot solo abre los ajustes del sistema. El hotspot programático + QR de descarga es la siguiente fase.
- Layout admin responsive: header compacto (atrás + título + cerrar), botones de red a ancho completo, fotos en **2 columnas bajo 600 dp** y 4 arriba. No volver a meter dos botones de texto largo en un `Row` angosto.
- La lista de fondos del admin se lee de `state.adminBackgrounds` (estado observable), no del getter `repository.allOptions`; así importar/borrar/renombrar refresca la lista sin salir de la pestaña.

## Fondos y etiquetas del evento

- **Admin → Evento** permite importar fondos desde el almacenamiento con SAF (`OpenMultipleDocuments("image/*")`), ocultar/mostrar fondos integrados y renombrar/eliminar los personalizados.
- Los fondos importados se copian a `filesDir/backgrounds/custom_<timestamp>.webp`, normalizados a **1080×1920**. No van a `assets/` ni al repo; se pierden si se borran los datos de la app.
- El catálogo se persiste en `SharedPreferences("background_catalog")`; los invitados solo ven los fondos habilitados + “Cámara real”.
- `EventBranding` agrupa marca, tagline, título/subtítulo de bienvenida y headline/subheadline/tag del badge. **No volver a hardcodear “EXPO SYSCOM 2027”** en la UI.
- Las marcas viven en `EventProfileStore` (`SharedPreferences("event_profiles")`, `profiles` + `active_id`). `EventProfile = id + name + branding + enabled + builtIn`; **SYSCOM siempre existe**, no se puede borrar ni deshabilitar, y es el default. Cualquier otra marca se agrega desde Admin → Evento y se puede activar/deshabilitar/borrar.
- `SharedPreferences("event_branding")` queda solo como migración legacy: `EventProfileStore` lo lee una vez si no hay perfiles guardados.
- Admin edita las etiquetas de la marca **activa** (`updateEventBranding` escribe sobre el perfil activo). Activar una marca actualiza la bienvenida y el marco de inmediato.
- El paso “Tu foto” del flujo IA muestra la fila “Marca del evento” solo cuando hay más de un perfil habilitado, más el selector de marco (`CollageFrameStyle`) y el de adorno (`FrameDecoration`: Limpio/Esquinas/Confeti/Tech/Festivo, dibujado en `PhotoFrameComposer.drawDecoration`). Los adornos se reinician con cada foto nueva.
- Cada `CollageFrameStyle` define una **forma** (`FrameShape`: `tech`, `polaroid`, `cyber`, `gala`), no la paleta. `PhotoFrameComposer` ramifica `drawFrameBackground` (silueta), `drawTopHeader` (posición de la marca y del tag), `drawPhotoPanel` (recorte de la foto: octágono, recto, chaflán o arco) y `drawFooter` (título centrado, en panel o con sello) según `frameStyle.shape`. Al agregar un marco nuevo hay que elegir una forma (o crear una nueva y cubrir los cuatro `when`).
- El **color lo pone la marca**, no el marco: `EventBranding` guarda `primaryColor` (fondo), `secondaryColor` (líneas/acentos) y `logoPath` (opcional). `CollageCustomization.palette()` deriva `FramePalette` (fondo secundario, acentos, texto claro/oscuro por luminancia). Los `*Hex` de `CollageFrameStyle` quedaron muertos para el render; no volver a leerlos.
- El logo se elige en Admin → Marcas → editor con SAF, se copia a `filesDir/logos/logo_<timestamp>.webp` con `BrandLogoStore` (máx. 1024 px, WebP) y se guarda **la ruta absoluta** en `logoPath`. El renderer dibuja el logo en la cabecera en lugar del texto y cae al texto si el archivo falta o no decodifica.
- El editor de marca muestra `FramePreviewRow`: los 4 marcos renderizados en vivo con la marca en edición (foto de muestra `assets/backgrounds/gala.webp`, `previewScale = true`, debounce 220 ms). Tocar una miniatura la abre a pantalla completa (`ExpandedFramePreview`, se re-renderiza a 720 px). No usa fotos de invitados.
- Abrir el editor **no** activa la marca; guardar escribe solo ese perfil (`updateEventProfileBranding(profileId, branding)`). Para que una marca sea la principal se usa “Hacer principal”, disponible en la lista de marcas y en el editor. Así se puede editar una marca sin cambiar la bienvenida ni los marcos de los invitados.
- `EventProfileStore.add()` deduplica por nombre (case-insensitive) y genera id con UUID, así un doble toque en “Agregar” no crea dos marcas iguales. Las marcas nunca se ven vacías: `PhotoBoothUiState` arranca con `profileStore.loadAll()`, que siempre incluye SYSCOM.
- `PhotoFrameComposer` es defensivo: si la marca está vacía no dibuja la píldora, si no hay headline/subheadline no dibuja el pie ni el sello, y todo texto largo se recorta con `ellipsized` antes de chocar con el sello o salir del marco.
- Al cambiar de fondo en el resultado, `requestPreviewUpdate` recompone con el catálogo actual; si el fondo elegido se oculta o borra, el ViewModel cae al primer fondo visible.

## Idiomas (ES/EN)

- Textos en `res/values/strings.xml` (español) y `res/values-en/strings.xml` (inglés). **Toda cadena visible nueva va en ambos archivos** con las mismas claves y placeholders (`%1$s`).
- `data/AppLanguage.kt`: enum `SYSTEM/SPANISH/ENGLISH` + `LanguageStore` (`SharedPreferences("app_language")`). `MainActivity.attachBaseContext` aplica `LanguageStore.wrap(...)`; cambiar idioma persiste y hace `recreate()`.
- El invitado puede cambiar con `LanguageQuickToggle` en la bienvenida; el admin en **Sistema** con `LanguageSelector`. `res/xml/locales_config.xml` + `android:localeConfig` exponen es/en al sistema.
- Pendiente conocido: los mensajes de error resueltos con `applicationContext` (ViewModels) siguen el idioma del dispositivo, no el forzado; se arreglaría con un `Application.attachBaseContext` propio.

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
- Catálogo de estilos (19): los 14 originales más `actionfigure`, `vinyl`, `catrina`, `mariachi` y `xmas`. Viven en `ServerPhotoBoot/styles.json` (fuente de verdad), `AiStyles.ALL` (fallback offline) y `assets/style_previews/<id>.webp` (270×480). Para agregar uno nuevo hay que tocar los tres y actualizar el test `test/expo-folder.test.ts`.
- Accesorios (24): además de los 16 previos hay `gown`, `tux`, `santa_hat`, `cowboy`, `flower_crown`, `mustache`, `guitar` y `balloons`. Se agrupan por `head`/`face`/`extra`/`hair`/`outfit` con selección única por grupo; el backend los valida en `accessories.json` y responde 400 si la app manda un id que el servidor aún no tiene (desplegar backend antes que el APK).
- `AiAccessories.blockedForStyle()` oculta los grupos que un estilo ya define (`mariachi`/`catrina`/`noir`: cabeza y ropa; `xmas`: ropa; `catrina` además `mask`) y `blockedBySelection()` evita combinar pelo teñido con sombrero. `AiSingleViewModel.selectStyle` limpia los accesorios que dejan de ser válidos.
- Los iconos de accesorios son vector drawables propios en `res/drawable/ic_accessory_*.xml` (línea blanca, tint de Compose); no volver a mapearlos a iconos de Material.

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

- Con red y tótem registrado, el QR de entrega apunta al `shareUrl` de Google Drive que devuelve `POST /v1/sessions/:id/finalize`.
- Sin red o sin registro, el QR sigue siendo el servidor HTTP local (`http://<ip-del-kiosko>:<puerto>/p/<token>`). Quick Share no cambia.
- El kiosko corre un servidor HTTP local y funciona sin internet, con hotspot propio o con el Wi-Fi del lugar.
- Leer el IP real del hotspot en runtime; no hardcodear `192.168.43.1`.
- Android avisa "sin internet" y puede saltar a datos móviles: indicar en pantalla que el invitado elija "mantener conexión" y desactive el cambio automático.
- Fallback: share sheet (`ACTION_SEND` + `FileProvider`) si el invitado no puede entrar a la red.
- Servir solo la foto de la sesión con token aleatorio; no exponer toda la galería. Guardar copia local en `MediaStore`.

## Fondos (contenido, no runtime)

- La app **nunca** llama a OpenRouter. El único runtime permitido es `https://110-238-85-51.sslip.io`. Los prompts viven en el servidor; la cabina manda `styleId` y `modelId`.
- La key de OpenRouter vive solo en el VPS. No debe terminar en el APK, en `SharedPreferences`, en el repo ni en `tools/generate-backgrounds/.env` publicado. Ese `.env` local sigue gitignored.
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

## Pruebas en el kiosko

- El **usuario** opera la app en el tótem y da el feedback; no manejar la UI por `adb shell input tap` ni recorrer flujos automáticamente. Sí se puede instalar el APK, leer `logcat` y tomar capturas cuando lo pida.

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
