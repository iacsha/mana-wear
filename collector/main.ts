#!/usr/bin/env bun
// Mana collector. Serves one JSON document describing Claude Code plan usage.
//
//   mana-collector serve [--dry-run] [--bind 127.0.0.1] [--port 7337] [--stale-seconds 900]
//
// Sources, first success wins:
//   1. teamclaude  when MANA_TEAMCLAUDE_URL and MANA_TEAMCLAUDE_KEY are set, or in config
//   2. statusline-tap  the file tap/tap.ts writes
//
// Binding anything other than loopback needs --expose, or a bind address that
// `mana-collector init` saved, so the payload never leaves the machine by accident.

import crypto from "node:crypto";
import { parseArgs } from "node:util";
import { build, type Payload, type Reading } from "./payload";
import { loadConfig, type Config } from "./config";
import { readTap, readTeamclaude, SourceError } from "./sources";

const LOOPBACK = new Set(["127.0.0.1", "::1", "localhost"]);

function sameToken(got: string | null, want: string): boolean {
  const a = Buffer.from(got ?? "");
  const b = Buffer.from(want);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export interface Options {
  staleSeconds: number;
  teamclaude?: { url: string; key: string };
  tapDir?: string;
}

export async function collect(opts: Options): Promise<Payload> {
  const errors: string[] = [];
  let reading: Reading | null = null;

  if (opts.teamclaude) {
    try {
      reading = await readTeamclaude(opts.teamclaude);
    } catch (e) {
      errors.push(e instanceof SourceError ? e.message : "teamclaude:error");
    }
  }
  if (!reading) {
    try {
      reading = readTap(opts.tapDir);
    } catch (e) {
      errors.push(e instanceof SourceError ? e.message : "statusline-tap:error");
    }
  }
  return build(reading, errors, opts.staleSeconds);
}

// MANA_* wins; CLIP_* is the pre-rename spelling and still read.
function env(name: string): string | undefined {
  return process.env[`MANA_${name}`] || process.env[`CLIP_${name}`] || undefined;
}

function options(staleSeconds: number, cfg: Config, tapDir?: string): Options {
  const url = env("TEAMCLAUDE_URL");
  const key = env("TEAMCLAUDE_KEY");
  const teamclaude = url && key ? { url, key } : cfg.teamclaude;
  return { staleSeconds, teamclaude, tapDir };
}

export async function serve(args: string[]): Promise<void> {
  const { values } = parseArgs({
    args,
    options: {
      "dry-run": { type: "boolean", default: false },
      bind: { type: "string" },
      port: { type: "string" },
      "stale-seconds": { type: "string", default: "900" },
      expose: { type: "boolean", default: false },
    },
  });
  const cfg = loadConfig();
  const opts = options(Number(values["stale-seconds"]), cfg);

  if (values["dry-run"]) {
    console.log(JSON.stringify(await collect(opts), null, 2));
    return;
  }

  const bind = values.bind ?? cfg.bind ?? "127.0.0.1";
  const port = Number(values.port ?? cfg.port ?? 7337);
  // A bind address saved by init was a deliberate choice, so it counts as --expose.
  const exposed = !LOOPBACK.has(bind);
  if (exposed && !values.expose && values.bind !== undefined) {
    console.error(`refusing to bind ${bind} without --expose`);
    process.exit(2);
  }
  // Every request must carry the token the phone holds. Loopback without a token is
  // allowed for local testing; anything reachable from another machine is not.
  const token = env("TOKEN") ?? cfg.token;
  if (exposed && (!token || token.length < 16)) {
    console.error("an exposed collector needs a token of at least 16 characters (run mana-collector init)");
    process.exit(2);
  }

  const server = Bun.serve({
    hostname: bind,
    port,
    async fetch(req) {
      const path = new URL(req.url).pathname;
      if (req.method !== "GET") return new Response("method not allowed", { status: 405 });
      if (path === "/healthz") return new Response("ok\n");
      if (token && !sameToken(req.headers.get("authorization"), `Bearer ${token}`)) {
        return new Response("unauthorized", { status: 401 });
      }
      if (path !== "/v1/usage") return new Response("not found", { status: 404 });
      return Response.json(await collect(opts), { headers: { "cache-control": "no-store" } });
    },
  });
  console.error(`mana collector on http://${server.hostname}:${server.port}/v1/usage`);
}

if (import.meta.main) await serve(process.argv.slice(2));
