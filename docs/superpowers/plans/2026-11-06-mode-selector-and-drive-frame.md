# Selector visual de modo y marco de Drive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar el selector textual de modo por dos previews grandes y asegurar que la foto de Foto IA subida a Drive incluya el marco personalizado final.

**Architecture:** La app empaqueta dos previews WebP estáticos: un mosaico 2×3 que representa estilos de Foto IA y una tira de tres fotos para Collage clásico. `ModeSelectScreen` los presenta como tarjetas táctiles apiladas. Para la entrega IA, el backend deja de subir el resultado crudo de OpenRouter; la app compone el JPEG final con `PhotoFrameComposer` y lo sube mediante un endpoint firmado antes de habilitar la entrega de Drive.

**Tech Stack:** Kotlin, Jetpack Compose, Android resources WebP, Node.js, Fastify, Vitest, Google Drive API.

## Global Constraints

- La app se mantiene en Kotlin + Jetpack Compose, un solo módulo `app/`, AGP 8.13.2 y `compileSdk 36`.
- La interfaz visible y los errores de pantalla permanecen en español; código, comentarios y logs en inglés.
- La pantalla kiosko es vertical y la UI debe conservar zonas táctiles grandes.
- La app solo llama a `https://110-238-85-51.sslip.io` mediante solicitudes firmadas; OpenRouter y sus llaves no entran al APK.
- Los recursos finales se guardan en `app/src/main/res/drawable-nodpi/`; las fuentes de Unsplash y archivos temporales no se versionan.
- El trabajo de la app sale en `dittrich` y se integra a `main` mediante pull request. El servidor se trabaja y publica desde `main`.
- No versionar `.env`, `secrets/*`, tokens ni llaves.

---

### Task 1: Crear previews estáticos del selector

**Files:**
- Create: `photo-booth-SYS/app/src/main/res/drawable-nodpi/mode_ai_styles.webp`
- Create: `photo-booth-SYS/app/src/main/res/drawable-nodpi/mode_collage_classic.webp`

**Interfaces:**
- Consumes: Un retrato permitido de Unsplash descargado solo como fuente temporal.
- Produces: `R.drawable.mode_ai_styles` y `R.drawable.mode_collage_classic` para `ModeSelectScreen`.

- [ ] **Step 1: Descargar un único retrato fuente con licencia Unsplash**

Guarda el archivo fuente fuera del repositorio. El retrato debe tener cara completa, una sola persona, ojos visibles, fondo sin texto y composición vertical.

- [ ] **Step 2: Generar el mosaico Foto IA**

Usa el mismo retrato como referencia y genera seis paneles: Ghibli, Pixar, anime, cómic, Simpsons y LEGO. Compón los seis paneles en una cuadrícula 2×3 con esta estructura:

```text
┌──────────┬──────────┬──────────┐
│ Ghibli   │ Pixar    │ Anime    │
├──────────┼──────────┼──────────┤
│ Cómic    │ Simpsons │ LEGO     │
└──────────┴──────────┴──────────┘
```

No pongas texto, logos ni etiquetas dentro de la imagen. Exporta el mosaico como `mode_ai_styles.webp` a 1080×720, con cada panel legible a 55".

- [ ] **Step 3: Generar el preview de Collage clásico**

Construye una tira de tres fotografías del mismo retrato, con fondo Syscom azul oscuro y tratamiento de marco consistente con `PhotoFrameComposer`. No agregues texto dentro de la imagen. Exporta `mode_collage_classic.webp` a 1080×540.

- [ ] **Step 4: Validar los recursos**

Ejecuta:

```powershell
Get-Item app\src\main\res\drawable-nodpi\mode_ai_styles.webp,
  app\src\main\res\drawable-nodpi\mode_collage_classic.webp |
  Select-Object Name, Length
```

Esperado: ambos archivos existen, tienen tamaño mayor a cero y sus nombres permiten que Android genere los IDs de recurso indicados.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/drawable-nodpi/mode_ai_styles.webp app/src/main/res/drawable-nodpi/mode_collage_classic.webp
git commit -m "feat: add visual previews for booth modes"
```

### Task 2: Rehacer el selector como dos tarjetas visuales verticales

**Files:**
- Modify: `photo-booth-SYS/app/src/main/java/com/photobooth/kiosk/ui/ModeSelectScreen.kt`
- Test: `photo-booth-SYS/app/src/main/java/com/photobooth/kiosk/ui/ModeSelectScreen.kt` through `:app:compileDebugKotlin`

**Interfaces:**
- Consumes: `R.drawable.mode_ai_styles`, `R.drawable.mode_collage_classic`, `onPickAi()`, `onPickCollage()`, `onHome()`.
- Produces: Dos tarjetas verticales de ancho completo, cada una con preview, título y acción inequívoca.

- [ ] **Step 1: Reemplazar el diseño de filas por tarjetas apiladas**

Elimina `ModeIconBadge`, el ícono de chevron y los subtítulos actuales. Mantén `onPickAi`, `onPickCollage` y `onHome` sin cambiar sus firmas.

Define una tarjeta privada que reciba el recurso de preview:

```kotlin
@Composable
private fun ModePreviewCard(
    @DrawableRes previewRes: Int,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = InkSoft,
    ) {
        Box {
            Image(
                painter = painterResource(previewRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = 0.94f)),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Column {
                    Text(title, color = Cream, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = Cream.copy(alpha = 0.82f), fontSize = 16.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Usar las dos tarjetas en `ModeSelectScreen`**

Conserva únicamente la marca `SYSCOM`, una instrucción breve y un botón discreto para volver. Usa:

```kotlin
ModePreviewCard(
    previewRes = R.drawable.mode_ai_styles,
    title = "Foto IA",
    subtitle = "Tu foto, muchos estilos",
    accent = SyscomBlueLight,
    onClick = onPickAi,
)
Spacer(Modifier.height(16.dp))
ModePreviewCard(
    previewRes = R.drawable.mode_collage_classic,
    title = "Collage clásico",
    subtitle = "3 fotos para llevar",
    accent = SyscomBlue,
    onClick = onPickCollage,
)
```

Usa un `Column` con altura completa entre encabezado y botón de regreso para que las tarjetas ocupen el espacio restante y no requieran scroll en una pantalla portrait.

- [ ] **Step 3: Compilar y revisar visualmente**

Ejecuta:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --offline
```

Esperado: `BUILD SUCCESSFUL`. Abre la app en un dispositivo o emulador portrait y verifica que ambas tarjetas se vean completas, que cada área de imagen responda al toque y que los callbacks entren al modo correcto.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/photobooth/kiosk/ui/ModeSelectScreen.kt
git commit -m "feat: make booth mode selection visual"
```

### Task 3: Añadir un endpoint firmado para el JPEG IA ya enmarcado

**Files:**
- Modify: `ServerPhotoBoot/src/routes/sessions.ts`
- Modify: `ServerPhotoBoot/test/sessions.test.ts`

**Interfaces:**
- Consumes: `POST /v1/sessions/:id/result` con multipart field `result`.
- Produces: Un solo archivo `foto-ia.jpg` en la carpeta Drive de la sesión, validado por `preparePhoto`.

- [ ] **Step 1: Escribir la prueba que falla**

En `ServerPhotoBoot/test/sessions.test.ts`, tras crear una sesión y subir `photo0`, agrega una solicitud firmada multipart:

```ts
const finalImage = multipart([
  { name: 'result', filename: 'foto-ia.jpg', type: 'image/jpeg', data: photo },
]);
const delivered = await injectRaw(
  built,
  keys.privateKey,
  device.id,
  'POST',
  `/v1/sessions/${sessionId}/result`,
  finalImage.body,
  finalImage.contentType,
);
expect(delivered.statusCode).toBe(200);
expect(drive.folders.get('folder-1')?.files).toContain('foto-ia.jpg');
```

- [ ] **Step 2: Verificar que la prueba falla**

Ejecuta:

```powershell
npx vitest run test/sessions.test.ts
```

Esperado: fallo `404` para `/v1/sessions/:id/result`.

- [ ] **Step 3: Implementar `POST /v1/sessions/:id/result`**

En `ServerPhotoBoot/src/routes/sessions.ts`, agrega junto a `/photos`:

```ts
app.post('/v1/sessions/:id/result', async (request) => {
  const deviceId = requireDevice(request);
  const session = loadOwnedSession(deps, deviceId, sessionParam(request), deps.now());
  if (session.finalized_at) {
    throw new ApiError(409, 'session_finalized', 'The result cannot be replaced after the link is created');
  }
  if (!session.drive_folder_id) {
    throw new ApiError(502, 'drive_error', 'Session has no Drive folder');
  }
  const file = await readMultipartField(request, 'result');
  const prepared = await preparePhoto(file);
  const dir = sessionDirectory(deps.config.dataDir, session.id);
  const target = path.join(dir, 'final.jpg');
  await writeFile(target, prepared.jpeg);
  await deps.drive.uploadJpeg(session.drive_folder_id, 'foto-ia.jpg', prepared.jpeg);
  return { sessionId: session.id, bytes: prepared.jpeg.length };
});
```

Agrega esta función junto a `readBackgroundFile`:

```ts
async function readFinalResult(request: FastifyRequest): Promise<Buffer> {
  const contentType = request.headers['content-type'];
  if (typeof contentType !== 'string' || !contentType.toLowerCase().includes('multipart/form-data')) {
    throw new ApiError(400, 'validation_error', 'The result must be sent as multipart form data');
  }
  const files = await parsePhotoParts(contentType, request.rawBody ?? Buffer.alloc(0), ['result']);
  const file = files.get('result');
  if (!file) throw new ApiError(400, 'validation_error', 'Missing result');
  return file;
}
```

Usa `const file = await readFinalResult(request);` dentro del handler. `parsePhotoParts` conserva su límite de 15 MB y rechaza campos inesperados, duplicados o incompletos.

- [ ] **Step 4: Evitar la entrega cruda de OpenRouter**

En el handler `/stylize`, conserva `imageBase64` como respuesta para que la app componga el marco, pero elimina:

```ts
await deps.drive.uploadJpeg(session.drive_folder_id, fileName, prepared.jpeg);
await writeFile(path.join(sessionDirectory(deps.config.dataDir, session.id), fileName), prepared.jpeg);
```

Elimina también `fileName` si no tiene otro consumidor. La carpeta de Drive no debe exponer el resultado sin marco.

- [ ] **Step 5: Ejecutar la prueba**

Ejecuta:

```powershell
npx vitest run test/sessions.test.ts
```

Esperado: la prueba de `result` pasa y el fake Drive contiene `foto-ia.jpg`.

- [ ] **Step 6: Commit**

```powershell
git add src/routes/sessions.ts test/sessions.test.ts
git commit -m "feat: accept framed AI result for Drive delivery"
```

### Task 4: Subir el JPEG final enmarcado desde Foto IA

**Files:**
- Modify: `photo-booth-SYS/app/src/main/java/com/photobooth/kiosk/network/AiBackendClient.kt`
- Modify: `photo-booth-SYS/app/src/main/java/com/photobooth/kiosk/ui/ai/AiSingleViewModel.kt`
- Test: `photo-booth-SYS/app/src/main/java/com/photobooth/kiosk/ui/ai/AiSingleViewModel.kt` through `:app:compileDebugKotlin`

**Interfaces:**
- Consumes: `AiBackendClient.uploadFinalResult(sessionId: String, result: File)`.
- Produces: Una sesión remota con `foto-ia.jpg` en Drive antes de que `AiStatus.READY` permita generar QR.

- [ ] **Step 1: Añadir el cliente multipart firmado**

En `AiBackendClient.kt`, junto a `uploadPhoto`, agrega:

```kotlin
fun uploadFinalResult(sessionId: String, result: File) {
    val boundary = "----Booth${UUID.randomUUID().toString().replace("-", "")}"
    val body = multipart(boundary, listOf("result" to result))
    open(
        "POST",
        "/v1/sessions/$sessionId/result",
        body,
        "multipart/form-data; boundary=$boundary",
        sign = true,
    )
}
```

- [ ] **Step 2: Subir el archivo después de componer el marco completo**

En `AiSingleViewModel.generate`, reemplaza:

```kotlin
withContext(Dispatchers.IO) { PhotoFrameComposer.writeJpeg(full, framedFile) }
```

por una escritura comprobada y la subida antes de liberar `full`:

```kotlin
val saved = withContext(Dispatchers.IO) {
    PhotoFrameComposer.writeJpeg(full, framedFile)
}
if (!saved) throw AiBackendException("Could not write framed photo")
withContext(Dispatchers.IO) {
    aiClient.uploadFinalResult(session.id, framedFile)
}
```

Actualiza la asignación de `remoteSessionId` para conservar `session.id` y usa ese mismo ID para `driveShareUrl`.

- [ ] **Step 3: Mantener sincronizado el resultado si cambia el marco**

En `rebuildFramed`, después de escribir `framedFile`, llama a:

```kotlin
remoteSessionId?.let { sessionId ->
    withContext(Dispatchers.IO) { aiClient.uploadFinalResult(sessionId, framedFile) }
}
```

Si la subida falla, conserva `resultFile` local y usa `showError` para informar el fallo de Drive; no reemplaces el preview ni borres el resultado local.

- [ ] **Step 4: Compilar y validar entrega**

Ejecuta:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin --offline
```

Esperado: `BUILD SUCCESSFUL`.

En un tótem registrado, genera Foto IA, elige un marco, abre el QR de Drive y confirma que `foto-ia.jpg` contiene el marco, la marca y el estilo seleccionados. Cambia el marco antes de finalizar y confirma que Drive recibe la versión actualizada.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/photobooth/kiosk/network/AiBackendClient.kt app/src/main/java/com/photobooth/kiosk/ui/ai/AiSingleViewModel.kt
git commit -m "feat: upload framed AI photo to Drive"
```

### Task 5: Verificación final y publicación

**Files:**
- Modify: Ninguno.

**Interfaces:**
- Consumes: commits de Tasks 1–4.
- Produces: Server `main` publicado y app integrada de `dittrich` a `main`.

- [ ] **Step 1: Ejecutar la suite del servidor**

```powershell
npm test
```

Esperado: todas las pruebas Vitest pasan.

- [ ] **Step 2: Generar el APK de depuración**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

Esperado: `BUILD SUCCESSFUL` y `app/build/outputs/apk/debug/app-debug.apk` existe.

- [ ] **Step 3: Publicar el servidor**

```powershell
git -C ..\ServerPhotoBoot push origin main
```

Esperado: el commit del endpoint y la entrega enmarcada aparece en `origin/main`.

- [ ] **Step 4: Publicar la app**

```powershell
git push origin dittrich
gh pr create --base main --head dittrich --title "Make mode selection visual and deliver framed AI photos" --body "Adds visual mode previews and uploads the final framed AI image to Drive."
gh pr merge --merge
```

Esperado: el pull request se integra a `main`.

- [ ] **Step 5: Desplegar y probar**

En el VPS:

```bash
git pull
docker compose up -d --build
```

Instala el APK nuevo y verifica los dos caminos de entrega: Drive con una foto IA enmarcada y collage clásico.
