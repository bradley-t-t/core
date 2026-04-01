<h1 align="center">Core</h1>

<p align="center"><strong>All-in-one Minecraft Paper plugin for custom survival servers</strong></p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-3.3-blue" alt="Version 3.3" />
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/Paper-1.21.4-44cc11?logo=minecraft&logoColor=white" alt="Paper 1.21.4" />
  <img src="https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white" alt="Maven Build" />
</p>

---

Core is a comprehensive all-in-one Paper plugin powering survive.coreminecraft.com — a custom survival server built around a tight, feature-rich experience without the overhead of dozens of loosely integrated third-party plugins. It consolidates rank management, land claiming, punishment tooling, AI-driven bot players, vote rewards, an inventory GUI framework, animated tree felling, and player statistics into a single cohesive system with a shared language layer, unified data persistence, and a consistent command interface.

The plugin is built on an auto-discovery architecture: `CorePlugin` scans the classpath at startup and registers every service, command, and event listener it finds — no manual wiring, no registration boilerplate. Adding a new feature means writing the class; the framework finds and loads it automatically. This design keeps the entry point lean, enforces consistent registration contracts across modules, and makes the codebase scale cleanly as features accumulate.

---

## Rank System

The rank module manages a configurable tier hierarchy — member, diamond, moderator, operator — with permission gates, display formatting, and promotion logic that integrates cleanly with the rest of the plugin's command and GUI surfaces. Rank data is stored per-player through the shared `DataManager` and exposed to every module that needs to gate behavior behind privilege level. Configuration lives in `ranks.yml`, making the tier structure and associated permissions adjustable without touching code.

## Land Claiming

The claim module gives players a golden-shovel based land selection and protection workflow. Claims are chunk-indexed for fast spatial lookup and support trust/untrust relationships, resize operations, and particle-based boundary visualization for in-world feedback. The chunk-indexed storage model ensures claim lookups during block interaction and movement events remain performant even as the number of active claims on the server grows. All claim data persists through the shared YAML flat-file layer.

## Punishment System

Bans, temporary bans, mutes, kicks, and freezes are handled through a purpose-built punishment module with a GUI-driven flow for moderators. The module tracks severity levels and punishment history per player, making it straightforward to assess repeat offenders or review a player's record from the admin interface. Punishment records persist independently and remain queryable regardless of whether the target is online. The GUI framework handles the moderator-facing interface, keeping the punishment flow consistent with other GUI-driven workflows in the plugin.

## Bot Players

The bot module populates the server with convincing fake players that appear in the tab list, move naturally, and engage in AI-driven chat conversations powered by the xAI Grok API. Each bot is assigned a generated name and a set of personality traits that shape its responses, producing distinct conversational identities across the bot pool. The module manages session cycling so bots rotate through activity patterns, and configurable minimum/maximum counts let server operators tune how many bots are active at any time. Tab list spoofing ensures bots present as real players to connecting clients.

## Vote Rewards

Votifier integration handles incoming vote events and distributes configurable rewards to voting players. The module tracks voting streaks and applies party bonuses when multiple players vote within a shared time window, incentivizing coordinated voting behavior. Reward configuration is externalized and adjustable without redeployment.

## GUI Framework

A shared paginated inventory GUI framework underpins every menu-driven interface in the plugin — punishment flows, configuration screens, and player-facing menus all draw on the same building blocks. The framework handles glass-pane chrome, page navigation, and click-action bindings, giving module authors a consistent, low-friction surface for building menu interfaces without reimplementing inventory management logic.

## Tree Felling

When a player breaks a log block, the tree felling module performs a connected-log BFS scan from the broken block outward through the tree's structure, then animates the entire tree coming down sequentially rather than disappearing instantly. The result is a satisfying, visually coherent fell animation that rewards players who break trees from the base without disrupting the feel of vanilla survival gameplay.

## Player Statistics and Achievements

The stats module tracks per-player gameplay statistics and evaluates them against a configurable milestone system defined in `achievements.yml` and `stats.yml`. As players hit thresholds — blocks mined, mobs killed, time played, and similar axes — achievements unlock and are surfaced in-game. The milestone system is data-driven, so new achievements can be added or tuned entirely through configuration.

## Commands

Core ships more than 30 player and admin commands spanning the full feature surface of the plugin. Player-facing commands include `fly`, `god`, `vanish`, `heal`, `feed`, `speed`, `nick`, `ping`, `seen`, `afk`, `tpa`, `home`, `warp`, `/rules`, `/discord`, and `/link` — the last of which issues one-time codes through Supabase to associate Minecraft accounts with external platform identities. Admin commands cover `ban`, `mute`, `kick`, `freeze`, `broadcast`, `gamemode`, `give`, `time`, `weather`, `near`, `invsee`, `enderchest`, `/bots`, and more. All commands are implemented as `BaseCommand` subclasses annotated with `@CommandInfo` and discovered automatically at startup.

## Architecture

`CorePlugin` bootstraps the entire system by scanning the classpath for classes implementing the `Service`, `BaseCommand`, and `Listener` contracts, then registering them in sequence — zero manual registration anywhere in the codebase. Domain isolation is enforced through a `ServiceRegistry` + `Module` abstraction: each of the eight modules owns its domain logic and exposes a stable interface to the rest of the system.

Per-player state is persisted through `DataManager` as per-player YAML flat files with configurable auto-save intervals, keeping persistence simple and portable without a database dependency. All player-facing strings are externalized to `language.yml` through a `Lang` enum and `LanguageManager`, ensuring the plugin is fully localizable and keeping display text out of logic code.

External integrations are handled at the service layer: Supabase receives periodic player-count logs and serves one-time account-link codes; the xAI Grok API backs the bot chat system. Both integrations are cleanly isolated behind service boundaries so they can be swapped or extended without touching module logic.

---

| Stat | Count |
|---|---|
| Modules | 8 |
| Commands | 30+ |
| Rank Levels | 4 |
| Config Files | 7 |
| Command Categories | 12 |
| Manual Registrations | 0 (all auto-discovered) |
| Target Server | survive.coreminecraft.com |

---

<p align="center"><sub>Built by <strong>Trenton Taylor</strong></sub></p>
