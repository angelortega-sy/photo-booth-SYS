/**
 * Generates a single still with the OpenRouter Images API. Used for the
 * character sheet and the totem that Seedance takes as input_references.
 * Requires Node 18+ (global fetch). No dependencies.
 *
 * Usage:
 *   node still.mjs --name mascot-sheet --prompt-file out/attract-v4/prompts/mascot.txt \
 *     --aspect 16:9 --resolution 2K
 *
 * Options:
 *   --name <slug>        Output name (required); writes <out>/<slug>.<ext>
 *   --prompt-file <path> Prompt loaded from a file (utf8)
 *   --prompt <text>      Prompt passed inline (overrides --prompt-file)
 *   --ref <path>         Edit/reference image (repeatable)
 *   --aspect <ratio>     Aspect ratio (default: 9:16)
 *   --resolution <r>     Resolution hint (default: 2K)
 *   --model <slug>       Overrides the default model
 *   --out <dir>          Output directory (default: <repo>/out/attract-v4/refs)
 *   --dry-run            Print the request body without calling the API
 */
import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..", "..");
const API_URL = "https://openrouter.ai/api/v1/images";
const DEFAULT_MODEL = "google/gemini-3-pro-image";
const DEFAULT_OUT = path.join(REPO_ROOT, "out", "attract-v4", "refs");
const ENV_FILE = path.join(REPO_ROOT, "tools", "generate-backgrounds", ".env");

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
    aspect: "9:16",
    resolution: "2K",
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
      case "--aspect":
        args.aspect = next();
        break;
      case "--resolution":
        args.resolution = next();
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
        console.log("See the header of still.mjs for usage.");
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
  const mime = MIME_BY_EXT[path.extname(absolute).toLowerCase()];
  if (!mime) fail(`unsupported reference image: ${absolute}`);
  const buffer = await readFile(absolute);
  return `data:${mime};base64,${buffer.toString("base64")}`;
}

function extensionFor(mediaType) {
  if (mediaType === "image/jpeg") return "jpg";
  if (mediaType === "image/webp") return "webp";
  return "png";
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (!args.name) fail("--name is required");

  const prompt = args.prompt ?? (args.promptFile ? await readFile(args.promptFile, "utf8") : null);
  if (!prompt || !prompt.trim()) fail("missing prompt (--prompt or --prompt-file)");

  const body = {
    model: args.model,
    prompt: prompt.trim(),
    n: 1,
    aspect_ratio: args.aspect,
    resolution: args.resolution,
    output_format: "png",
  };

  if (args.refs.length > 0) {
    body.input_references = [];
    for (const ref of args.refs) {
      body.input_references.push({ type: "image_url", image_url: { url: await toDataUrl(ref) } });
    }
  }

  if (args.dryRun) {
    const copy = structuredClone(body);
    if (copy.input_references) {
      copy.input_references = copy.input_references.map((ref) => ({
        ...ref,
        image_url: { url: `data:...;base64,<${ref.image_url.url.length} chars>` },
      }));
    }
    console.log(JSON.stringify(copy, null, 2));
    return;
  }

  const apiKey = await loadApiKey();
  await mkdir(args.out, { recursive: true });

  console.log(`model:  ${body.model}`);
  console.log(`format: ${body.aspect_ratio} · ${body.resolution} · ${body.output_format}`);
  console.log("requesting…");

  const response = await fetch(API_URL, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "X-OpenRouter-Title": "photo-booth-attract",
    },
    body: JSON.stringify(body),
  });

  const text = await response.text();
  if (!response.ok) fail(`HTTP ${response.status}: ${text.slice(0, 800)}`);

  const json = JSON.parse(text);
  const image = json.data?.[0];
  if (!image?.b64_json) fail(`response has no data[0].b64_json: ${text.slice(0, 400)}`);

  const file = path.join(args.out, `${args.name}.${extensionFor(image.media_type)}`);
  await writeFile(file, Buffer.from(image.b64_json, "base64"));

  const cost = json.usage?.cost ?? null;
  console.log(`saved:  ${file}`);
  console.log(`cost:   ${cost === null ? "unknown" : `$${cost.toFixed(4)}`}`);
}

main().catch((error) => fail(error?.stack ?? String(error)));
