#!/usr/bin/env bun
// claude-clip collector. Serves one JSON document describing Claude Code plan usage.
//
//   bun collector/main.ts [--dry-run] [--bind 127.0.0.1] [--port 7337] [--stale-seconds 900]
//
// Sources, first success wins:
//   1. teamclaude  when CLIP_TEAMCLAUDE_URL and CLIP_TEAMCLAUDE_KEY are set
//   2. statusline-tap  the file tap/tap.ts writes
//
// Binding anything other than loopback requires --expose, so the payload never leaves
// the machine by accident.

import crypto from "node:crypto";
import { parseArgs } from "node:util";
import { build, type Payload, type Reading } from "./payload";
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

function optionsFromEnv(staleSeconds: number): Options {
  const url = process.env.CLIP_TEAMCLAUDE_URL;
  const key = process.env.CLIP_TEAMCLAUDE_KEY;
  return { staleSeconds, teamclaude: url && key ? { url, key } : undefined };
}

async function main(): Promise<void> {
  const { values } = parseArgs({
    options: {
      "dry-run": { type: "boolean", default: false },
      bind: { type: "string", default: "127.0.0.1" },
      port: { type: "string", default: "7337" },
      "stale-seconds": { type: "string", default: "900" },
      expose: { type: "boolean", default: false },
    },
  });
  const opts = optionsFromEnv(Number(values["stale-seconds"]));

  if (values["dry-run"]) {
    console.log(JSON.stringify(await collect(opts), null, 2));
    return;
  }

  const exposed = !LOOPBACK.has(values.bind!);
  if (exposed && !values.expose) {
    console.error(`refusing to bind ${values.bind} without --expose`);
    process.exit(2);
  }
  // Off loopback, every request must carry the shared token the phone relay holds.
  const token = process.env.CLIP_TOKEN;
  if (exposed && (!token || token.length < 16)) {
    console.error("--expose requires CLIP_TOKEN of at least 16 characters");
    process.exit(2);
  }

  const server = Bun.serve({
    hostname: values.bind,
    port: Number(values.port),
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
  console.error(`claude-clip collector on http://${server.hostname}:${server.port}/v1/usage`);
}

if (import.meta.main) await main();
