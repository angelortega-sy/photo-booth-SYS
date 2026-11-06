#!/usr/bin/env node
/**
 * Generates the style preview thumbnails used by the AI flow.
 *
 * Each preview is an image-to-image restyle of one neutral Unsplash portrait,
 * using the exact prompt the server uses for that style. That way the card the
 * guest taps shows what the style really does instead of an idealized sample.
 *
 * Usage:
 *   node generate.mjs [--styles <styles.json>] [--only ghibli,oil] [--force]
 *                     [--reference <url|path>] [--resolution 1K] [--dry-run]
 *
 * Prompts live in the server repo (ServerPhotoBoot/styles.json); this tool only
 * reads them, so the previews stay honest when the server prompts change.
 */
import { readFile, writeFile, mkdir, readdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const API_URL = "https://openrouter.ai/api/v1/images";
const DEFAULT_MODEL = "openai/gpt-image-2.6-sunburst";

/** Neutral stock portrait already used by the shipped previews. */
const DEFAULT_REFERENCE =
  "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=1200&h=1800&fit=crop&crop=faces&q=90";

const DEFAULT_STYLES = path.resolve(__dirname, "..", "..", "..", "ServerPhotoBoot", "styles.json");

const EXTENSIONS = ["png", "jpg", "jpeg", "webp"];

function fail(message) {
  console.error(`\n✖ ${message}\n`);
  process.exit(1);
}

function printHelp() {
  console.log(`
Genera las miniaturas de estilos con OpenRouter.

  node generate.mjs [opciones]

Opciones:
  --styles <path>        styles.json del servidor (default: ${DEFAULT_STYLES})
  --only <ids>           Estilos separados por coma (default: todos)
  --reference <url|path> Retrato neutral de referencia (default: Unsplash)
  --model <slug>         Modelo de imagen (default: ${DEFAULT_MODEL})
  --resolution <r>       Default: 1K
  --quality <q>          Default: medium (low|medium|high)
  --out <dir>            Salida (default: ./out)
  --force                Regenera aunque el archivo exista
  --dry-run              No llama a la API
  --help                 Esta ayuda
`);
}

function parseArgs(argv) {
  const args = {
    out: path.join(__dirname, "out"),
    styles: DEFAULT_STYLES,
    reference: DEFAULT_REFERENCE,
    resolution: "1K",
    quality: "medium",
    force: false,
    dryRun: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) fail(`Falta el valor de ${arg}`);
      return argv[++i];
    };

    switch (arg) {
      case "--styles":
        args.styles = path.resolve(next());
        break;
      case "--only":
        args.only = next().split(",").map((s) => s.trim()).filter(Boolean);
        break;
      case "--reference":
        args.reference = next();
        break;
      case "--model":
        args.model = next();
        break;
      case "--resolution":
        args.resolution = next();
        break;
      case "--quality":
        args.quality = next();
        break;
      case "--out":
        args.out = path.resolve(next());
        break;
      case "--suffix":
        args.suffix = next();
        break;
      case "--seed": {
        const value = Number(next());
        if (!Number.isFinite(value)) fail("--seed necesita un número");
        args.seed = Math.trunc(value);
        break;
      }
      case "--force":
        args.force = true;
        break;
      case "--dry-run":
        args.dryRun = true;
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

/** Reads .env without dependencies; never overrides existing env vars. */
async function loadEnv() {
  const candidates = [
    path.join(__dirname, ".env"),
    path.join(__dirname, "..", "generate-backgrounds", ".env"),
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

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class HttpError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}: ${body}`);
    this.status = status;
  }
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

      const waitMs = attempt * 6000;
      console.warn(`  ↻ Reintento ${attempt} en ${waitMs / 1000}s (${String(err.message).slice(0, 120)})`);
      await sleep(waitMs);
    }
  }
  throw lastError;
}

/** Returns the reference portrait as a base64 data URL. */
async function loadReference(reference) {
  if (/^https?:\/\//i.test(reference)) {
    const response = await fetch(reference, { headers: { "User-Agent": "Mozilla/5.0" } });
    if (!response.ok) fail(`No se pudo descargar la referencia (HTTP ${response.status}).`);
    const type = response.headers.get("content-type") || "image/jpeg";
    const buffer = Buffer.from(await response.arrayBuffer());
    return { dataUrl: `data:${type.split(";")[0]};base64,${buffer.toString("base64")}`, bytes: buffer.length };
  }

  const file = path.resolve(reference);
  if (!existsSync(file)) fail(`No existe la referencia: ${file}`);
  const buffer = await readFile(file);
  const ext = path.extname(file).toLowerCase().replace(".", "") || "jpeg";
  const type = ext === "jpg" ? "jpeg" : ext;
  return { dataUrl: `data:image/${type};base64,${buffer.toString("base64")}`, bytes: buffer.length };
}

async function requestImage({ model, prompt, referenceUrl, resolution, quality, apiKey, seed }) {
  const body = {
    model,
    prompt,
    n: 1,
    aspect_ratio: "9:16",
    resolution,
    quality,
    background: "opaque",
    output_format: "png",
    input_references: [{ type: "image_url", image_url: { url: referenceUrl } }],
  };
  if (seed != null) body.seed = seed;

  const response = await fetch(API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-OpenRouter-Title": "photo-booth-style-previews",
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

  return response.json();
}

function extensionFromMediaType(mediaType) {
  if (!mediaType) return "png";
  if (mediaType.includes("png")) return "png";
  if (mediaType.includes("jpeg") || mediaType.includes("jpg")) return "jpg";
  if (mediaType.includes("webp")) return "webp";
  return "png";
}

async function findExisting(outDir, id) {
  for (const ext of EXTENSIONS) {
    const candidate = path.join(outDir, `${id}.${ext}`);
    if (existsSync(candidate)) return candidate;
  }
  return null;
}

async function main() {
  if (typeof fetch !== "function") fail("Se requiere Node 18 o superior (fetch global).");

  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }

  await loadEnv();

  const apiKey = process.env.OPENROUTER_API_KEY;
  if (!apiKey) fail("Falta OPENROUTER_API_KEY (usa tools/generate-backgrounds/.env).");

  if (!existsSync(args.styles)) {
    fail(`No existe ${args.styles}. Pásalo con --styles <ruta a styles.json>.`);
  }

  const allStyles = JSON.parse(await readFile(args.styles, "utf8"));
  const styles = args.only ? allStyles.filter((s) => args.only.includes(s.id)) : allStyles;
  if (styles.length === 0) fail(`Ningún estilo coincide con --only ${args.only.join(",")}`);

  await mkdir(args.out, { recursive: true });

  const model = args.model || DEFAULT_MODEL;
  console.log(`Estilos:   ${args.styles}`);
  console.log(`Salida:    ${args.out}`);
  console.log(`Modelo:    ${model} · 9:16 · ${args.resolution} · ${args.quality}`);
  console.log(`A generar: ${styles.map((s) => s.id).join(", ")}`);

  let referenceUrl = null;
  if (!args.dryRun) {
    const reference = await loadReference(args.reference);
    referenceUrl = reference.dataUrl;
    console.log(`Referencia: ${(reference.bytes / 1024).toFixed(0)} KB`);
  }
  console.log("");

  const results = [];

  for (const style of styles) {
    const existing = args.suffix ? null : await findExisting(args.out, style.id);
    if (existing && !args.force) {
      console.log(`- ${style.id}: ya existe (${path.basename(existing)}), se omite`);
      results.push({ id: style.id, status: "skipped", file: existing });
      continue;
    }

    if (args.dryRun) {
      console.log(`- ${style.id}: POST ${API_URL} { prompt: ${JSON.stringify(style.prompt.slice(0, 60))}… }`);
      results.push({ id: style.id, status: "dry-run" });
      continue;
    }

    console.log(`- ${style.id}: generando…`);
    const startedAt = Date.now();
    try {
      const json = await withRetry(() =>
        requestImage({
          model,
          prompt: style.prompt,
          referenceUrl,
          resolution: args.resolution,
          quality: args.quality,
          apiKey,
          seed: args.seed,
        })
      );

      const image = json?.data?.[0];
      if (!image?.b64_json) {
        throw new Error(`La respuesta no trae data[0].b64_json: ${JSON.stringify(json).slice(0, 400)}`);
      }

      const ext = extensionFromMediaType(image.media_type);
      const fileName = args.suffix ? `${style.id}-${args.suffix}.${ext}` : `${style.id}.${ext}`;
      const file = path.join(args.out, fileName);
      await writeFile(file, Buffer.from(image.b64_json, "base64"));

      const cost = json?.usage?.cost;
      const seconds = ((Date.now() - startedAt) / 1000).toFixed(1);
      console.log(`  ✓ ${path.basename(file)} en ${seconds}s${cost != null ? ` ($${Number(cost).toFixed(4)})` : ""}`);

      results.push({
        id: style.id,
        name: style.name,
        status: "ok",
        file,
        cost: cost ?? null,
        model,
        prompt: style.prompt,
        mediaType: image.media_type,
      });
    } catch (err) {
      console.error(`  ✖ ${style.id}: ${err.message}`);
      results.push({ id: style.id, status: "failed", error: err.message });
    }
  }

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

    const generated = new Set(ok.map((r) => path.basename(r.file)));
    manifest = manifest.filter((entry) => !generated.has(entry.file));

    const generatedAt = new Date().toISOString();
    for (const result of ok) {
      manifest.push({
        style: result.id,
        name: result.name,
        file: path.basename(result.file),
        model: result.model,
        prompt: result.prompt,
        reference: args.reference,
        aspect_ratio: "9:16",
        resolution: args.resolution,
        quality: args.quality,
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
