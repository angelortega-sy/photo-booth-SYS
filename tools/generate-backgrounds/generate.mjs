#!/usr/bin/env node
/**
 * Genera fondos para el photo booth con la Image API de OpenRouter.
 * Requiere Node 18+ (fetch global). Sin dependencias externas.
 *
 * Uso: node generate.mjs [--dry-run] [--only gala,neon] [--model <slug>]
 */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const API_URL = "https://openrouter.ai/api/v1/images";
const DEFAULT_MODEL = "black-forest-labs/flux-3-image-klein";
const EXTENSIONS = ["png", "jpg", "jpeg", "webp"];

function fail(message) {
  console.error(`\n✖ ${message}\n`);
  process.exit(1);
}

function printHelp() {
  console.log(`
Genera fondos del photo booth con OpenRouter.

  node generate.mjs [opciones]

Opciones:
  --model <slug>        Modelo de imagen (default: ${DEFAULT_MODEL})
  --only <ids>          Temas separados por coma: gala,neon,playa,...
  --prompt <texto>      Reemplaza el prompt de todos los temas
  --prompt-file <path>  Igual, pero lee el prompt de un archivo
  --out <dir>           Carpeta de salida (default: ./out)
  --force               Regenera aunque el archivo exista
  --dry-run             Muestra las peticiones sin llamar a la API
  --concurrency <n>     Peticiones en paralelo (default: 1)
  --aspect-ratio <r>    Default: 16:9 (auto para omitir)
  --resolution <r>      Default: 2K (auto para omitir)
  --format <fmt>        Default: png (png|jpeg|webp|auto)
  --seed <n>            Seed global (los temas pueden fijar el suyo)
  --help                Esta ayuda
`);
}

function parseArgs(argv) {
  const args = {
    concurrency: 1,
    out: path.join(__dirname, "out"),
    force: false,
    dryRun: false,
    aspectRatio: "16:9",
    resolution: "2K",
    format: "png",
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) fail(`Falta el valor de ${arg}`);
      return argv[++i];
    };

    switch (arg) {
      case "--model":
        args.model = next();
        break;
      case "--only":
        args.only = next().split(",").map((s) => s.trim()).filter(Boolean);
        break;
      case "--prompt":
        args.prompt = next();
        break;
      case "--prompt-file":
        args.promptFile = path.resolve(next());
        break;
      case "--out":
        args.out = path.resolve(next());
        break;
      case "--force":
        args.force = true;
        break;
      case "--dry-run":
        args.dryRun = true;
        break;
      case "--concurrency":
        args.concurrency = Math.max(1, Number.parseInt(next(), 10) || 1);
        break;
      case "--aspect-ratio":
        args.aspectRatio = next();
        break;
      case "--resolution":
        args.resolution = next();
        break;
      case "--format":
        args.format = next();
        break;
      case "--seed":
        args.seed = Number.parseInt(next(), 10);
        break;
      case "--help":
      case "-h":
        args.help = true;
        break;
      default:
        fail(`Argumento desconocido: ${arg}. Usa --help.`);
    }
  }

  return args;
}

/** Lee .env sin dependencias; no sobreescribe variables ya definidas. */
async function loadEnv() {
  const candidates = [
    path.join(__dirname, ".env"),
    path.join(__dirname, "..", "..", ".env"),
  ];

  for (const envPath of candidates) {
    if (!existsSync(envPath)) continue;

    const raw = await readFile(envPath, "utf8");
    for (const line of raw.split(/\r?\n/)) {
      const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
      if (!match) continue;

      const key = match[1];
      let value = match[2].trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      if (process.env[key] === undefined) process.env[key] = value;
    }
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

class HttpError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}: ${body}`);
    this.status = status;
  }
}

function extensionFromMediaType(mediaType) {
  if (!mediaType) return null;
  if (mediaType.includes("png")) return "png";
  if (mediaType.includes("jpeg") || mediaType.includes("jpg")) return "jpg";
  if (mediaType.includes("webp")) return "webp";
  return null;
}

async function findExisting(outDir, id) {
  for (const ext of EXTENSIONS) {
    const candidate = path.join(outDir, `${id}.${ext}`);
    if (existsSync(candidate)) return candidate;
  }
  return null;
}

async function withRetry(fn, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err;
      const retryable =
        !(err instanceof HttpError) || err.status === 429 || err.status >= 500;
      if (!retryable || attempt === attempts) break;

      const waitMs = attempt * 5000;
      console.warn(
        `  ↻ Reintento ${attempt}/${attempts - 1} en ${waitMs / 1000}s (${String(err.message).slice(0, 120)})`
      );
      await sleep(waitMs);
    }
  }
  throw lastError;
}

async function requestImage({ model, prompt, aspectRatio, resolution, format, seed, apiKey }) {
  const body = { model, prompt, n: 1 };
  if (aspectRatio && aspectRatio !== "auto") body.aspect_ratio = aspectRatio;
  if (resolution && resolution !== "auto") body.resolution = resolution;
  if (format && format !== "auto") body.output_format = format;
  if (seed !== undefined && seed !== null) body.seed = seed;

  const response = await fetch(API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-OpenRouter-Title": "photo-booth-backgrounds",
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    const text = await response.text();
    const hint =
      response.status === 402
        ? " La cuenta de OpenRouter no tiene créditos (o la key es de otra cuenta)."
        : "";
    throw new HttpError(response.status, text.slice(0, 800) + hint);
  }

  return { json: await response.json(), body };
}

async function main() {
  if (typeof fetch !== "function") {
    fail("Se requiere Node 18 o superior (fetch global).");
  }

  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }

  await loadEnv();

  const apiKey = process.env.OPENROUTER_API_KEY;
  if (!apiKey) {
    fail("Falta OPENROUTER_API_KEY. Copia .env.example a .env y pega la key de la empresa.");
  }

  const model = args.model || process.env.OPENROUTER_MODEL || DEFAULT_MODEL;
  const themesPath = path.join(__dirname, "themes.json");
  const allThemes = JSON.parse(await readFile(themesPath, "utf8"));
  const themes = args.only
    ? allThemes.filter((theme) => args.only.includes(theme.id))
    : allThemes;

  if (themes.length === 0) {
    fail(`Ningún tema coincide con --only ${args.only.join(",")}`);
  }

  if (args.prompt && args.promptFile) {
    fail("Usa --prompt o --prompt-file, no ambos.");
  }
  const customPrompt = args.promptFile
    ? (await readFile(args.promptFile, "utf8")).trim()
    : args.prompt;
  if (customPrompt) {
    for (const theme of themes) theme.prompt = customPrompt;
  }

  await mkdir(args.out, { recursive: true });

  console.log(`Modelo:  ${model}`);
  console.log(`Salida:  ${args.out}`);
  console.log(`Temas:   ${themes.map((theme) => theme.id).join(", ")}`);
  console.log(`Formato: ${args.aspectRatio} · ${args.resolution} · ${args.format}`);
  if (args.dryRun) console.log("Modo dry-run: no se llama a la API.");
  console.log("");

  const results = [];
  let cursor = 0;

  async function worker() {
    while (true) {
      const index = cursor++;
      if (index >= themes.length) return;

      const theme = themes[index];
      const existing = await findExisting(args.out, theme.id);

      if (existing && !args.force) {
        console.log(`- ${theme.id}: ya existe (${path.basename(existing)}), se omite`);
        results.push({ theme: theme.id, status: "skipped", file: existing });
        continue;
      }

      const aspectRatio = theme.aspect_ratio || args.aspectRatio;
      const resolution = theme.resolution || args.resolution;
      const seed = theme.seed ?? args.seed;

      if (args.dryRun) {
        console.log(
          `- ${theme.id}: POST ${API_URL} { model: "${model}", aspect_ratio: "${aspectRatio}", resolution: "${resolution}", output_format: "${args.format}"${seed !== undefined ? `, seed: ${seed}` : ""} }`
        );
        results.push({ theme: theme.id, status: "dry-run" });
        continue;
      }

      console.log(`- ${theme.id}: generando…`);
      try {
        const { json, body } = await withRetry(() =>
          requestImage({
            model,
            prompt: theme.prompt,
            aspectRatio,
            resolution,
            format: args.format,
            seed,
            apiKey,
          })
        );

        const image = json?.data?.[0];
        if (!image?.b64_json) {
          throw new Error(
            `La respuesta no trae data[0].b64_json: ${JSON.stringify(json).slice(0, 400)}`
          );
        }

        const ext = extensionFromMediaType(image.media_type) || (args.format === "auto" ? "png" : args.format);
        const file = path.join(args.out, `${theme.id}.${ext}`);
        await writeFile(file, Buffer.from(image.b64_json, "base64"));

        const cost = json?.usage?.cost;
        console.log(
          `  ✓ ${path.basename(file)}${cost != null ? ` ($${Number(cost).toFixed(4)})` : ""}`
        );

        results.push({
          theme: theme.id,
          status: "ok",
          file,
          cost: cost ?? null,
          body,
          mediaType: image.media_type,
          model,
          prompt: theme.prompt,
          seed: seed ?? null,
        });
      } catch (err) {
        console.error(`  ✖ ${theme.id}: ${err.message}`);
        results.push({ theme: theme.id, status: "failed", error: err.message });
      }
    }
  }

  const workers = Math.min(args.concurrency, themes.length);
  await Promise.all(Array.from({ length: workers }, () => worker()));

  const ok = results.filter((r) => r.status === "ok");
  const failed = results.filter((r) => r.status === "failed");
  const skipped = results.filter((r) => r.status === "skipped");

  if (ok.length > 0) {
    const manifestPath = path.join(args.out, "manifest.json");
    let manifest = [];
    if (existsSync(manifestPath)) {
      try {
        manifest = JSON.parse(await readFile(manifestPath, "utf8"));
      } catch {
        manifest = [];
      }
    }

    const generatedFiles = new Set(ok.map((r) => path.basename(r.file)));
    manifest = manifest.filter((entry) => !generatedFiles.has(entry.file));

    const generatedAt = new Date().toISOString();
    for (const result of ok) {
      manifest.push({
        theme: result.theme,
        file: path.basename(result.file),
        model: result.model,
        prompt: result.prompt,
        aspect_ratio: result.body.aspect_ratio,
        resolution: result.body.resolution,
        seed: result.seed,
        media_type: result.mediaType,
        cost_usd: result.cost,
        generated_at: generatedAt,
      });
    }

    await writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
  }

  const totalCost = ok.reduce((sum, r) => sum + (r.cost || 0), 0);
  console.log(
    `\nResumen: ${ok.length} generadas, ${skipped.length} omitidas, ${failed.length} fallidas` +
      (totalCost > 0 ? ` · costo ~$${totalCost.toFixed(4)}` : "")
  );

  if (failed.length > 0) process.exitCode = 1;
}

main().catch((err) => fail(err.stack || err.message));
