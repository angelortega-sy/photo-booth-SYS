# Generador de fondos (offline, una sola vez)

Genera los fondos del kiosko con la **Image API de OpenRouter** y los deja en `out/`.
La app **nunca** llama a OpenRouter: esto es contenido, no runtime.

## Requisitos

- Node 18 o superior (usa `fetch` global, sin dependencias).
- Una API key de OpenRouter con créditos.

## Configurar la key

```powershell
cd tools/generate-backgrounds
copy .env.example .env
# edita .env y pega tu OPENROUTER_API_KEY
```

También puedes dejar el `.env` en la raíz del repo: el script busca primero en
`tools/generate-backgrounds/.env` y luego en la raíz. `.env` está en el `.gitignore`
del repo. No lo subas y no lo metas en el APK.

## Uso

```powershell
# Ver qué se enviaría, sin gastar
node generate.mjs --dry-run

# Generar todo en vertical 9:16 (omite los que ya existen)
node generate.mjs --aspect-ratio 9:16 --resolution 2K

# Solo algunos temas
node generate.mjs --only gala,neon --aspect-ratio 9:16

# Regenerar aunque existan
node generate.mjs --force --only gala

# Elegir modelo
node generate.mjs --model x-ai/grok-imagine-image-2.0
```

Salida: `tools/generate-backgrounds/out/<tema>.png` + `manifest.json` con prompt, modelo y costo.
El app es vertical, así que el pack aprobado se genera en `9:16` (Grok devuelve 1584×2816).

## Modelos y costo aproximado (2026-11-03)

| Modelo | Costo por imagen | Nota |
|---|---|---|
| `black-forest-labs/flux-3-image-klein` | ~$0.008–0.036 | Default; el más barato |
| `x-ai/grok-imagine-image-2.0` | ~$0.04–0.08 | El que mencionaste; más caro |
| `google/gemini-3.1-flash-lite-image` | ~$0.00003/token | Buena adherencia al prompt |
| `sourceful/riverflow-v2.6-pro` | ~$0.13–0.17 | Evitar para lotes grandes |

Los precios cambian; verifica con:

```powershell
curl.exe -s "https://openrouter.ai/api/v1/images/models/black-forest-labs/flux-3-image-klein/endpoints"
```

## Pasar los fondos aprobados a Android

El app carga `app/src/main/assets/backgrounds/<tema>.webp` a 1080×1920. Antes de copiar,
redimensiona el PNG original a esa resolución y guárdalo como WebP (calidad ~88):

```powershell
python -c "from PIL import Image; im=Image.open('out/grok-pack-portrait/gala.png'); im=im.resize((1080,1920), Image.LANCZOS); im.save('app/src/main/assets/backgrounds/gala.webp', quality=88, method=6)"
```

Repite para `neon`, `playa`, `estudio`, `retro80s` y `jardin`. Solo copia las imágenes
revisadas: sin texto, sin logos, centro libre y caras de fondo no reconocibles.
