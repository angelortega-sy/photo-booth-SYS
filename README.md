# Cabina de Fotos SYSCOM (`photo-booth-SYS`)

Aplicación Android de kiosko interactivo en formato **vertical (portrait 9:16)** diseñada para **EXPO SYSCOM**. Permite a los asistentes acercarse al tótem, iniciar una sesión de **3 fotos consecutivas automáticas**, segmentar su silueta en el dispositivo sin conexión a internet y personalizar al instante tanto sus fotos individuales como su collage conmemorativo antes de guardarlos o compartirlos en redes sociales.

---

## Características Principales

### 1. Pantalla de Bienvenida de Alto Impacto (Attract Screen)
- Diseño inmersivo en paleta oficial **Syscom Blue** (`#0B63CE`, `#5EA8FF`, `#050B14`, `#F3F8FF`).
- Abanico visual con ejemplos de collages reales, indicadores claros de los 3 pasos de la experiencia y botón principal con pulso animado.
- La cámara permanece apagada mientras está en bienvenida y se libera automáticamente tras **60 segundos de inactividad**.

### 2. Captura Automática Continua de 3 Fotos + Cuenta Regresiva a Distancia
- **Un solo toque para las 3 poses:** Al presionar el botón de captura una sola vez, el kiosko ejecuta automáticamente la secuencia de 3 fotos.
- **Cuenta regresiva de larga distancia:** Anillo de progreso continuo con gradiente luminoso (`270.dp`), borde perimetral pulsante en toda la pantalla y dígitos gigantes (`136.sp`) con animación elástica visibles a 2–3 metros del tótem.
- **Transición guiada entre poses:** Entre la Foto 1→2 y Foto 2→3 se muestra un aviso animado de cambio de pose mientras las miniaturas capturadas aparecen en tiempo real en el HUD superior.

### 3. Pipeline de Segmentación en Tiempo Real y Latencia Cero (< 40 ms)
- **Segmentación en paralelo durante la sesión:** Tan pronto se captura cada foto, `PhotoProcessor.extractCutout()` ejecuta **MediaPipe Image Segmenter** (`selfie_multiclass_256x256.tflite`) + **Guided Filter** en segundo plano mientras el usuario posa para la siguiente foto.
- **Caché de recortes con alfa (`PreparedCutout`):** El recorte de la persona y su máscara refinada se conservan en memoria durante la sesión activa. Cambiar de fondo, diseño de collage, marco o sello en el estudio tarda **menos de 40 ms** usando composición en `Canvas` sin volver a ejecutar el modelo neuronal ni bloquear la interfaz con pantallas de carga.
- **Exportación bajo demanda:** Los 4 archivos JPEG finales en alta resolución (`3 fotos individuales 1080×1920` + `1 collage en alta definición`) se generan únicamente al presionar **Guardar** o **Compartir**.

### 4. Estudio Wizard de Personalización en 3 Pasos (`StudioWizardScreen`)
En lugar de una pantalla estática de resultados, el invitado entra directamente a un estudio interactivo tipo *stepper*:

1. **Paso 1 — Fondos (`1. Fondos`):**
   - Permite aplicar un fondo a **las 3 fotos a la vez** o tocar cualquier foto del collage para asignarle un **fondo individual distinto**.
   - Incluye 6 fondos oficiales offline en `1080×1920` WebP (`Gala`, `Neón`, `Playa`, `Estudio`, `Retro 80s`, `Jardín`) más la opción **Cámara real** (sin recorte).
2. **Paso 2 — Collage y Marcos (`2. Collage y Marco`):**
   - **3 Diseños de Collage (`CollageLayout`):**
     - `Póster 1+2` (`1080×1920` — 1 foto hero superior + 2 inferiores, ideal para Stories/Reels).
     - `Tira Clásica` (`1080×2640` — 3 fotos apiladas estilo cabina clásica).
     - `Póster Editorial` (`1080×1920` — mosaico asimétrico tipo revista con tarjeta lateral SYSCOM).
   - **4 Estilos de Marco General (`CollageFrameStyle`):** `Azul Syscom`, `Galería Perla`, `Cyber Neón` y `Gala VIP`.
   - **4 Estilos de Borde por Cuadro (`PhotoBorderStyle`):** `Borde Suave`, `Marco Blanco` (tipo Polaroid), `Visor Tech` (brackets HUD `CAM 01 · HD`) y `Corte Editorial`.
3. **Paso 3 — Sello del Evento y Entrega (`3. Sello y Entrega`):**
   - **5 Insignias y Frases Oficiales de un toque (`EventBadgePreset`):**
     - `EXPO SYSCOM 2027 · CABINA DE FOTOS OFICIAL`
     - `SYSCOM · INNOVACIÓN · TECNOLOGÍA QUE CONECTA EL FUTURO`
     - `INVITADO VIP · SYSCOM · EXPERIENCIA EXCLUSIVA EXPO 2027`
     - `EQUIPO SYSCOM · LIDERANDO LA INDUSTRIA · 2027`
     - `RECUERDO OFICIAL · VIVIENDO LA EXPERIENCIA SYSCOM`
   - Interruptor de un toque para mostrar u ocultar la **fecha del evento** en el encabezado.
   - Botones de acción para **Guardar todo (4 fotos)** en `Pictures/Cabina de Fotos` y **Compartir** mediante el selector nativo de Android.

### 5. Modo Administrador Protegido por PIN
- **Acceso oculto:** Mantener presionado el logotipo **SYSCOM** en la esquina superior izquierda de la pantalla de bienvenida.
- **Seguridad:** PIN de 4 a 8 dígitos almacenado como hash **SHA-256 con sal aleatoria** en `SharedPreferences` (`admin_auth`).
- **Diagnóstico y Gestión:** Resumen del dispositivo, inspección de cámaras detectadas (frontal, trasera, externa/USB), prueba de cámara en vivo, galería local con borrado individual o masivo, y accesos rápidos a la configuración de red y hotspot del sistema.

---

## Arquitectura del Proyecto

```text
app/src/main/
├── assets/
│   ├── backgrounds/                  # 6 fondos verticales 1080x1920 WebP (offline)
│   └── models/
│       └── selfie_multiclass_256x256.tflite
├── java/com/photobooth/kiosk/
│   ├── MainActivity.kt               # Modo inmersivo pantalla completa + keep screen on
│   ├── admin/
│   │   ├── AdminAuth.kt              # Hash SHA-256 con salt para PIN de administrador
│   │   ├── DeviceDiagnostics.kt      # Diagnóstico de cámaras Camera2, USB y red
│   │   └── PhotoLibrary.kt           # Consulta y borrado en MediaStore
│   ├── camera/
│   │   └── CameraPreview.kt          # Captura y vista previa con CameraX
│   ├── data/
│   │   ├── BackgroundRepository.kt   # Caché de miniaturas, previews 540x960 y fondos 1080x1920
│   │   └── CustomizationOptions.kt   # Layouts, estilos de marco, bordes y sellos SYSCOM
│   ├── photo/
│   │   ├── BitmapTransforms.kt       # Rotación, espejo y center-crop
│   │   ├── PhotoFrameComposer.kt     # Motor Canvas para collages, marcos, HUD y sellos
│   │   ├── PhotoProcessor.kt         # Segmentación MediaPipe + Guided Filter + PreparedCutout
│   │   └── PhotoSaver.kt             # Guardado en MediaStore y FileProvider para compartir
│   └── ui/
│       ├── AdminScreen.kt            # Panel de administración por secciones
│       ├── PhotoBoothApp.kt          # Flujo principal, bienvenida y cuenta regresiva animada
│       ├── PhotoBoothViewModel.kt    # Estado reactivo, pipeline de recorte y exportación
│       ├── StudioWizardScreen.kt     # Estudio interactivo de personalización en 3 pasos
│       └── theme/Theme.kt            # Paleta de colores y tipografía Syscom
```

---

## Stack Tecnológico

- **Lenguaje y UI:** Kotlin `2.3.21` + Jetpack Compose (`BOM 2025.09.01`), Material 3.
- **Android SDK:** `compileSdk 36`, `targetSdk 36`, `minSdk 26`, Android Gradle Plugin `8.13.2`.
- **Cámara:** AndroidX CameraX `1.6.2` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`).
- **Visión Artificial On-Device:** MediaPipe Tasks Vision `0.10.35` (`ImageSegmenter` + refinamiento de bordes mediante *Guided Filter* propio en Kotlin).

---

## Compilación e Instalación

### Requisitos previos
- JDK 17+
- Android SDK con plataforma 36 instalada

### Comandos principales (Windows PowerShell)

```powershell
# Compilar APK de depuración
.\gradlew.bat assembleDebug

# Instalar directamente en un dispositivo o tótem conectado por ADB
.\gradlew.bat installDebug

# Instalación manual vía ADB
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

El APK compilado se genera en:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Generación Offline de Fondos (Herramienta de Desarrollo)

La aplicación Android funciona **100% offline** y no realiza llamadas de red ni incluye llaves de API. Para generar nuevos paquetes de fondos durante el diseño del evento se incluye el script Node.js en [`tools/generate-backgrounds/`](tools/generate-backgrounds/README.md).
