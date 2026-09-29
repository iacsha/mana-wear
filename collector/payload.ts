// The one JSON document the collector serves. Watch and phone code depend on this
// shape, so any breaking change bumps SCHEMA_VERSION and schema/payload.schema.json.

export const SCHEMA_VERSION = 1;
export const COLLECTOR_VERSION = "0.1.0";

export type SourceName = "teamclaude" | "statusline-tap" | "none";

export interface Window {
  // 0-100, the vendor's own figure. Never derived from token counts.
  usedPercentage: number;
  // ISO-8601, or null when the source gives no reset time.
  resetsAt: string | null;
}

export interface Reading {
  source: Exclude<SourceName, "none">;
  // When the figures were observed at the source, ISO-8601.
  observedAt: string;
  fiveHour: Window | null;
  weekly: Window | null;
  // Per-account breakdown for pooled sources. Index only, never account names.
  accounts?: { fiveHour: Window | null; weekly: Window | null }[];
  claudeCodeVersion?: string | null;
}

export interface Payload {
  schemaVersion: number;
  collectorVersion: string;
  generatedAt: string;
  source: SourceName;
  observedAt: string | null;
  ageSeconds: number | null;
  stale: boolean;
  fiveHour: Window | null;
  weekly: Window | null;
  accounts: { fiveHour: Window | null; weekly: Window | null }[];
  claudeCodeVersion: string | null;
  // Short machine codes for sources that failed, e.g. "teamclaude:http-401".
  errors: string[];
}

export function build(
  reading: Reading | null,
  errors: string[],
  staleAfterSeconds: number,
  now: Date = new Date(),
): Payload {
  const base = {
    schemaVersion: SCHEMA_VERSION,
    collectorVersion: COLLECTOR_VERSION,
    generatedAt: now.toISOString(),
    errors,
  };
  if (!reading) {
    // No source answered. Say so explicitly instead of reporting zero usage.
    return {
      ...base,
      source: "none",
      observedAt: null,
      ageSeconds: null,
      stale: true,
      fiveHour: null,
      weekly: null,
      accounts: [],
      claudeCodeVersion: null,
    };
  }
  const age = Math.max(0, Math.round((now.getTime() - Date.parse(reading.observedAt)) / 1000));
  return {
    ...base,
    source: reading.source,
    observedAt: reading.observedAt,
    ageSeconds: age,
    stale: age > staleAfterSeconds,
    fiveHour: reading.fiveHour,
    weekly: reading.weekly,
    accounts: reading.accounts ?? [],
    claudeCodeVersion: reading.claudeCodeVersion ?? null,
  };
}
