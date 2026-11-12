/**
 * Generates the attract-loop clips with ByteDance Seedance 2.0 through the
 * OpenRouter Videos API. Requires Node 18+ (global fetch). No dependencies.
 *
 * Usage:
 *   node generate.mjs --name a --prompt-file out/attract-v4/prompts/a.txt \
 *     --ref out/attract-v4/refs/mascot-sheet.png --ref out/attract-v4/refs/totem.png \
 *     --duration 8 --seed 4211
 *
 * Options:
 *   --name <slug>        Clip name; writes clips/<slug>.mp4 and clips/<slug>.json (required)
 *   --prompt-file <path> Prompt loaded from a file (utf8)
 *   --prompt <text>      Prompt passed inline (overrides --prompt-file)
 *   --ref <path>         Reference image (repeatable, reference-to-video)
 *   --first-frame <path> First frame of the clip (image-to-video; cannot be combined with --ref)
 *   --last-frame <path>  Last frame of the clip (only with --first-frame)
 *   --duration <int>     Seconds, 4-15 (default: 8)
 *   --seed <int>         Deterministic seed
 *   --size <WxH>         Output size (default: 1080x1920)
 *   --model <slug>       Overrides the default model
 *   --out <dir>          Output directory (default: <repo>/out/attract-v4/clips)
 *   --dry-run            Print the request body without calling the API
 */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..", "..");
const API_URL = "https://openrouter.ai/api/v1/videos";
const DEFAULT_MODEL = "bytedance/seedance-2.0";
const DEFAULT_OUT = path.join(REPO_ROOT, "out", "attract-v4", "clips");
const ENV_FILE = path.join(REPO_ROOT, "tools", "generate-backgrounds", ".env");
const POLL_INTERVAL_MS = 15_000;
const MAX_POLLS = 160; // ~40 minutes

const MIME_BY_EXT = {
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
};

function fail(message) {
  console.error(`error: ${message}`);
  process.exit(1);
}

function parseArgs(argv) {
  const args = {
    name: null,
    promptFile: null,
    prompt: null,
    refs: [],
    firstFrame: null,
    lastFrame: null,
    duration: 8,
    seed: undefined,
    size: "1080x1920",
    model: DEFAULT_MODEL,
    out: DEFAULT_OUT,
    dryRun: false,
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      i += 1;
      if (i >= argv.length) fail(`${arg} needs a value`);
      return argv[i];
    };

    switch (arg) {
      case "--name":
        args.name = next();
        break;
      case "--prompt-file":
        args.promptFile = next();
        break;
      case "--prompt":
        args.prompt = next();
        break;
      case "--ref":
        args.refs.push(next());
        break;
      case "--first-frame":
        args.firstFrame = next();
        break;
      case "--last-frame":
        args.lastFrame = next();
        break;
      case "--duration":
        args.duration = Number.parseInt(next(), 10);
        break;
      case "--seed":
        args.seed = Number.parseInt(next(), 10);
        break;
      case "--size":
        args.size = next();
        break;
      case "--model":
        args.model = next();
        break;
      case "--out":
        args.out = path.resolve(next());
        break;
      case "--dry-run":
        args.dryRun = true;
        break;
      case "--help":
      case "-h":
        console.log("See the header of generate.mjs for usage.");
        process.exit(0);
        break;
      default:
        fail(`unknown flag: ${arg}`);
    }
  }

  return args;
}

async function loadApiKey() {
  if (process.env.OPENROUTER_API_KEY) return process.env.OPENROUTER_API_KEY.trim();

  let raw;
  try {
    raw = await readFile(ENV_FILE, "utf8");
  } catch {
    fail("OPENROUTER_API_KEY is not set and tools/generate-backgrounds/.env is missing.");
  }

  for (const line of raw.split(/\r?\n/)) {
    const match = line.match(/^\s*OPENROUTER_API_KEY\s*=\s*(.+?)\s*$/);
    if (match) return match[1].replace(/^["']|["']$/g, "");
  }

  fail("OPENROUTER_API_KEY not found in tools/generate-backgrounds/.env.");
}

async function toDataUrl(relativeOrAbsolutePath) {
  const absolute = path.resolve(relativeOrAbsolutePath);
  const ext = path.extname(absolute).toLowerCase();
  const mime = MIME_BY_EXT[ext];
  if (!mime) fail(`unsupported reference image extension: ${ext}`);
  const buffer = await readFile(absolute);
  return { dataUrl: `data:${mime};base64,${buffer.toString("base64")}`, bytes: buffer.length };
}

async function buildBody(args) {
  const prompt = args.prompt ?? (args.promptFile ? await readFile(args.promptFile, "utf8") : null);
  if (!prompt || !prompt.trim()) fail("missing prompt (--prompt or --prompt-file)");

  const body = {
    model: args.model,
    prompt: prompt.trim(),
    size: args.size,
    duration: args.duration,
    generate_audio: false,
  };
  if (args.seed !== undefined && !Number.isNaN(args.seed)) body.seed = args.seed;

  if (args.refs.length > 0 && (args.firstFrame || args.lastFrame)) {
    fail("--ref cannot be combined with --first-frame/--last-frame: the API ignores the references when frames are sent.");
  }
  if (args.lastFrame && !args.firstFrame) {
    fail("--last-frame requires --first-frame");
  }

  if (args.firstFrame) {
    body.frame_images = [];
    for (const [file, frameType] of [
      [args.firstFrame, "first_frame"],
      [args.lastFrame, "last_frame"],
    ]) {
      if (!file) continue;
      const { dataUrl } = await toDataUrl(file);
      body.frame_images.push({ type: "image_url", image_url: { url: dataUrl }, frame_type: frameType });
    }
  }

  if (args.refs.length > 0) {
    body.input_references = [];
    for (const ref of args.refs) {
      const { dataUrl } = await toDataUrl(ref);
      body.input_references.push({ type: "image_url", image_url: { url: dataUrl } });
    }
  }

  return body;
}

function redact(body) {
  const copy = structuredClone(body);
  for (const key of ["input_references", "frame_images"]) {
    if (!copy[key]) continue;
    copy[key] = copy[key].map((ref) => ({
      ...ref,
      image_url: { url: `data:...;base64,<${ref.image_url.url.length} chars>` },
    }));
  }
  return copy;
}

async function post(url, apiKey, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-OpenRouter-Title": "photo-booth-attract",
    },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) fail(`submit failed HTTP ${response.status}: ${text.slice(0, 800)}`);
  return JSON.parse(text);
}

async function poll(pollingUrl, apiKey) {
  for (let attempt = 1; attempt <= MAX_POLLS; attempt += 1) {
    const response = await fetch(pollingUrl, {
      headers: { Authorization: `Bearer ${apiKey}` },
    });
    const text = await response.text();
    if (!response.ok) fail(`poll failed HTTP ${response.status}: ${text.slice(0, 800)}`);

    const job = JSON.parse(text);
    const elapsed = Math.round((attempt * POLL_INTERVAL_MS) / 1000);
    console.log(`  [${elapsed}s] ${job.status}`);

    if (job.status === "completed") return job;
    if (job.status === "failed") fail(`generation failed: ${JSON.stringify(job.error ?? job).slice(0, 800)}`);

    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  fail(`timed out after ${MAX_POLLS} polls`);
}

async function download(job, apiKey, destination) {
  const url = job.unsigned_urls?.[0] ?? `https://openrouter.ai/api/v1/videos/${job.id}/content?index=0`;
  const response = await fetch(url, { headers: { Authorization: `Bearer ${apiKey}` } });
  if (!response.ok) fail(`download failed HTTP ${response.status}`);
  const buffer = Buffer.from(await response.arrayBuffer());
  await writeFile(destination, buffer);
  return buffer.length;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.name) fail("--name is required");

  const body = await buildBody(args);

  if (args.dryRun) {
    console.log(JSON.stringify(redact(body), null, 2));
    return;
  }

  const apiKey = await loadApiKey();
  await mkdir(args.out, { recursive: true });

  console.log(`model:    ${body.model}`);
  console.log(`size:     ${body.size} · ${body.duration}s · audio ${body.generate_audio ? "on" : "off"}`);
  console.log(`refs:     ${args.refs.length}`);
  const frameCount = body.frame_images?.length ?? 0;
  console.log(`frames:   ${frameCount === 0 ? "none" : frameCount === 1 ? "first only" : "first + last"}`);
  console.log(`seed:     ${body.seed ?? "random"}`);
  console.log(`prompt:   ${body.prompt.length} chars`);
  console.log("submitting…");

  const submitted = await post(API_URL, apiKey, body);
  console.log(`job:      ${submitted.id} (${submitted.status})`);

  const job = await poll(submitted.polling_url, apiKey);
  const destination = path.join(args.out, `${args.name}.mp4`);
  const bytes = await download(job, apiKey, destination);

  const cost = job.usage?.cost ?? null;
  const manifest = {
    name: args.name,
    model: body.model,
    prompt: body.prompt,
    duration: body.duration,
    size: body.size,
    seed: body.seed ?? null,
    ref_count: args.refs.length,
    frames: (body.frame_images ?? []).map((f) => f.frame_type),
    job_id: job.id,
    cost_usd: cost,
    bytes,
    generated_at: new Date().toISOString(),
  };
  await writeFile(path.join(args.out, `${args.name}.json`), `${JSON.stringify(manifest, null, 2)}\n`);

  console.log(`saved:    ${destination} (${(bytes / 1024 / 1024).toFixed(2)} MB)`);
  console.log(`cost:     ${cost === null ? "unknown" : `$${cost.toFixed(4)}`}`);
}

main().catch((error) => fail(error?.stack ?? String(error)));
