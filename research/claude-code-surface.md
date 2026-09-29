# Claude Code surface for claude-clip (first-party sources only)

Researched 2026-09-29. Sources: code.claude.com docs (statusline, hooks, cli-reference, headless, costs, legal-and-compliance, agent-sdk/typescript) and anthropics/claude-code CHANGELOG.md (raw.githubusercontent.com/anthropics/claude-code/main/CHANGELOG.md). Nothing was executed against the `claude` binary.

## 1. statusLine stdin: `rate_limits`

**YES.** `rate_limits` has `five_hour` and `seven_day`, each with `used_percentage` and `resets_at`.

Source: https://code.claude.com/docs/en/statusline (Available data)
- "`rate_limits.five_hour.used_percentage`, `rate_limits.seven_day.used_percentage` | Percentage of the 5-hour or 7-day rate limit consumed, from 0 to 100"
- "`rate_limits.five_hour.resets_at`, `rate_limits.seven_day.resets_at` | Unix epoch seconds when the 5-hour or 7-day rate limit window resets"
- Units: epoch SECONDS (not ms, not ISO). Doc example: `"resets_at": 1738425600`.

Plans / presence (same page, field notes):
- "`rate_limits`: appears only for claude.ai Pro and Max subscribers, or behind a Claude apps gateway that sets a spend limit for you, and only after the first API response in the session. Each window (`five_hour`, `seven_day`, `spend_limit`) may be independently absent, and Claude Code drops a window once its `resets_at` time passes."
- So: Pro/Max only (Team/Enterprise not listed there); API-key users: absent by implication (not explicitly named). Not present before the first API response. Each window can be absent independently; handle with `jq '.rate_limits.five_hour.used_percentage // empty'`.
- Also documented: `rate_limits.spend_limit.{used_percentage,resets_at}` (gateway users, v2.1.251+; % can exceed 100). CHANGELOG 2.1.284 adds `used_usd`, `limit_usd`, `period` to `spend_limit`.

Version added: CHANGELOG 2.1.80: "Added `rate_limits` field to statusline scripts for displaying Claude.ai rate limit usage (5-hour and 7-day windows with `used_percentage` and `resets_at`)".
Related fix, 2.1.x (heading not captured): "Fixed the status line `rate_limits` fields and `/usage` still showing a rate-limit window's pre-reset usage percentage after the window reset while the session was idle".

Other documented top-level stdin fields (same table): `model.{id,display_name}`, `cwd`, `workspace.{current_dir,project_dir,added_dirs,git_worktree,repo.{host,owner,name}}`, `cost.{total_cost_usd,total_duration_ms,total_api_duration_ms,total_lines_added,total_lines_removed}`, `context_window.{total_input_tokens,total_output_tokens,context_window_size,used_percentage,remaining_percentage,current_usage}`, `exceeds_200k_tokens`, `fast_mode`, `effort.level`, `thinking.enabled`, `rate_limits`, `prompt_cache` (v2.1.251+), `session_id`, `session_name`, `prompt_id` (v2.1.196+), `transcript_path`, `version`, `vim.mode`, `pr.{number,url,kind,...}`, plus `worktree.*` and agent fields. Full JSON example on the page includes a `rate_limits` block with `five_hour`, `seven_day` and `spend_limit`.

## 2. When the statusLine command runs; headless

**YES to event-driven and timer; UNKNOWN for `claude -p`.**

Source: https://code.claude.com/docs/en/statusline (How status lines work)
- "Your script runs once when a session starts, including when you resume one. After that, it runs again when: A new assistant message arrives / `/compact` finishes / The permission mode changes / Vim mode toggles / You change the `command` in your `statusLine` settings / A `refreshInterval` timer elapses, if you set one / A rate-limit window in the data your script last received reaches its `resets_at` time / A warm prompt cache ... reaches its `expires_at` time"
- "Claude Code debounces updates at 300ms ... If a new update triggers while your script is still running, Claude Code cancels the in-flight script."
- `refreshInterval`: "The optional `refreshInterval` field re-runs your command every N seconds in addition to the event-driven updates. The minimum is `1`." Leave unset to run only on events. CHANGELOG 2.1.97: "Added `refreshInterval` status line setting to re-run the status line command every N seconds".
- Gating: "Until then [workspace trust accepted], the status line stays blank".
- "The status line runs locally and does not consume API tokens."
- Headless: no first-party doc says the statusLine runs (or not) in `claude -p`. The statusline page is written entirely about the interactive bar ("It temporarily hides during certain UI interactions"). Treat as UNKNOWN; do not rely on it. (`--safe-mode` doc confirms status line commands are a loadable customization; `--bare` skips hooks etc., statusLine not named.)

## 3. Hook input JSON and rate-limit / usage data

**NO for rate limits on every hook event.** https://code.claude.com/docs/en/hooks
Hook events (33 in page's cadence table): SessionStart, Setup, SessionEnd, UserPromptSubmit, UserPromptExpansion, Stop, StopFailure, PreToolUse, PermissionRequest, PermissionDenied, PostToolUse, PostToolUseFailure, PostToolBatch, SubagentStart, SubagentStop, TaskCreated, TaskCompleted, TeammateIdle, PreCompact, PostCompact, PreModelSwitch, PostModelSwitch, Elicitation, ElicitationResult, InstructionsLoaded, ConfigChange, CwdChanged, DirectoryAdded, FileChanged, WorktreeCreate, WorktreeRemove, Notification, MessageDisplay.
Common fields: `session_id`, `prompt_id`, `transcript_path`, `cwd`, `scratchpad_dir`, `permission_mode`, `effort`, `hook_event_name`, `agent_id`, `agent_type`. None is a rate-limit/plan-usage figure.

Per event, only these carry anything usage-adjacent, none give plan percentage or reset time:
- SessionStart (resume/fork, v2.1.251+): `seconds_since_last_response`, `context_tokens`, `prompt_cache_likely_expired`, `estimated_cache_write_usd`. Cache/context cost, NOT plan limits.
- PostToolUse (Agent tool): `tool_response.usage` = "Per-type token breakdown of the final API request". Per-subagent tokens, not plan limits.
- StopFailure: `error` field, value `rate_limit` among types. Signals a hit limit, no numbers/reset.
- Notification: types `quota_auto_resume_fired`, `quota_auto_resume_stale`, `quota_auto_resume_disabled`. Event only, no numbers.
- Stop and all others: no rate-limit/usage field documented (`rg -i rate_limits` on hooks.md returns no hits).
Practical route: a hook can read nothing about plan usage; use statusLine, or Stop hook plus own read of a statusLine-written cache file.

## 4. Non-interactive CLI to print plan usage

**NO** `claude usage` subcommand. Full subcommand list in https://code.claude.com/docs/en/cli-reference: update, gateway, install, auth login/logout/status, agents, attach, auto-mode, daemon, doctor, import, logs, mcp, plugin, project purge, remote-control, respawn, rm, self-hosted-runner, setup-token, stop, ultrareview. No `usage`.
- `claude auth status` prints auth state as JSON only (login/`configDirectory`), no usage.
- **NO** documented `/usage` via `-p` returning plan limits. headless.md: "Built-in commands that only run in the terminal interface, such as `/login`, aren't available." `/usage` is documented as an interactive dialog (costs page, "Using the `/usage` command"). Whether `claude -p "/usage"` works is UNKNOWN (undocumented).
- **NO** JSON flag for plan usage. `--output-format json` gives `total_cost_usd` and per-model cost, "client-side estimates", not plan windows.
- **YES (partial, indirect):** SDK stream emits rate limit info. https://code.claude.com/docs/en/agent-sdk/typescript: `SDKRateLimitEvent` = `{ type: "rate_limit_event", rate_limit_info: { status: "allowed" | "allowed_warning" | "rejected", resetsAt?: number, utilization?: number, ... } }`, "Emitted when the session encounters a rate limit." CHANGELOG 2.1.45: "Added `SDKRateLimitInfo` and `SDKRateLimitEvent` types to the SDK, enabling consumers to receive rate limit status updates including utilization, reset times, and overage information". Requires a running session (`-p --output-format stream-json`); not a query command, and emission cadence is not documented.

## 5. `api.anthropic.com/api/oauth/usage` and third-party OAuth use

**NO** first-party documentation of the endpoint. Zero hits for `oauth/usage` / `api/oauth` in CHANGELOG.md, legal-and-compliance, hooks, headless, statusline docs. The CHANGELOG only refers to it anonymously: "Improved plan-usage reads: editor windows and non-interactive sessions on one machine now share a read made in the last minute instead of each calling the usage endpoint" and "Fixed repeated calls to the plan-usage endpoint after it rate-limits or rejects your login" (2.1.284). It is an internal endpoint; treat as unsupported/undocumented. Also note: `/usage` "shows the last usage bars it loaded ... within the past 60 minutes" when the endpoint is rate limited (costs page), i.e. it does get throttled.

**YES** Anthropic has an explicit policy statement. https://code.claude.com/docs/en/legal-and-compliance ("Authentication and credential use"):
- "OAuth authentication is intended exclusively for purchasers of Claude Free, Pro, Max, Team, and Enterprise subscription plans and is designed to support ordinary use of Claude Code and other native Anthropic applications."
- "Developers building products or services that interact with Claude's capabilities, including those using the Agent SDK, should use API key authentication through Claude Console or a supported cloud provider. Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must complete through Anthropic's own flow."
- Same page links the Consumer Terms of Service "for Free, Pro, and Max users" (https://www.anthropic.com/legal/consumer-terms). The Feb 2026 wording ("Using OAuth tokens obtained through Claude Free, Pro, or Max accounts in any other product, tool, or service ... is not permitted") is quoted by press (theregister.com, 2026-02-20), not verified on the current first-party page, which is softer.
- Carve-out: "Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription".

Implication for claude-clip: reading the token from `~/.claude/.credentials.json` and calling `/api/oauth/usage` from a separate app is a grey/likely-disallowed use of subscription credentials by a third-party tool. The statusLine `rate_limits` field (computed and handed over by the unmodified Claude Code binary) is the first-party-sanctioned data path.
