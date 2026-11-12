#!/usr/bin/env node
// Assembles the explanatory attract clip ("how it works") from the v6 beats.
//
// This clip is sequential, not a montage: every beat starts from a still that
// continues the previous action, so the beats are joined with a cross dissolve
// instead of the white flash used by the fantasy loop. Beats 2 and 4 were
// generated before the change and still end on a flash to white, so `keep` cuts
// them right before the flash starts (measured on their own frames) and the
// dissolve hides the cut.
//
// Upscale 720x1280 -> 1080x1920 with Lanczos.
//
// Caption timings live in AttractCaptions.kt and must match the segment lengths
// below; they are printed at the end of a run so the two can be kept in sync.
//
// Usage:
//   node tools/attract-video/assemble-explain.mjs [--dry-run] [--out <path>]

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

const ROOT = resolve(import.meta.dirname, "..", "..");
const FFMPEG = join(ROOT, "out", "attract-v4", "bin", "ffmpeg.exe");
const CLIPS = join(ROOT, "out", "attract-v6", "clips");

const OUT_WIDTH = 1080;
const OUT_HEIGHT = 1920;
const FPS = 30;
const CRF = "22";
const XFADE = 0.55;
const LAST_TAIL = 0.8;

// `keep` is how many seconds of the source survive. `tail` freezes the last
// frame of the final beat so the closing pose has room to breathe.
const SEGMENTS = [
  { file: "v6-1b.mp4", keep: 4.9, tail: 0 },
  { file: "v6-2.mp4", keep: 4.3, tail: 0 },
  { file: "v6-3c.mp4", keep: 4.9, tail: 0 },
  { file: "v6-4.mp4", keep: 4.15, tail: 0 },
  { file: "v6-5c.mp4", keep: 4.9, tail: 0 },
  { file: "v6-6c.mp4", keep: 4.9, tail: LAST_TAIL },
];

function parseArgs(argv) {
  const args = { out: resolve(ROOT, "app", "src", "main", "assets", "attract", "attract-explain.mp4"), dryRun: false };
  for (let i = 0; i < argv.length; i += 1) {
    const flag = argv[i];
    if (flag === "--dry-run") args.dryRun = true;
    else if (flag === "--out") args.out = resolve(argv[(i += 1)]);
    else throw new Error(`unknown flag: ${flag}`);
  }
  return args;
}

function timeline() {
  const marks = [];
  let length = SEGMENTS[0].keep;
  marks.push({ file: SEGMENTS[0].file, from: 0, to: length + SEGMENTS[0].tail });

  for (let i = 1; i < SEGMENTS.length; i += 1) {
    const offset = length - XFADE;
    const start = offset + XFADE; // where the incoming clip is fully visible
    length = length + SEGMENTS[i].keep - XFADE;
    marks.push({ file: SEGMENTS[i].file, from: start, to: length + SEGMENTS[i].tail });
  }

  return { marks, total: length + SEGMENTS[SEGMENTS.length - 1].tail };
}

function buildArgs(out) {
  const inputs = [];
  const filters = [];

  SEGMENTS.forEach((segment, index) => {
    const file = join(CLIPS, segment.file);
    if (!existsSync(file)) throw new Error(`missing clip: ${file}`);
    inputs.push("-i", file);
    filters.push(
      `[${index}:v]trim=0:${segment.keep},setpts=PTS-STARTPTS,` +
        `scale=${OUT_WIDTH}:${OUT_HEIGHT}:flags=lanczos,setsar=1,fps=${FPS}[v${index}]`,
    );
  });

  let previous = "v0";
  let length = SEGMENTS[0].keep;
  for (let i = 1; i < SEGMENTS.length; i += 1) {
    const out = `x${i}`;
    filters.push(
      `[${previous}][v${i}]xfade=transition=fade:duration=${XFADE}:offset=${(length - XFADE).toFixed(2)}[${out}]`,
    );
    length = length + SEGMENTS[i].keep - XFADE;
    previous = out;
  }

  const tail = SEGMENTS[SEGMENTS.length - 1].tail;
  if (tail > 0) {
    filters.push(`[${previous}]tpad=stop_mode=clone:stop_duration=${tail}[out]`);
  } else {
    filters.push(`[${previous}]null[out]`);
  }

  return [
    "-y",
    "-hide_banner",
    "-loglevel", "error",
    ...inputs,
    "-filter_complex", filters.join(";"),
    "-map", "[out]",
    "-an",
    "-c:v", "libx264",
    "-preset", "slow",
    "-crf", CRF,
    "-profile:v", "high",
    "-level", "4.0",
    "-pix_fmt", "yuv420p",
    "-colorspace", "bt709",
    "-color_primaries", "bt709",
    "-color_trc", "bt709",
    "-movflags", "+faststart",
    out,
  ];
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const ffmpeg = existsSync(FFMPEG) ? FFMPEG : "ffmpeg";
  const ffmpegArgs = buildArgs(args.out);
  const { marks, total } = timeline();

  if (args.dryRun) {
    console.log(`${ffmpeg} ${ffmpegArgs.join(" ")}`);
    return;
  }

  mkdirSync(dirname(args.out), { recursive: true });
  console.log(`clips:    ${SEGMENTS.length}`);
  for (const mark of marks) {
    console.log(`  ${mark.file.padEnd(12)} ${mark.from.toFixed(2)}s -> ${mark.to.toFixed(2)}s`);
  }
  console.log(`total:    ${total.toFixed(2)} s`);
  console.log("encoding…");
  execFileSync(ffmpeg, ffmpegArgs, { stdio: "inherit" });

  const bytes = statSync(args.out).size;
  console.log(`saved:    ${args.out} (${(bytes / 1024 / 1024).toFixed(2)} MB)`);
  if (bytes > 12 * 1024 * 1024) {
    console.warn("warning:  over the 12 MB budget, raise CRF or trim the beats");
  }
}

main();
