// Where Mana keeps its state and its one config file, and the move from the old
// claude-clip directories. Everything here is cheap because the tap calls it on every
// status line render.

import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const APP = "mana";
const LEGACY = "claude-clip";

function windows(): boolean {
  return process.platform === "win32";
}

// Pre-rename installs used claude-clip directories. Move one over the first time the
// new name is asked for, so a token and a tap reading survive the upgrade.
function migrated(base: string): string {
  const dir = join(base, APP);
  const old = join(base, LEGACY);
  if (!existsSync(dir) && existsSync(old)) {
    try {
      renameSync(old, dir);
    } catch {
      return old;
    }
  }
  return dir;
}

export function stateDir(): string {
  if (windows()) return migrated(join(process.env.LOCALAPPDATA || join(homedir(), "AppData", "Local")));
  return migrated(process.env.XDG_STATE_HOME || join(homedir(), ".local", "state"));
}

export function configDir(): string {
  if (windows()) return migrated(process.env.APPDATA || join(homedir(), "AppData", "Roaming"));
  return migrated(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"));
}

export interface StatusLineBackup {
  // The statusLine value found in settings.json before init, or null when there was none.
  original: unknown;
  settingsPath: string;
  wrappedCommand: string;
}

export interface Config {
  token?: string;
  bind?: string;
  port?: number;
  // The URL the phone should use, printed in the pairing code.
  url?: string;
  // The user's own statusLine command. The tap runs it through the shell and relays its
  // output, so wrapping never changes what the status line shows.
  downstream?: string;
  statusLine?: StatusLineBackup;
  teamclaude?: { url: string; key: string };
}

export function configPath(dir: string = configDir()): string {
  return join(dir, "config.json");
}

export function loadConfig(dir: string = configDir()): Config {
  try {
    return JSON.parse(readFileSync(configPath(dir), "utf8")) as Config;
  } catch {
    // A pre-rename install kept only a bare token file.
    try {
      const token = readFileSync(join(dir, "token"), "utf8").trim();
      return token ? { token } : {};
    } catch {
      return {};
    }
  }
}

export function saveConfig(cfg: Config, dir: string = configDir()): void {
  mkdirSync(dir, { recursive: true, mode: 0o700 });
  // The token is a credential: owner-only, like an ssh key.
  writeFileSync(configPath(dir), JSON.stringify(cfg, null, 2) + "\n", { mode: 0o600 });
  // mode only applies when the file is created.
  if (!windows()) chmodSync(configPath(dir), 0o600);
}
