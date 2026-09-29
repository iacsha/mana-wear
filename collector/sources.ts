// Usage sources. Each returns a Reading, or throws a short error code. None of them
// reads anything under ~/.claude/ or touches Claude.ai credentials.

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { stateDir } from "../tap/tap";
import type { Reading, Window } from "./payload";

export class SourceError extends Error {}

// ---- teamclaude -------------------------------------------------------------------
// A teamclaude proxy tracks every pooled account from the rate-limit headers on each
// response and serves them at GET /teamclaude/quota. Utilization is 0-1, resetAt is
// epoch milliseconds. The key is the proxy's own client key, not a Claude.ai token.

export interface TeamclaudeConfig {
  url: string;
  key: string;
  timeoutMs?: number;
}

function pct01(u: unknown): number | null {
  const n = Number(u);
  if (!Number.isFinite(n) || n < 0 || n > 1.5) return null;
  // teamclaude can report slightly over 1 when an account runs past its limit.
  return Math.min(100, Math.round(n * 1000) / 10);
}

function msIso(v: unknown): string | null {
  const n = Number(v);
  return Number.isFinite(n) && n > 0 ? new Date(n).toISOString() : null;
}

function tcWindow(b: any, resetKey: "resetAt" | "nextResetAt"): Window | null {
  if (!b || typeof b !== "object") return null;
  const p = pct01(b.utilization);
  return p == null ? null : { usedPercentage: p, resetsAt: msIso(b[resetKey]) };
}

export function parseTeamclaude(doc: any, now: Date = new Date()): Reading {
  const agg = doc?.aggregate;
  if (!agg || typeof agg !== "object") throw new SourceError("teamclaude:schema-drift");
  const fiveHour = tcWindow(agg.fiveHour, "nextResetAt");
  const weekly = tcWindow(agg.weeklyShared, "nextResetAt");
  if (!fiveHour && !weekly) throw new SourceError("teamclaude:schema-drift");
  const accounts = Array.isArray(doc.accounts)
    ? doc.accounts
        .filter((a: any) => a && !a.disabled)
        .map((a: any) => ({
          fiveHour: tcWindow(a.buckets?.fiveHour, "resetAt"),
          weekly: tcWindow(a.buckets?.weeklyShared, "resetAt"),
        }))
    : [];
  // The proxy refreshes on every request and on its keep-warm timer, and the quota
  // endpoint carries no per-bucket observation time, so fetch time is the best stamp.
  return { source: "teamclaude", observedAt: now.toISOString(), fiveHour, weekly, accounts };
}

export async function readTeamclaude(cfg: TeamclaudeConfig): Promise<Reading> {
  let res: Response;
  try {
    res = await fetch(new URL("/teamclaude/quota", cfg.url), {
      headers: { "x-api-key": cfg.key },
      signal: AbortSignal.timeout(cfg.timeoutMs ?? 3000),
    });
  } catch {
    throw new SourceError("teamclaude:unreachable");
  }
  if (!res.ok) throw new SourceError(`teamclaude:http-${res.status}`);
  let doc: unknown;
  try {
    doc = await res.json();
  } catch {
    throw new SourceError("teamclaude:bad-json");
  }
  return parseTeamclaude(doc);
}

// ---- statusline tap ---------------------------------------------------------------
// Written by tap/tap.ts whenever Claude Code renders a status line with rate_limits.

export function readTap(dir: string = stateDir()): Reading {
  let raw: string;
  try {
    raw = readFileSync(join(dir, "usage.json"), "utf8");
  } catch {
    throw new SourceError("statusline-tap:missing");
  }
  let rec: any;
  try {
    rec = JSON.parse(raw);
  } catch {
    throw new SourceError("statusline-tap:bad-json");
  }
  if (rec?.tapSchemaVersion !== 1 || typeof rec.observedAt !== "string") {
    throw new SourceError("statusline-tap:schema-drift");
  }
  return {
    source: "statusline-tap",
    observedAt: rec.observedAt,
    fiveHour: rec.fiveHour ?? null,
    weekly: rec.sevenDay ?? null,
    claudeCodeVersion: rec.claudeCodeVersion ?? null,
  };
}
