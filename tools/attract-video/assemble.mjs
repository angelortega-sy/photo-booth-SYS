#!/usr/bin/env node
// Assembles the attract loop from the generated clips.
//
// Every beat was generated 4 s at 720x1280 starting from its styled still and
// ending on a pure-white frame, so the beats are cut mid-flash and the next one
// is faded in from white: the flash never fully lands and the montage reads as
// one continuous series of transformations. Clip A ends on its own flash, clip C
// starts in the same white studio, so the whole thing is stitched by light.
//
// Upscale 720x1280 -> 1080x1920 (half of the 2160x3840 panel) with Lanczos.
//
// Usage:
//   node tools/attract-video/assemble.mjs [--dry-run] [--out <path>]

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

const ROOT = resolve(import.meta.dirname, "..", "..");
const FFMPEG = join(ROOT, "out", "attract-v4", "bin", "ffmpeg.exe");
const CLIPS = join(ROOT, "out", "attract-v4", "clips");
const DEFAULT_OUT = join(ROOT, "app", "src", "main", "assets", "attract", "attract-loop.mp4");

const OUT_WIDTH = 1080;
const OUT_HEIGHT = 1920;
const FPS = 30;
const CRF = "22";
const FADE_IN = 0.35;
const WHITE_HOLD = 0.12;

// `keep` is how many seconds of the source survive. `fade` is the white fade at
// the head; `tail` freezes the last frame so the slogan has room to appear.
// `out` forces the tail to white. The model does not always reach pure white on
// the last frame (b1 stops at the lantern's glow), and every beat has to hand
// over to the next one on white, so the fade is drawn here instead of trusted.
const SEGMENTS = [
  { file: "a5.mp4", keep: 7.75, fade: 0, out: 0, tail: 0 },
  { file: "b1.mp4", keep: 3.4, fade: FADE_IN, out: 0.45, tail: 0 },
  { file: "b2.mp4", keep: 3.4, fade: FADE_IN, out: 0.3, tail: 0 },
  { file: "b3.mp4", keep: 3.4, fade: FADE_IN, out: 0.3, tail: 0 },
  { file: "b4.mp4", keep: 3.4, fade: FADE_IN, out: 0.3, tail: 0 },
  { file: "b5.mp4", keep: 3.4, fade: FADE_IN, out: 0.3, tail: 0 },
  { file: "c.mp4", keep: 5.2, fade: 0.25, out: 0, tail: 0.9 },
];

function parseArgs(argv) {
  const args = { out: DEFAULT_OUT, dryRun: false };
  for (let i = 0; i < argv.length; i += 1) {
    const flag = argv[i];
    if (flag === "--dry-run") args.dryRun = true;
    else if (flag === "--out") args.out = resolve(argv[(i += 1)]);
    else throw new Error(`unknown flag: ${flag}`);
  }
  return args;
}

function buildArgs(out) {
  const inputs = [];
  const filters = [];
  const labels = [];

  SEGMENTS.forEach((segment, index) => {
    const file = join(CLIPS, segment.file);
    if (!existsSync(file)) throw new Error(`missing clip: ${file}`);
    inputs.push("-i", file);

    const chain = [
      `trim=0:${segment.keep}`,
      "setpts=PTS-STARTPTS",
      `scale=${OUT_WIDTH}:${OUT_HEIGHT}:flags=lanczos`,
      "setsar=1",
      `fps=${FPS}`,
    ];
    if (segment.fade > 0) {
      chain.push(`fade=t=in:st=0:d=${segment.fade}:color=white`);
    }
    if (segment.out > 0) {
      chain.push(`fade=t=out:st=${(segment.keep - segment.out - 0.08).toFixed(2)}:d=${segment.out}:color=white:color=white`);
      // Hold the white for a beat: a single white frame reads as a glitch, not a flash.
      chain.push(`tpad=stop_mode=clone:stop_duration=${WHITE_HOLD}`);
    }
    if (segment.tail > 0) {
      chain.push(`tpad=stop_mode=clone:stop_duration=${segment.tail}`);
    }
    const label = `v${index}`;
    filters.push(`[${index}:v]${chain.join(",")}[${label}]`);
    labels.push(`[${label}]`);
  });

  filters.push(`${labels.join("")}concat=n=${SEGMENTS.length}:v=1:a=0[out]`);

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

  if (args.dryRun) {
    console.log(`${ffmpeg} ${ffmpegArgs.join(" ")}`);
    return;
  }

  mkdirSync(dirname(args.out), { recursive: true });
  console.log(`clips:    ${SEGMENTS.length}`);
  console.log(`total:    ${SEGMENTS.reduce((sum, s) => sum + s.keep + s.tail, 0).toFixed(2)} s`);
  console.log("encoding…");
  execFileSync(ffmpeg, ffmpegArgs, { stdio: "inherit" });

  const bytes = statSync(args.out).size;
  console.log(`saved:    ${args.out} (${(bytes / 1024 / 1024).toFixed(2)} MB)`);
  if (bytes > 12 * 1024 * 1024) {
    console.warn("warning:  over the 12 MB budget, raise CRF or trim the beats");
  }
}

main();




