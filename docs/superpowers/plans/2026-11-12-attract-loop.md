# Attract Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un video vertical de ~25 s que se reproduce encima de la bienvenida cuando la cabina está sola, y que un toque quite para dejar la bienvenida de siempre.

**Architecture:** Tres clips generados por separado con `bytedance/seedance-2.0` en modo reference-to-video (hoja de personaje + tótem), unidos por destellos a blanco resueltos en edición; se ensamblan en un solo MP4 de 1080×1920 que la app reproduce con `MediaPlayer` + `SurfaceView`, sin dependencias nuevas. La capa `AttractOverlay` vive dentro del `Box` raíz de `PhotoBoothApp` y se enciende a los 25 s de inactividad en la bienvenida.

**Tech Stack:** Kotlin + Jetpack Compose (app existente), Node ESM (script de generación), ffmpeg (ensamblado), OpenRouter Videos API.

**Spec:** `docs/superpowers/specs/2026-11-12-attract-loop-design.md`

## Global Constraints

- Salida exacta **1080×1920** (mitad de 2160×3840). Sin recortes ni barras.
- **Cero texto, logos o marcas de agua** en el cuadro generado.
- El monito es el mismo en todos los clips: cabeza esférica blanca, ojos negros ovalados, sonrisa, manoplas, pies. Nunca se vuelve humano.
- El tótem es el mismo objeto: marco negro delgado, pantalla `#0B63CE` con icono de cámara blanco, webcam arriba, base.
- Todo corte ocurre dentro de un blanco `#FFFFFF` puro (no gris).
- `generate_audio: false` en todas las generaciones. El MP4 final va sin pista de audio (`-an`).
- MP4 ≤ 12 MB, H.264, `yuv420p`, `+faststart`.
- Todo lo intermedio vive en `out/attract-v4/` (gitignored). Solo el MP4 final entra a `app/src/main/assets/attract/`.
- La API key se lee de `tools/generate-backgrounds/.env`. Nunca se imprime ni se escribe en `out/` ni en el repo.

## Review Focus

- **El invitado toca el video y arranca una sesión sin querer** (doble toque sobre el CTA de la bienvenida) → ventana de gracia de 400 ms, con test de la función de estado.
- **El video aparece a media sesión o encima del admin** → la condición de visibilidad incluye `showWelcome && !showAdmin`, con test.
- **El temporizador de 60 s corre con el video visible** y reinicia la bienvenida (parpadeo) → el loop de reposo se salta mientras `attractVisible`.
- **El MP4 no existe o está corrupto** → la app se comporta como hoy; nunca crash, nunca pantalla negra bloqueante.
- **El reproductor queda vivo** al entrar a admin o a una sesión → `DisposableEffect` lo libera; un solo player en memoria.

---

### Task 1: Referencias visuales

**Files:**
- Create: `out/attract-v4/refs/mascot-sheet.png`
- Create: `out/attract-v4/refs/totem.png`
- Create: `out/attract-v4/refs/white.png`
- Create: `out/attract-v4/README.md`

**Interfaces:**
- Produces: tres PNG en `out/attract-v4/refs/` que las Tasks 3-5 mandan como `input_references`.

- [ ] **Step 1: Crear la carpeta y el README**

`out/attract-v4/README.md` lista clip por clip qué se generó, con duración, seed, costo y veredicto (`usar` / `rehacer`). Es el registro de producción, no documentación.

- [ ] **Step 2: Generar `mascot-sheet.png`**

Hoja de personaje, fondo blanco puro, tres vistas del mismo monito (frente, 3/4, perfil): cabeza esférica blanca con acabado mate suave, dos ojos negros ovalados, sonrisa curva fina, cuerpo redondo y pequeño, manoplas sin dedos, piernas cortas, pies redondos. Sin texto. Sin sombras duras.

- [ ] **Step 3: Generar `totem.png`**

Tótem de frente sobre blanco, 9:16: marco negro delgado, pantalla azul `#0B63CE` con icono de cámara blanco de línea, webcam pequeña negra en el borde superior, base negra ancha. Sin texto, sin botones, sin teclado.

- [ ] **Step 4: Generar `white.png` con ffmpeg (determinista, no con IA)**

```powershell
ffmpeg -f lavfi -i color=c=white:s=1080x1920 -frames:v 1 out/attract-v4/refs/white.png
```

- [ ] **Step 5: Verificar**

`white.png` debe medir exactamente 1080×1920 y ser `#FFFFFF` puro (comprobar el pixel 0,0). Las otras dos, fondo blanco sin texto.

- [ ] **Step 6: Mostrar al usuario y esperar su ok antes de gastar en video**

---

### Task 2: Script de generación

**Files:**
- Create: `tools/attract-video/generate.mjs`
- Create: `tools/attract-video/README.md`

**Interfaces:**
- Produces: `node tools/attract-video/generate.mjs --name <slug> --prompt-file <path> [--duration 8] [--ref <png> ...] [--seed 123] [--dry-run]`, que escribe `out/attract-v4/clips/<slug>.mp4` y `out/attract-v4/clips/<slug>.json` con `{ model, prompt, duration, seed, cost_usd, generated_at, job_id }`.

- [ ] **Step 1: Implementar el script**

- Lee `OPENROUTER_API_KEY` de `process.env`; si falta, la carga de `tools/generate-backgrounds/.env` (parseo línea a línea de `KEY=VALUE`, sin dependencias).
- `POST https://openrouter.ai/api/v1/videos` con `{ model: "bytedance/seedance-2.0", prompt, size: "1080x1920", duration, generate_audio: false, seed, input_references: [...] }`. Cada referencia va como `{ type: "image_url", image_url: { url: "data:image/png;base64,..." } }`.
- Poll a `polling_url` cada 15 s, imprime `status` en cada vuelta; corta en `completed` o `failed` (con el `error` del cuerpo).
- Descarga `unsigned_urls[0]` con el header `Authorization`.
- `--dry-run` imprime el JSON del cuerpo sin `prompt` recortado y sin base64 (longitud en su lugar) y no hace red.

- [ ] **Step 2: Verificar con dry-run**

```powershell
node tools/attract-video/generate.mjs --name test --prompt-file out/attract-v4/prompts/a.txt --ref out/attract-v4/refs/white.png --duration 4 --dry-run
```

Esperado: JSON con `size: "1080x1920"`, `generate_audio: false`, y `input_references[0].image_url.url` mostrado como `data:image/png;base64,<N chars>`.

- [ ] **Step 3: Commit**

```bash
git add tools/attract-video
git commit -m "chore(tools): add seedance video generation script"
```

---

### Task 3: Clip A — llegada

**Files:**
- Create: `out/attract-v4/prompts/a.txt`
- Create: `out/attract-v4/clips/a.mp4`

**Interfaces:**
- Consumes: `mascot-sheet.png`, `totem.png` (Task 1), `generate.mjs` (Task 2).

- [ ] **Step 1: Escribir el prompt**

Vertical 9:16, 24 fps, animación 3D limpia, luz suave de estudio, calidad de anuncio. Estudio blanco infinito sin costuras. El monito de la referencia entra caminando desde la izquierda con dos pasos botando, se detiene frente al tótem de la referencia, mira la pantalla hacia arriba, apoya la mano plana en el cristal, la pantalla pulsa en azul y cambia, el monito se pone en pose orgullosa con una mano en la cadera y luego un destello blanco llena el cuadro. Sin texto, sin logos, sin marcas de agua. La cámara no se mueve.

- [ ] **Step 2: Generar**

```powershell
node tools/attract-video/generate.mjs --name a --prompt-file out/attract-v4/prompts/a.txt --ref out/attract-v4/refs/mascot-sheet.png --ref out/attract-v4/refs/totem.png --duration 8 --seed 4211
```

- [ ] **Step 3: Verificar el resultado**

Hoja de contactos de 8 frames (`ffmpeg -i a.mp4 -vf "fps=1,scale=270:-1,tile=4x2" ...`). Revisar: mismo monito que la referencia, mismo tótem, sin texto deformado, el último segundo es blanco pleno. Anotar costo en `out/attract-v4/README.md`.

- [ ] **Step 4: Mostrar al usuario**

Es el clip de prueba de costo y calidad. Sin su ok no se generan los otros seis.

---

### Task 4: Clip C — cierre

**Files:**
- Create: `out/attract-v4/prompts/c.txt`
- Create: `out/attract-v4/clips/c.mp4`

- [ ] **Step 1: Escribir el prompt**

Mismo estudio blanco, misma luz, mismo encuadre que el clip A. El monito de la referencia está de pie junto al tótem de la referencia, mira la pantalla, luego se voltea hacia la cámara y sonríe con calma. Respiración sutil, sin caminar. Sin texto, sin logos. Cámara fija.

- [ ] **Step 2: Generar**

```powershell
node tools/attract-video/generate.mjs --name c --prompt-file out/attract-v4/prompts/c.txt --ref out/attract-v4/refs/mascot-sheet.png --ref out/attract-v4/refs/totem.png --duration 6 --seed 4212
```

Se genera **antes** que los beats B: fija el estudio y la pose final que el invitado va a ver durante minutos.

- [ ] **Step 3: Verificar y mostrar al usuario**



---

### Task 5: Beats B1-B5 — estilos

**Files:**
- Create: `out/attract-v4/prompts/b1.txt` … `b5.txt`
- Create: `out/attract-v4/clips/b1.mp4` … `b5.mp4`

- [ ] **Step 1: Prompt por beat**

Cada uno: 9:16, el mismo monito de la referencia vestido para ese mundo (nunca convertido en humano), el mismo tótem de la referencia integrado al estilo del mundo, mira alrededor asombrado, toca la pantalla, destello blanco final. Sin texto.

| Beat | Mundo | Vestuario | Accesorios del catálogo |
|---|---|---|---|
| b1 | Bosque pixel art al atardecer | Explorador, mochila | — |
| b2 | Oficina noir B/N, persianas | Traje, moño, fedora, bigote fino | `mustache` |
| b3 | Cabaña navideña con nieve | Abrigo, gorro rojo, bufanda | `santa_hat`, `scarf` |
| b4 | Patio con sakura, golden hour | Cel-shading, kimono | `flower_crown` |
| b5 | Callejón neón | Chaqueta con luces, gafas | `sunglasses` |

- [ ] **Step 2: Generar los cinco (4 s cada uno, seeds 4221-4225)**

```powershell
node tools/attract-video/generate.mjs --name b1 --prompt-file out/attract-v4/prompts/b1.txt --ref out/attract-v4/refs/mascot-sheet.png --ref out/attract-v4/refs/totem.png --duration 4 --seed 4221
```

- [ ] **Step 3: Verificar cada uno**

Hoja de contactos por beat. Rechazar si el monito deja de ser el monito, si el tótem cambió de diseño o si aparecieron letras. Regenerar solo el beat rechazado.

---

### Task 6: Ensamblado

**Files:**
- Create: `out/attract-v4/build/` (cortes intermedios)
- Create: `app/src/main/assets/attract/attract-loop.mp4`

**Interfaces:**
- Consumes: `a.mp4`, `c.mp4`, `b1..b5.mp4`.
- Produces: el único asset que consume la app.

- [ ] **Step 1: Recortar y normalizar**

Cada beat a 2.5 s, A a 7 s, C a 5 s. Todos a 24 fps, 1080×1920, sin audio, con 4 frames de rampa a blanco en cada extremo (el corte cae dentro del blanco).

- [ ] **Step 2: Anclar negro/blanco**

Forzar el último frame de cada segmento a `#FFFFFF` puro y el primero también, para que la unión no muestre un gris distinto.

- [ ] **Step 3: Concatenar y exportar**

H.264 High, `yuv420p`, CRF 21, `-an`, `-movflags +faststart`, 1080×1920.

- [ ] **Step 4: Verificar**

- Duración 24.5 s ±0.3.
- `ffprobe` reporta un solo stream de video, 1080×1920, sin audio.
- Peso ≤ 12 MB.
- Reproducir con un player local y mirar las siete uniones: ninguna debe mostrar salto.

---

### Task 7: Lógica de visibilidad (test primero)

**Files:**
- Create: `app/src/main/java/com/photobooth/kiosk/ui/attract/AttractState.kt`
- Test: `app/src/test/java/com/photobooth/kiosk/ui/attract/AttractStateTest.kt`

**Interfaces:**
- Produces: `internal fun shouldShowAttract(showWelcome: Boolean, showAdmin: Boolean, hasSession: Boolean, idleMs: Long): Boolean` y `internal const val ATTRACT_DELAY_MS: Long = 25_000L`.

- [ ] **Step 1: Escribir el test**

```kotlin
@Test fun `welcome idle for the delay shows the attract`() =
    assertTrue(shouldShowAttract(true, false, false, ATTRACT_DELAY_MS))

@Test fun `welcome before the delay does not`() =
    assertFalse(shouldShowAttract(true, false, false, ATTRACT_DELAY_MS - 1))

@Test fun `admin never shows the attract`() =
    assertFalse(shouldShowAttract(true, true, false, 10 * ATTRACT_DELAY_MS))

@Test fun `an active session never shows the attract`() =
    assertFalse(shouldShowAttract(false, false, true, 10 * ATTRACT_DELAY_MS))
```

- [ ] **Step 2: Correr y ver fallar**

`.\gradlew.bat testDebugUnitTest --tests "*AttractStateTest*"` → FAIL por función no definida.

- [ ] **Step 3: Implementar**

`showWelcome && !showAdmin && !hasSession && idleMs >= ATTRACT_DELAY_MS`.

- [ ] **Step 4: Correr y ver pasar**

- [ ] **Step 5: Commit**

---

### Task 8: AttractOverlay

**Files:**
- Create: `app/src/main/java/com/photobooth/kiosk/ui/attract/AttractOverlay.kt`
- Modify: `app/src/main/java/com/photobooth/kiosk/ui/PhotoBoothApp.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Consumes: `shouldShowAttract`, `ATTRACT_DELAY_MS`, `assets/attract/attract-loop.mp4`.
- Produces: `@Composable fun AttractOverlay(visible: Boolean, onDismiss: () -> Unit)`.

- [ ] **Step 1: Strings**

`attract_slogan` = "Transfórmate en lo que tú más quieras" / "Become whatever you want most"; `attract_cta` = "Toca para empezar" / "Tap to start". Mismas claves en ambos archivos.

- [ ] **Step 2: Implementar el composable**

`Box` a pantalla completa sobre `Ink`. `AndroidView` con `SurfaceView` + `MediaPlayer` creado una vez, `setVideoScalingMode(VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)`, `assets/attract/attract-loop.mp4` preparado en pausa y `seekTo(0)+start()` al hacerse visible. `OnCompletionListener` enciende el texto (slogan + pastilla) con `AnimatedVisibility`. `DisposableEffect` libera el player. `clickable` que consume el toque y llama `onDismiss`.

- [ ] **Step 3: Cablearlo en `PhotoBoothApp`**

`attractVisible` se calcula con `shouldShowAttract(...)` sobre `lastInteraction`. Al quitarse, `lastInteraction = SystemClock.uptimeMillis()` y se ignoran toques 400 ms. Mientras es visible: `showIdleTimer = false` y el loop de reposo no corre.

- [ ] **Step 4: Compilar**

`.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

---

### Task 9: Prueba en dispositivo

- [ ] **Step 1: Instalar y dejar la cabina quieta 25 s** — el video aparece sin frame negro.
- [ ] **Step 2: Un toque** — se ve la bienvenida de siempre, sin sesión arrancada.
- [ ] **Step 3: Dejar 90 s tras el final** — el video se repite.
- [ ] **Step 4: Long-press en SYSCOM** — el admin abre y el video no vuelve.
- [ ] **Step 5: Correr una sesión completa** — el video no aparece durante el flujo.
