import { afterAll, beforeAll, describe, expect, test } from "bun:test";
import Ajv2020 from "ajv/dist/2020";
import addFormats from "ajv-formats";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import schema from "../schema/payload.schema.json";
import { collect } from "./main";
import { build } from "./payload";
import { parseTeamclaude, readTap } from "./sources";

const ajv = new Ajv2020({ allErrors: true });
addFormats(ajv);
const validate = ajv.compile(schema);
const valid = (p: unknown) => {
  const ok = validate(p);
  if (!ok) console.error(validate.errors);
  return ok;
};

const quota = {
  accounts: [
    { name: "someone@example.com", disabled: false, buckets: {
      fiveHour: { utilization: 0.02, resetAt: 1790712600000 },
      weeklyShared: { utilization: 0.98, resetAt: 1790827199589 } } },
    { name: "off@example.com", disabled: true, buckets: {} },
  ],
  aggregate: {
    fiveHour: { utilization: 0.006666666667, nextResetAt: 1790705399651 },
    weeklyShared: { utilization: 0.42, nextResetAt: 1790827199589 },
  },
};

function fakeProxy(status: number, body: unknown) {
  return Bun.serve({
    port: 0,
    hostname: "127.0.0.1",
    fetch(req) {
      if (req.headers.get("x-api-key") !== "k") return new Response("{}", { status: 401 });
      return new Response(JSON.stringify(body), { status });
    },
  });
}

describe("teamclaude parsing", () => {
  test("aggregate 0-1 becomes 0-100 with ISO resets", () => {
    const r = parseTeamclaude(quota);
    expect(r.fiveHour).toEqual({ usedPercentage: 0.7, resetsAt: new Date(1790705399651).toISOString() });
    expect(r.weekly?.usedPercentage).toBe(42);
  });

  test("disabled accounts dropped, names never carried", () => {
    const r = parseTeamclaude(quota);
    expect(r.accounts).toHaveLength(1);
    expect(r.accounts![0].weekly?.usedPercentage).toBe(98);
    expect(JSON.stringify(r)).not.toContain("example.com");
  });

  test("missing aggregate is schema drift, not zero", () => {
    expect(() => parseTeamclaude({ accounts: [] })).toThrow("teamclaude:schema-drift");
  });
});

describe("payload", () => {
  test("no source yields source none, stale, and validates", () => {
    const p = build(null, ["statusline-tap:missing"], 900);
    expect(p.source).toBe("none");
    expect(p.stale).toBe(true);
    expect(p.fiveHour).toBeNull();
    expect(valid(p)).toBe(true);
  });

  test("old observation is flagged stale", () => {
    const now = new Date("2026-09-29T12:00:00Z");
    const r = { source: "statusline-tap" as const, observedAt: "2026-09-29T11:00:00Z", fiveHour: null, weekly: null };
    const p = build(r, [], 900, now);
    expect(p.ageSeconds).toBe(3600);
    expect(p.stale).toBe(true);
  });
});

describe("tap source", () => {
  test("reads the tap file", () => {
    const dir = mkdtempSync(join(tmpdir(), "clip-"));
    writeFileSync(join(dir, "usage.json"), JSON.stringify({
      tapSchemaVersion: 1, observedAt: new Date().toISOString(), claudeCodeVersion: "2.1.273",
      fiveHour: { usedPercentage: 34, resetsAt: null }, sevenDay: null,
    }));
    const r = readTap(dir);
    expect(r.fiveHour?.usedPercentage).toBe(34);
    expect(r.claudeCodeVersion).toBe("2.1.273");
  });

  test("unknown tap schema is drift", () => {
    const dir = mkdtempSync(join(tmpdir(), "clip-"));
    writeFileSync(join(dir, "usage.json"), JSON.stringify({ tapSchemaVersion: 9 }));
    expect(() => readTap(dir)).toThrow("statusline-tap:schema-drift");
  });
});

describe("collect end to end", () => {
  let ok: ReturnType<typeof fakeProxy>;
  beforeAll(() => { ok = fakeProxy(200, quota); });
  afterAll(() => ok.stop(true));
  const emptyTap = () => mkdtempSync(join(tmpdir(), "clip-"));

  test("teamclaude wins and payload validates", async () => {
    const p = await collect({ staleSeconds: 900, teamclaude: { url: ok.url.href, key: "k" }, tapDir: emptyTap() });
    expect(p.source).toBe("teamclaude");
    expect(p.stale).toBe(false);
    expect(p.errors).toEqual([]);
    expect(valid(p)).toBe(true);
  });

  test("bad key falls through with an error code", async () => {
    const p = await collect({ staleSeconds: 900, teamclaude: { url: ok.url.href, key: "wrong" }, tapDir: emptyTap() });
    expect(p.source).toBe("none");
    expect(p.errors).toEqual(["teamclaude:http-401", "statusline-tap:missing"]);
    expect(valid(p)).toBe(true);
  });

  test("unreachable proxy does not throw", async () => {
    const p = await collect({ staleSeconds: 900, teamclaude: { url: "http://127.0.0.1:9", key: "k" }, tapDir: emptyTap() });
    expect(p.errors[0]).toBe("teamclaude:unreachable");
    expect(valid(p)).toBe(true);
  });
});
