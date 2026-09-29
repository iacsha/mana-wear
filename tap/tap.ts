#!/usr/bin/env bun
// claude-clip statusLine tap.
//
// Claude Code passes a JSON document on stdin to the statusLine command. Since 2.1.80
// that document carries `rate_limits` for Pro/Max plans. This tap records only the
// rate-limit figures and the Claude Code version, then hands the untouched stdin to an
// optional downstream statusLine command and relays its output.
//
// Usage in settings.json:
//   "command": "bun /path/to/tap.ts node /path/to/your-existing-statusline.js"
//
// The tap must never break the status line. Every recording failure is swallowed.

import { mkdirSync, renameSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export const TAP_SCHEMA_VERSION = 1;

export interface Window {
  usedPercentage: number;
  resetsAt: string | null;
}

export interface TapRecord {
  tapSchemaVersion: number;
  observedAt: string;
  claudeCodeVersion: string | null;
  fiveHour: Window | null;
  sevenDay: Window | null;
}

export function stateDir(): string {
  const base = process.env.XDG_STATE_HOME || join(homedir(), ".local", "state");
  return join(base, "claude-clip");
}

function toWindow(seg: unknown): Window | null {
  if (!seg || typeof seg !== "object") return null;
  const s = seg as Record<string, unknown>;
  const pct = Number(s.used_percentage);
  if (!Number.isFinite(pct) || pct < 0 || pct > 100) return null;
  // resets_at is Unix epoch seconds.
  const epoch = Number(s.resets_at);
  const resetsAt =
    Number.isFinite(epoch) && epoch > 0 ? new Date(epoch * 1000).toISOString() : null;
  return { usedPercentage: pct, resetsAt };
}

// Returns null when stdin carries no usable rate limits (API-key users, or before the
// first API response of a session). Null means "nothing new", not "zero usage".
export function extract(input: unknown, now: Date = new Date()): TapRecord | null {
  if (!input || typeof input !== "object") return null;
  const doc = input as Record<string, unknown>;
  const rl = doc.rate_limits as Record<string, unknown> | undefined;
  if (!rl || typeof rl !== "object") return null;
  const fiveHour = toWindow(rl.five_hour);
  const sevenDay = toWindow(rl.seven_day);
  if (!fiveHour && !sevenDay) return null;
  return {
    tapSchemaVersion: TAP_SCHEMA_VERSION,
    observedAt: now.toISOString(),
    claudeCodeVersion: typeof doc.version === "string" ? doc.version : null,
    fiveHour,
    sevenDay,
  };
}

// Key names and value types only, never values. Used to record the live stdin shape
// without capturing paths, session ids or model names.
export function shapeOf(value: unknown): unknown {
  if (Array.isArray(value)) return value.length ? [shapeOf(value[0])] : [];
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const k of Object.keys(value).sort()) out[k] = shapeOf((value as any)[k]);
    return out;
  }
  return value === null ? "null" : typeof value;
}

function writeAtomic(path: string, body: string): void {
  const tmp = `${path}.${process.pid}.tmp`;
  writeFileSync(tmp, body, { mode: 0o600 });
  renameSync(tmp, path);
}

export function record(raw: string, dir: string = stateDir()): void {
  let input: unknown;
  try {
    input = JSON.parse(raw);
  } catch {
    return;
  }
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  writeAtomic(join(dir, "stdin-shape.json"), JSON.stringify(shapeOf(input), null, 2) + "\n");
  const rec = extract(input);
  if (rec) writeAtomic(join(dir, "usage.json"), JSON.stringify(rec) + "\n");
}

async function main(): Promise<void> {
  const raw = await Bun.stdin.text();
  try {
    record(raw);
  } catch {
    // Never let the tap break the status line.
  }

  // Bun swallows a literal `--`, so everything after the script path is the downstream
  // command. A leading `--` is still tolerated for runtimes that pass it through.
  const rest = process.argv.slice(2);
  const downstream = rest[0] === "--" ? rest.slice(1) : rest;
  if (downstream.length === 0) return;

  const proc = Bun.spawn(downstream, { stdin: "pipe", stdout: "pipe", stderr: "inherit" });
  proc.stdin.write(raw);
  proc.stdin.end();
  const out = await new Response(proc.stdout).text();
  process.stdout.write(out);
  process.exitCode = await proc.exited;
}

if (import.meta.main) await main();
