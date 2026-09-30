#!/usr/bin/env bun
// mana-collector: the one command a Mana user installs on the machine that runs
// Claude Code.

import { init } from "../collector/init";
import { serve } from "../collector/main";
import { pair } from "../collector/pair";
import { runTap } from "../tap/tap";

const HELP = `mana-collector <command>

  init     wrap your statusLine, create a token, install the service, show the pairing code
  pair     show the pairing code again
  serve    run the collector in the foreground (the service runs this)
  tap      statusLine command; init wires it up
  init --undo   put everything back
`;

const [cmd, ...rest] = process.argv.slice(2);
switch (cmd) {
  case "init":
    await init(rest);
    break;
  case "pair":
    await pair(rest);
    break;
  case "serve":
    await serve(rest);
    break;
  case "tap":
    await runTap(rest);
    break;
  default:
    console.log(HELP);
    if (cmd && cmd !== "help" && cmd !== "--help" && cmd !== "-h") process.exitCode = 2;
}
