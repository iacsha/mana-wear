import { describe, expect, test } from "bun:test";
import { mkdtempSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { isWrapped, newToken, tapCommand, unwrap, wrap } from "./init";
import { loadConfig, saveConfig } from "./config";
import { launchdPlist, schtasksCreate, systemdUnit } from "./service";
import { pairingUri } from "./pair";
import { downstreamArgv } from "../tap/tap";

const CMD = tapCommand("/usr/bin/bun", "/opt/mana/cli/mana.ts");

describe("statusLine wrap", () => {
  test("keeps the user's command as downstream and their other fields", () => {
    const before = { theme: "dark", statusLine: { type: "command", command: "node ~/sl.js", padding: 1 } };
    const w = wrap(before, CMD);
    expect(w.downstream).toBe("node ~/sl.js");
    expect(w.settings.statusLine).toEqual({ type: "command", command: CMD, padding: 1 });
    expect(w.settings.theme).toBe("dark");
    expect(isWrapped(w.settings, CMD)).toBe(true);
  });

  test("undo restores the original statusLine exactly", () => {
    const before = { theme: "dark", statusLine: { type: "command", command: "node ~/sl.js", padding: 1 } };
    const w = wrap(before, CMD);
    expect(JSON.stringify(unwrap(w.settings, w.original))).toBe(JSON.stringify(before));
  });

  test("no statusLine before means none after undo", () => {
    const w = wrap({ theme: "dark" }, CMD);
    expect(w.downstream).toBeUndefined();
    expect(unwrap(w.settings, w.original)).toEqual({ theme: "dark" });
  });

  test("a user edit after init is not mistaken for ours", () => {
    expect(isWrapped({ statusLine: { type: "command", command: "other" } }, CMD)).toBe(false);
  });
});

describe("token and pairing", () => {
  test("tokens are long, url-safe and unique", () => {
    const a = newToken();
    expect(a).toMatch(/^[A-Za-z0-9_-]{32}$/);
    expect(newToken()).not.toBe(a);
  });

  test("pairing URI percent-encodes both fields", () => {
    expect(pairingUri("https://pc.tail1.ts.net:7337/v1/usage", "a+b/c")).toBe(
      "mana://pair?v=1&url=https%3A%2F%2Fpc.tail1.ts.net%3A7337%2Fv1%2Fusage&token=a%2Bb%2Fc",
    );
  });
});

describe("config", () => {
  test("saved owner-only and read back", () => {
    const dir = mkdtempSync(join(tmpdir(), "mana-cfg-"));
    saveConfig({ token: "x".repeat(32), port: 7337 }, dir);
    expect(loadConfig(dir)).toEqual({ token: "x".repeat(32), port: 7337 });
    expect(statSync(join(dir, "config.json")).mode & 0o777).toBe(0o600);
  });

  test("a pre-rename bare token file still loads", () => {
    const dir = mkdtempSync(join(tmpdir(), "mana-cfg-"));
    writeFileSync(join(dir, "token"), "legacy-token-1234567890\n");
    expect(loadConfig(dir)).toEqual({ token: "legacy-token-1234567890" });
  });
});

describe("tap downstream", () => {
  test("argv on the command line wins", () => {
    expect(downstreamArgv(["node", "sl.js"], "ignored")).toEqual(["node", "sl.js"]);
    expect(downstreamArgv(["--", "node", "sl.js"])).toEqual(["node", "sl.js"]);
  });

  test("saved command runs through the shell", () => {
    const argv = downstreamArgv([], "node ~/sl.js | cat");
    expect(argv.at(-1)).toBe("node ~/sl.js | cat");
  });

  test("nothing to run", () => {
    expect(downstreamArgv([])).toEqual([]);
  });
});

describe("service files", () => {
  test("systemd unit runs serve with absolute paths", () => {
    expect(systemdUnit("/usr/bin/bun", "/opt/m/cli/mana.ts")).toContain('ExecStart="/usr/bin/bun" "/opt/m/cli/mana.ts" serve');
  });

  test("launchd plist escapes paths", () => {
    const p = launchdPlist("/b/bun", "/Users/a&b/cli/mana.ts", "/tmp/log");
    expect(p).toContain("<string>/Users/a&amp;b/cli/mana.ts</string>");
    expect(p).toContain("<key>KeepAlive</key><true/>");
  });

  test("windows task starts headless at logon", () => {
    const argv = schtasksCreate("C:\\bun\\bun.exe", "C:\\m\\cli\\mana.ts");
    expect(argv).toContain("ONLOGON");
    expect(argv.at(-1)).toBe('conhost.exe --headless "C:\\bun\\bun.exe" "C:\\m\\cli\\mana.ts" serve');
  });
});
