import { describe, expect, test } from "bun:test";
import { mkdtempSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { extract, record, shapeOf } from "./tap";

const sample = {
  session_id: "abc",
  transcript_path: "/home/someone/secret/project.jsonl",
  version: "2.1.273",
  model: { id: "claude-opus-5-5", display_name: "Opus" },
  rate_limits: {
    five_hour: { used_percentage: 34, resets_at: 1790000000 },
    seven_day: { used_percentage: 61.5, resets_at: 1790500000 },
  },
};

describe("extract", () => {
  test("reads both windows and converts epoch seconds to ISO", () => {
    const r = extract(sample, new Date("2026-09-29T12:00:00Z"))!;
    expect(r.fiveHour).toEqual({ usedPercentage: 34, resetsAt: "2026-09-21T14:13:20.000Z" });
    expect(r.sevenDay?.usedPercentage).toBe(61.5);
    expect(r.claudeCodeVersion).toBe("2.1.273");
    expect(r.observedAt).toBe("2026-09-29T12:00:00.000Z");
  });

  test("absent rate_limits returns null, not zero", () => {
    expect(extract({ version: "2.1.273" })).toBeNull();
  });

  test("one missing window keeps the other", () => {
    const r = extract({ rate_limits: { seven_day: { used_percentage: 5, resets_at: 1 } } })!;
    expect(r.fiveHour).toBeNull();
    expect(r.sevenDay?.usedPercentage).toBe(5);
  });

  test("out-of-range percentage is rejected", () => {
    expect(extract({ rate_limits: { five_hour: { used_percentage: 140 } } })).toBeNull();
  });

  test("bad resets_at yields null reset, keeps percentage", () => {
    const r = extract({ rate_limits: { five_hour: { used_percentage: 3, resets_at: "soon" } } })!;
    expect(r.fiveHour).toEqual({ usedPercentage: 3, resetsAt: null });
  });
});

describe("privacy", () => {
  test("shape carries no values", () => {
    const s = JSON.stringify(shapeOf(sample));
    expect(s).not.toContain("secret");
    expect(s).not.toContain("abc");
    expect(s).not.toContain("opus");
  });

  test("usage record carries no path, session or model", () => {
    const s = JSON.stringify(extract(sample));
    for (const bad of ["secret", "abc", "opus", "transcript"]) expect(s).not.toContain(bad);
  });
});

describe("record", () => {
  test("writes usage.json and stdin-shape.json", () => {
    const dir = mkdtempSync(join(tmpdir(), "clip-"));
    record(JSON.stringify(sample), dir);
    expect(JSON.parse(readFileSync(join(dir, "usage.json"), "utf8")).fiveHour.usedPercentage).toBe(34);
    expect(existsSync(join(dir, "stdin-shape.json"))).toBe(true);
  });

  test("malformed stdin writes nothing and does not throw", () => {
    const dir = mkdtempSync(join(tmpdir(), "clip-"));
    expect(() => record("{not json", dir)).not.toThrow();
    expect(existsSync(join(dir, "usage.json"))).toBe(false);
  });
});
