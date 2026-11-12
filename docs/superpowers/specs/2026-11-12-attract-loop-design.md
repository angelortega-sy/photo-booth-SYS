# Video de atracción (attract loop) — diseño

**Fecha:** 2026-11-12 · **Estado:** propuesto · **Aplica a:** `ui/PhotoBoothApp.kt`, `assets/attract/`, `out/attract/`

## 1. Qué es y por qué

Un video vertical a pantalla completa que se reproduce **encima** de `WelcomeScreen` cuando la cabina lleva un rato sola. Un toque en cualquier parte lo quita y deja la bienvenida de hoy tal cual, sin video.

Hoy la bienvenida es estática: un titular, tres pasos flotantes y un botón. A 5-10 m en una expo ruidosa eso no voltea cabezas. El video muestra lo que la cabina realmente hace — convertirte en otra versión de ti — en 25 s, sin audio y sin leer nada.

Es además la vitrina del catálogo: los cinco mundos del video son cinco fondos que ya existen en `assets/backgrounds/`, así que lo que el invitado ve en la pantalla lo puede obtener en su foto.

## 2. Éxito y fracaso

**Funciona si:**

- Se entiende sin audio. Alguien a 8 m mira y sonríe.
- El monito se reconoce como el mismo personaje en los cinco estilos y el tótem nunca cambia de diseño.
- El texto se lee a 5 m y cambia solo, en ES y EN.
- Rehacer un beat cuesta céntimos y no obliga a regenerar el resto.
- Pesa ≤ 12 MB, arranca sin frame negro y aguanta 8 h de turno sin fugas de memoria.

**No es el objetivo:** audio, un video por marca, más de un video, impresión.

**Fracasa si:** el monito se vuelve humano (le pasó a `out/attract-v3` en el beat anime), el tótem cambia de diseño entre clips, el texto va quemado en el MP4, el video tapa el admin para siempre, o el invitado tiene que tocar tres veces para llegar a la cámara.

## 3. Restricciones

- Panel 2160×3840 en vertical → **1080×1920 es exactamente la mitad**: escala entera, sin blur.
- 4 GB RAM, 32 GB eMMC. APK actual ~97 MB.
- El video es local. No hay red, ni CDN, ni descarga en runtime.
- No hay reproductor hoy: `app/build.gradle.kts` no tiene Media3. Es la única dependencia nueva.
- **Cero texto generado.** La app es bilingüe y la marca es editable en Admin → Marcas (SYSCOM es solo el perfil por defecto). Todo lo tipográfico lo dibuja Compose.
- Paleta de la app: `Ink #050B14`, `InkSoft #0B1727`, `SyscomBlue #0B63CE`, `SyscomBlueLight #5EA8FF`, `Cream #F3F8FF`.
- La API de OpenRouter no entra al APK. El video se genera fuera del repo, a mano, y solo el MP4 final se copia a `assets/`.

## 4. Enfoques considerados

**A. Un solo plano de 15-25 s** (es lo que hay en `out/attract-v3`, `google/gemini-omni-1.2-flash`, 15 s, $1.56). Se ve bien de lejos y aun así falla por estructura: con dos o más cambios de estilo en una sola generación el modelo colapsa al estilo que ya estableció (el beat anime regresó al bosque pixel art), el personaje deriva (el monito se volvió un adolescente humano en el beat anime) y el tótem se reinventó con botones de arcade. Y no se puede arreglar un beat: se regenera todo.

**B. Tres clips con el destello blanco como costura** — **elegido**. Cada corte ocurre dentro de un destello a blanco pleno, así que la continuidad entre clips no depende de que el modelo acierte: el blanco la garantiza. Rehacer un beat cuesta $0.05-0.10.

**C. Todo procedural en Compose** (gradientes, logo animado, sin video). Descartado: no vende "transfórmate", y modelar un personaje 3D creíble en Compose cuesta más que el video y se ve peor.

## 5. La película

### Reglas duras

1. **El monito es el mismo en todos los clips:** cabeza esférica blanca, ojos negros ovalados, sonrisa curva, cuerpo redondo, manoplas, pies. Se **viste**, nunca se transforma en persona.
2. **El tótem es el mismo objeto:** marco negro delgado, pantalla azul `#0B63CE` con icono de cámara blanco, webcam arriba, base. Sin botones, sin panel arcade, sin teclado.
3. Cero texto, logos, marcas de agua o números en el cuadro.
4. Todo corte ocurre dentro de un destello a blanco pleno (`#FFFFFF`, no gris).
5. Fondo del estudio blanco puro: es la fuente del destello y es la cosa más brillante de la pared.
6. Cada clip termina en blanco pleno y el siguiente empieza en blanco pleno.

### Estructura — ~25 s

| # | Tiempo | Mundo | Look del monito | Acción |
|---|---|---|---|---|
| **A** | 0:00-0:07 | Estudio blanco | Liso, blanco mate | Entra con dos pasos botando, mira el tótem, apoya la mano en la pantalla, la pantalla pulsa y cambia, pose orgulloso → destello |
| **B1** | 0:07-0:09.5 | Bosque pixel art, atardecer (`pixel.webp`) | Sprite pixel art, sombrero de explorador, mochila | Mira alrededor asombrado, toca el tótem → destello |
| **B2** | 0:09.5-0:12 | Oficina noir, B/N, persianas (`noir_office.webp`) | Traje, moño, fedora, bigote fino | Ajusta el ala del sombrero, toca con un dedo → destello |
| **B3** | 0:12-0:14.5 | Cabaña navideña con nieve (`xmas_cabin.webp`) | Gorro rojo con pompón, bufanda | Sopla copos, se sacude la nieve, toca → destello |
| **B4** | 0:14.5-0:17 | Patio con sakura, golden hour (`sakura.webp`) | Cel-shading, corona de flores, kimono | Pose confiada, señala la pantalla → destello |
| **B5** | 0:17-0:19.5 | Callejón neón (`neon_alley.webp`) | Chaqueta con luces, gafas | Se mira en el reflejo, toca → destello |
| **C** | 0:19.5-0:25 | Estudio blanco | Liso, blanco mate | Aparece junto al tótem, se voltea a cámara, sonríe. Sin texto |

Los accesorios salen del catálogo real (`AiAccessories`: `santa_hat`, `scarf`, `flower_crown`, `sunglasses`, `mustache`…), así que el video anuncia algo que el invitado puede pedir. El fedora de noir no existe como accesorio: es parte del vestuario del estilo.

### Por qué no hay loop

El video se reproduce una vez y se queda en el último frame. No necesita loop perfecto: el cierre es un póster con el CTA de la app encima, así que un frame quieto no se siente muerto. Esto elimina el problema de "la costura entre el final y el inicio".

## 6. Producción

**Modelo:** `bytedance/seedance-2.0`, 9:16, 1080p, un clip por beat. Se genera más largo de lo necesario (4-5 s) y se recorta, porque el timing fino se decide en edición y no en el prompt. Primer beat generado como prueba de costo antes de producir los siete.

**Hallazgo de API (OpenRouter, `POST /api/v1/videos`):** `frame_images` (primer/último frame) y `input_references` (referencias de personaje) **son mutuamente excluyentes**: si se mandan las dos, gana `frame_images` y las referencias se ignoran. Seedance 2.0 sí soporta ambas por separado y soporta `1080x1920` exacto (parámetro `size`) y duraciones de 4 a 15 s.

**Decisión:** se usa **reference-to-video** (`input_references`: hoja del monito + tótem limpio), no `frame_images`. La identidad del personaje es lo que se rompió en v3 y es lo que no se puede arreglar en edición; el blanco de los extremos sí se arregla en edición con un fundido de 4 frames. `generate_audio: false` siempre.

**Referencias (`out/attract/refs/`, gitignored):**

- `mascot-sheet.png`: el monito en tres vistas (frente, 3/4, perfil) sobre blanco, derivado de la foto de referencia del usuario. Es el ancla de identidad para todos los clips.
- `totem.png`: el tótem limpio, de frente, pantalla azul con cámara.
- `world-*.png`: cinco stills de los mundos, en 9:16, para arrancar cada beat con image-to-video.

**Por clip:** 4-5 s generados, recortados a 2.5 s en edición. Si el endpoint acepta `first_frame`/`last_frame`, se fuerza blanco en los extremos; si no, el blanco se resuelve en edición con un fundido de 4 frames.

**Ensamblado (ffmpeg):** H.264 High, `yuv420p`, 1080×1920, 24 fps, CRF 20-22, `-an`, `+faststart`. Destino: `app/src/main/assets/attract/attract-loop.mp4`.

**Revisión:** hoja de contactos de 8 frames por clip. Se revisa que no haya texto deformado, que el tótem no cambió y que el blanco final sea blanco puro.

## 7. La app

**Estado nuevo:** `attractVisible`, en `PhotoBoothApp`. Se enciende cuando `showWelcome && !showAdmin` y pasaron `ATTRACT_DELAY_MS = 25_000` sin interacción. Se apaga con cualquier toque, o al abrir admin o una sesión.

**Alcance:** solo la bienvenida. Quien toca "Comenzar" y se va queda en `ModeSelect` hasta que el temporizador de 60 s lo regresa a la bienvenida, y ahí arranca la cuenta de 25 s. `PermissionScreen` no lleva video: si falta el permiso de cámara, el video no es la respuesta.

**Render:** `AttractOverlay` dentro del `Box` raíz, después del `when`, encima de todo. `AnimatedVisibility`: fade-in 600 ms, fade-out 350 ms.

**Toque:** el primer toque solo quita el video. No debe atravesar al CTA de la bienvenida (`clickable` que consume el evento). Reinicia `lastInteraction` y abre una ventana de gracia de 400 ms en la que se ignoran toques: sin ella, el segundo toque del gesto cae sobre el CTA y arranca una sesión fantasma.

**Temporizador de reposo:** mientras el video está visible, `showIdleTimer = false` y el loop de 60 s no corre. Si no, a los 60 s llama a `endSession()`, la bienvenida se reinicia y el video parpadea.

**Reproducción:** `MediaPlayer` + `SurfaceView` vía `AndroidView`, **sin dependencias nuevas**. Un solo MP4 local a pantalla completa no necesita Media3, y el proyecto tiene versiones fijadas para `compileSdk 36`: agregar `media3` arriesga un salto de AGP/Gradle por cero beneficio. `setVideoScalingMode(SCALE_TO_FIT_WITH_CROPPING)` para llenar el panel sin barras, `setLooping(false)`. El player se prepara al entrar a la bienvenida con el video pausado; al mostrarse, `seekTo(0)` y play. Nunca arranca en frío, así no hay frame negro. `DisposableEffect` libera el player al salir de la bienvenida, al abrir admin o al iniciar sesión: un solo player en memoria. Si en el futuro hace falta más control, Media3 es el plan B.

**Repetición:** el video se reproduce una vez y se queda en el último frame. Si nadie toca durante **90 s**, se rebobina y vuelve a empezar con un fundido a blanco de 300 ms —usa el mismo lenguaje de destello del video— para que la pantalla no se quede congelada por horas (retención del panel y consumo).

**Texto encima:** cuando el player llega al final del video, la app dibuja con fade el slogan grande y la pastilla "Toca para empezar", con el mismo pulso del CTA de la bienvenida. Durante el video no hay texto: el video habla solo. Colores fijos Syscom, igual que `WelcomeScreen` (que tampoco se re-tiñe por marca): `Cream` sobre una sombra suave para que se lea sobre el estudio blanco, y la pastilla en `SyscomBlue`.

**Si el video falla:** el overlay simplemente no se muestra (o se muestra la bienvenida). Nunca crash, nunca pantalla negra bloqueante.

**Strings nuevas:** `attract_slogan` = "Transfórmate en lo que tú más quieras", `attract_cta` = "Toca para empezar", en `values/` y `values-en/`.

## 8. Orden de ejecución

1. Referencias: sheet del monito, tótem limpio, cinco mundos.
2. Clip A.
3. Clip C (fija el estudio y la pose final).
4. Los cinco beats de B.
5. Ensamblado y revisión.
6. Overlay en la app + Media3.
7. Prueba en el A55.
8. Prueba en el tótem de 55".

## 9. Decisiones para revisar

- El video aparece a los **25 s** de inactividad en la bienvenida (constante, no ajuste de admin por ahora).
- **Mudo.** Una cabina con audio en una expo es invasiva y el salón ya es ruidoso.
- **Estudio blanco**, aunque la bienvenida sea oscura: el contraste es el atractivo y el blanco es la fuente del destello. El tap hace un fade blanco → Ink de 500 ms, así el salto no se siente.
- **Sin texto en el MP4**, todo lo tipográfico lo dibuja la app.
- Long-press sobre el video **no** abre admin. El camino sigue siendo: toque → bienvenida → long-press sobre SYSCOM.
- Cinco beats de 2.5 s.
- Reproductor **sin dependencias nuevas** (`MediaPlayer` + `SurfaceView`), no Media3.
- Si a los 90 s nadie toca, el video se repite (anti-congelamiento).

## 10. Lo que queda al builder

- Milisegundos exactos de los fades y tamaño/tipografía del slogan (¿entra en `BoothDimens`?).
- Flags finales de ffmpeg y bitrate objetivo.
- Cómo se prepara el player para que el primer frame no parpadee en el arranque en frío.
- Si Seedance 2.0 acepta último frame; si no, plan B de fundido en edición.

---

## 11. Incremento 2026-11-12: píldora permanente y video elegible desde admin

Pedido del usuario tras ver el loop en el tótem: "para que no se vea como video" y "agregar en admin escoger los videos el tiempo de inactividad".

### 11.1 Texto durante todo el video

- La píldora `attract_cta` ("Toca para empezar") está visible **desde el primer segundo**, no solo al terminar.
- Estilo decidido: píldora abajo al centro, texto 22 sp, fondo negro al 35 %, esquinas 999 dp, opacidad respirando entre 0.55 y 0.85 cada ~3 s. Debe leerse sobre blanco, sobre bosque y sobre neón sin tapar al monito.
- Al terminar el video, la píldora se sustituye por el bloque de cierre de hoy (gradiente, eslogan 46 sp y CTA grande).
- No es un botón aparte: toda la capa ya es clickable; la píldora no intercepta nada.
- Sigue habiendo **cero texto dentro del MP4**: bilingüe y editable sin re-encodear.

### 11.2 Admin → Video promocional

- Tarjeta propia en el inicio del admin (no dentro de Sistema), con resumen: nombre del video activo y segundos de inactividad.
- Pantalla `AdminPromoScreen` con `AdminSettingsScreen` (borrador + "Guardar cambios"; no se guarda al tocar).
- Lista: videos **integrados** (`assets/attract/*.mp4`) + **importados** (`filesDir/attract/*.mp4`), cada fila con miniatura del primer frame (≈54×96 dp), nombre, duración, tamaño y origen. Selección única con radio.
- Importar con SAF (`OpenDocument("video/*")`): se **copia** a `filesDir/attract/promo_<timestamp>.mp4` (no se referencia el original, que puede moverse). Tope **50 MB**; si se pasa, mensaje de error y no se guarda.
- Borrar solo los importados. El video integrado no se puede borrar.
- Tiempo de inactividad: de **10 a 120 s en pasos de 5**, default 25.
- Persistencia en `SharedPreferences("attract_settings")` (`video_id`, `idle_ms`). El id es `asset:<archivo>` o `file:<archivo>`.
- Si el video elegido falta o no decodifica, el overlay se cierra solo y queda la bienvenida: nunca crash, nunca pantalla negra.

### 11.3 Por qué el selector

El otro agente produjo `out/attract-v5/attract-v5-30s-1080p.mp4` (9.3 MB, 30 s): un recorrido del producto real (toque → detección de caras → QR → resultado con accesorios) todo en el estudio blanco, sin montaje de estilos. Se evalúa en la expo. **Advertencia registrada:** el tótem de v5 no es el mismo modelo que el nuestro (más grueso, panel negro grande, pantalla con degradado); si gana, hay que alinear el aparato del video con el real.
