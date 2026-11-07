# Selector visual de modo y resultado de Drive

## Objetivo

Hacer evidente, desde el primer toque, la diferencia entre Foto IA y Collage clásico. La foto final que se entrega por Google Drive debe llevar el mismo marco personalizado de Syscom que se ve en pantalla y en el collage.

## Selector de modo

La pantalla tendrá dos tarjetas grandes apiladas, en formato vertical y con toda la tarjeta como zona táctil.

### Foto IA

- Se muestra primero.
- Lleva un preview estático 2×3 del mismo retrato, transformado en Ghibli, Pixar, anime, cómic, Simpsons y LEGO.
- Título: `Foto IA`.
- Texto: `Tu foto, muchos estilos`.
- La cuadrícula se genera una sola vez durante el desarrollo a partir de un retrato permitido de Unsplash y queda empaquetada como assets WebP. La cabina no hace llamadas de IA ni incurre en costo para mostrarla.

### Collage clásico

- Se muestra debajo.
- Lleva un preview visual de tres fotos en una tira personalizada con fondo y marco Syscom.
- Título: `Collage clásico`.
- Texto: `3 fotos para llevar`.
- El preview se construye con los recursos y el compositor ya presentes en la app, sin requerir red.

El encabezado se reduce a marca y una instrucción corta. Se eliminan los subtítulos descriptivos actuales porque las imágenes explican ambos recorridos.

## Resultado guardado en Drive

Al terminar Foto IA, la imagen escrita en el directorio de la sesión y subida a Drive será el bitmap completo que genera `PhotoFrameComposer` con `previewScale = false`. El bitmap con `previewScale = true` queda exclusivamente para la interfaz local.

Esto garantiza que QR, Drive, Quick Share y guardado local apunten a la misma imagen final con el marco, marca y personalización del evento.

## Validación

- En pantalla vertical, las dos tarjetas no deben requerir scroll y cada una debe tener una altura táctil amplia.
- El preview IA debe mostrar claramente seis estilos sin texto superpuesto.
- El preview de collage debe mostrar una tira de tres fotos con la identidad Syscom.
- Una generación IA entregada por Drive debe conservar exactamente el marco seleccionado y la personalización visible en el paso final.
- Compilar `:app:compileDebugKotlin`; comprobar los flujos de Foto IA y collage.
