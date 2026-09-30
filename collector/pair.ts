// `mana-collector pair`: a QR code the phone app scans, so nobody types a token.
// The phone parses the same URI shape in :shared (parsePairingUri).

import { renderUnicodeCompact } from "uqr";
import { loadConfig } from "./config";

export function pairingUri(url: string, token: string): string {
  return `mana://pair?v=1&url=${encodeURIComponent(url)}&token=${encodeURIComponent(token)}`;
}

export function printPairing(url: string, token: string): void {
  // The QR holds the token, so it is only ever shown on this terminal, never written out.
  console.log("\nIn the Mana phone app, tap Scan pairing code and point it at this:\n");
  console.log(renderUnicodeCompact(pairingUri(url, token), { ecc: "M", border: 2 }));
  console.log(`\nURL: ${url}`);
  console.log("Token: run `mana-collector pair --show-token` if you need to type it.\n");
}

export async function pair(args: string[]): Promise<void> {
  const cfg = loadConfig();
  if (!cfg.url || !cfg.token) {
    console.error("Not set up yet. Run: mana-collector init");
    process.exit(2);
  }
  if (args.includes("--show-token")) return void console.log(cfg.token);
  printPairing(cfg.url, cfg.token);
}
