// Keeps `mana-collector serve` running across logins: a systemd user unit on Linux, a
// launchd agent on macOS, a logon task on Windows. The file generators are pure; the
// install step writes the file and asks the OS to load it.

import { existsSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { stateDir } from "./config";

export const LABEL = "io.github.manawear.collector";
export const UNIT = "mana-collector.service";
export const TASK = "Mana Collector";

export function systemdUnit(bun: string, cli: string): string {
  return `[Unit]
Description=Mana collector (Claude Code usage for Wear OS)
After=network-online.target

[Service]
ExecStart="${bun}" "${cli}" serve
Restart=on-failure
RestartSec=10

[Install]
WantedBy=default.target
`;
}

function xml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function launchdPlist(bun: string, cli: string, log: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>${LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>${xml(bun)}</string>
    <string>${xml(cli)}</string>
    <string>serve</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardErrorPath</key><string>${xml(log)}</string>
</dict>
</plist>
`;
}

// conhost --headless keeps a console window from flashing up at every logon.
export function schtasksCreate(bun: string, cli: string): string[] {
  const run = `conhost.exe --headless "${bun}" "${cli}" serve`;
  return ["schtasks", "/Create", "/F", "/SC", "ONLOGON", "/RL", "LIMITED", "/TN", TASK, "/TR", run];
}

function run(argv: string[]): boolean {
  try {
    return Bun.spawnSync(argv, { stdout: "ignore", stderr: "ignore" }).exitCode === 0;
  } catch {
    return false;
  }
}

function systemdPath(): string {
  const base = process.env.XDG_CONFIG_HOME || join(homedir(), ".config");
  return join(base, "systemd", "user", UNIT);
}

function launchdPath(): string {
  return join(homedir(), "Library", "LaunchAgents", `${LABEL}.plist`);
}

function write(path: string, body: string): void {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, body);
}

export async function installService(bun: string, cli: string): Promise<boolean> {
  const manual = `"${bun}" "${cli}" serve`;
  switch (process.platform) {
    case "linux": {
      write(systemdPath(), systemdUnit(bun, cli));
      if (run(["systemctl", "--user", "daemon-reload"]) && run(["systemctl", "--user", "enable", "--now", UNIT])) {
        console.log(`service running (systemd user unit ${UNIT})`);
        console.log("To keep it running while you are logged out: loginctl enable-linger $USER");
        return true;
      }
      console.error(
        `No systemd user session here. Unit written to ${systemdPath()}.\n` +
          `Start it another way, for example a crontab line:\n  @reboot ${manual}`,
      );
      return false;
    }
    case "darwin": {
      const log = join(stateDir(), "collector.log");
      write(launchdPath(), launchdPlist(bun, cli, log));
      const uid = process.getuid?.() ?? 0;
      run(["launchctl", "bootout", `gui/${uid}/${LABEL}`]);
      if (run(["launchctl", "bootstrap", `gui/${uid}`, launchdPath()])) {
        console.log(`service running (launchd agent ${LABEL})`);
        return true;
      }
      console.error(`launchctl refused ${launchdPath()}. Run it yourself: ${manual}`);
      return false;
    }
    case "win32": {
      if (run(schtasksCreate(bun, cli)) && run(["schtasks", "/Run", "/TN", TASK])) {
        console.log(`service running (scheduled task "${TASK}", starts at logon)`);
        return true;
      }
      console.error(`schtasks failed. Run it yourself: ${manual}`);
      return false;
    }
    default:
      console.error(`No service support for ${process.platform}. Run it yourself: ${manual}`);
      return false;
  }
}

export async function uninstallService(): Promise<void> {
  let removed = false;
  switch (process.platform) {
    case "linux":
      run(["systemctl", "--user", "disable", "--now", UNIT]);
      if (existsSync(systemdPath())) {
        rmSync(systemdPath());
        removed = true;
      }
      run(["systemctl", "--user", "daemon-reload"]);
      break;
    case "darwin":
      run(["launchctl", "bootout", `gui/${process.getuid?.() ?? 0}/${LABEL}`]);
      if (existsSync(launchdPath())) {
        rmSync(launchdPath());
        removed = true;
      }
      break;
    case "win32":
      run(["schtasks", "/End", "/TN", TASK]);
      removed = run(["schtasks", "/Delete", "/F", "/TN", TASK]);
      break;
  }
  console.log(removed ? "service removed" : "no service was installed");
}
