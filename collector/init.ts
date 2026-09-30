// `mana-collector init`: wrap the user's statusLine with the tap, create the token,
// choose how the phone reaches this machine, and install the service. `--undo` puts the
// statusLine back exactly as it was.
//
// The settings edits are pure functions over parsed JSON so they can be tested without
// touching a real ~/.claude.

import crypto from "node:crypto";
import { chmodSync, copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { parseArgs } from "node:util";
import { configDir, loadConfig, saveConfig, type Config } from "./config";
import { installService, uninstallService } from "./service";
import { printPairing } from "./pair";

export function settingsPath(): string {
  const base = process.env.CLAUDE_CONFIG_DIR || join(homedir(), ".claude");
  return join(base, "settings.json");
}

// Absolute paths on both sides, because Claude Code runs the command from whatever
// directory the session is in, often with a thin PATH.
export function tapCommand(bun: string, cli: string): string {
  return `"${bun}" "${cli}" tap`;
}

export interface Wrapped {
  settings: Record<string, any>;
  original: unknown;
  downstream?: string;
}

export function isWrapped(settings: Record<string, any>, command: string): boolean {
  return settings.statusLine?.type === "command" && settings.statusLine?.command === command;
}

export function wrap(settings: Record<string, any>, command: string): Wrapped {
  const original = settings.statusLine ?? null;
  const downstream =
    original && original.type === "command" && typeof original.command === "string" ? original.command : undefined;
  // Keep padding and any other field the user set; only the command changes.
  const statusLine = { ...(original && typeof original === "object" ? original : {}), type: "command", command };
  return { settings: { ...settings, statusLine }, original, downstream };
}

export function unwrap(settings: Record<string, any>, original: unknown): Record<string, any> {
  const out = { ...settings };
  if (original === null || original === undefined) delete out.statusLine;
  else out.statusLine = original;
  return out;
}

export function newToken(): string {
  return crypto.randomBytes(24).toString("base64url");
}

function readSettings(path: string): Record<string, any> {
  if (!existsSync(path)) return {};
  return JSON.parse(readFileSync(path, "utf8"));
}

function writeSettings(path: string, settings: Record<string, any>): void {
  // A dated copy first: settings.json holds far more than the statusLine.
  if (existsSync(path)) {
    mkdirSync(configDir(), { recursive: true, mode: 0o700 });
    // settings.json can hold API keys in its env block, so the copy is owner-only.
    const bak = join(configDir(), `settings.json.bak-${Date.now()}`);
    copyFileSync(path, bak);
    if (process.platform !== "win32") chmodSync(bak, 0o600);
  }
  writeFileSync(path, JSON.stringify(settings, null, 2) + "\n");
}

interface TailscaleSelf {
  ip?: string;
  dnsName?: string;
}

function tailscaleSelf(): TailscaleSelf | null {
  try {
    const p = Bun.spawnSync(["tailscale", "status", "--json"], { stdout: "pipe", stderr: "ignore" });
    if (p.exitCode !== 0) return null;
    const self = JSON.parse(p.stdout.toString()).Self;
    return { ip: self?.TailscaleIPs?.[0], dnsName: String(self?.DNSName ?? "").replace(/\.$/, "") || undefined };
  } catch {
    return null;
  }
}

// bunx runs from a throwaway cache. A statusLine pointing there breaks when the cache
// is cleaned, so init insists on an installed copy.
function looksEphemeral(path: string): boolean {
  return /[\\/]bunx-[^\\/]*[\\/]|[\\/]\.bun[\\/]install[\\/]cache[\\/]/.test(path);
}

const HELP = `mana-collector init [options]

  --tailscale      serve on loopback and publish over HTTPS with tailscale serve (default when tailscale is up)
  --bind <ip>      listen on this address instead, over plain HTTP (a LAN or tailnet IP)
  --port <n>       port (default 7337)
  --url <url>      the URL the phone should use, if the detected one is wrong
  --no-service     do not install the background service
  --undo           restore the statusLine and remove the service
`;

export async function init(args: string[]): Promise<void> {
  const { values } = parseArgs({
    args,
    options: {
      tailscale: { type: "boolean", default: false },
      bind: { type: "string" },
      port: { type: "string" },
      url: { type: "string" },
      "no-service": { type: "boolean", default: false },
      undo: { type: "boolean", default: false },
      help: { type: "boolean", short: "h", default: false },
    },
  });
  if (values.help) return void console.log(HELP);
  if (values.undo) return undo();

  const cli = resolve(import.meta.dir, "..", "cli", "mana.ts");
  if (looksEphemeral(cli)) {
    console.error("Install first so the status line has a stable path:\n  bun add -g mana-collector\n  mana-collector init");
    process.exit(2);
  }
  const command = tapCommand(process.execPath, cli);
  const cfg: Config = loadConfig();

  // 1. statusLine
  const path = settingsPath();
  const settings = readSettings(path);
  if (isWrapped(settings, command)) {
    console.log(`statusLine already runs the tap (${path})`);
  } else {
    const w = wrap(settings, command);
    writeSettings(path, w.settings);
    cfg.downstream = w.downstream;
    cfg.statusLine = { original: w.original, settingsPath: path, wrappedCommand: command };
    console.log(`statusLine now runs the tap${w.downstream ? " and then your existing command" : ""} (${path})`);
  }

  // 2. token
  if (!cfg.token || cfg.token.length < 16) cfg.token = newToken();

  // 3. how the phone gets here
  const port = Number(values.port ?? cfg.port ?? 7337);
  const ts = values.bind ? null : tailscaleSelf();
  cfg.port = port;
  if (values.bind) {
    cfg.bind = values.bind;
    cfg.url = values.url ?? `http://${values.bind}:${port}/v1/usage`;
  } else if (values.tailscale || ts?.dnsName) {
    cfg.bind = "127.0.0.1";
    cfg.url = values.url ?? (ts?.dnsName ? `https://${ts.dnsName}:${port}/v1/usage` : undefined);
    console.log(
      `\nPublish it on your tailnet over HTTPS (one time):\n  tailscale serve --bg --https=${port} http://127.0.0.1:${port}\n`,
    );
  } else {
    console.error(
      "No Tailscale found. Pass --bind with this machine's LAN address, for example:\n  mana-collector init --bind 192.168.1.20",
    );
    saveConfig(cfg);
    process.exit(2);
  }
  saveConfig(cfg);
  console.log(`config saved (${configDir()})`);

  // 4. service
  if (!values["no-service"]) await installService(process.execPath, cli);

  if (cfg.url) printPairing(cfg.url, cfg.token);
  else console.log("Set the phone URL with: mana-collector init --url <url>");
}

async function undo(): Promise<void> {
  const cfg = loadConfig();
  const backup = cfg.statusLine;
  if (backup) {
    const settings = readSettings(backup.settingsPath);
    if (!isWrapped(settings, backup.wrappedCommand)) {
      console.error(
        `statusLine in ${backup.settingsPath} was changed after init, so it is left alone.\n` +
          `Your original was: ${JSON.stringify(backup.original)}`,
      );
    } else {
      writeSettings(backup.settingsPath, unwrap(settings, backup.original));
      console.log(`statusLine restored (${backup.settingsPath})`);
    }
    delete cfg.statusLine;
    delete cfg.downstream;
    saveConfig(cfg);
  } else {
    console.log("init never wrapped a statusLine here; nothing to restore");
  }
  await uninstallService();
}
