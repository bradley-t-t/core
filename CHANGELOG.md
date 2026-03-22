# Changelog

All notable changes to this project will be documented in this file.

## [1.1] - 2026-03-22

- Introduced a Module abstraction with a new base Module class and concrete implementations for each domain: BotsModule, ClaimModule, GuiModule, PunishmentModule, RankModule, and VoteModule
- Reorganized domain classes under a new `modules` package (bots, claim, gui, punishment, rank, vote)
- Consolidated all service classes into the `service` package, moving RankService, ClaimService, and VoteService from their respective domain packages
- Consolidated all listener classes into the `listener` package, moving GuiListener from `gui` and VoteListener from `vote`
- Moved punishment GUI classes (PunishmentGui, PunishmentHistoryGui) under the modules punishment subpackage
- Updated import references across all commands, listeners, and services to reflect the new package structure

# Changelog

All notable changes to this project will be documented in this file.

## [1.1] - 2026-03-22

- Core Release v1.1

## [1.1] - 2026-03-22

- Add Core plugin for Paper 1.21.4 with auto-discovery of services, commands, and listeners via classpath scanning
- Add annotation-driven command framework with BaseCommand, CommandContext, CommandInfo, and CommandRegistry
- Add persistent flat-file DataManager for per-player YAML data with auto-save interval
- Add configurable language system with Lang enum and LanguageManager for all player-facing messages
- Add rank system with configurable rank levels (member, diamond, moderator, operator) and permissions
- Add land claim system with golden shovel selection, chunk-indexed region storage, trust/untrust, and particle visualization
- Add fake player (bot) system with AI chat via Grok API, personality traits, name generation, tab list spoofing, and session cycling
- Add punishment system with ban, tempban, mute, kick, freeze, and a GUI-based punishment flow with severity levels and history
- Add teleport commands including tpa, tpaccept, tpdeny, back, wild, top, and coordinate-based tppos
- Add home system with set, delete, list, and teleport-to-home commands with per-rank limits
- Add warp system with set, delete, list, and teleport-to-warp commands
- Add player commands: fly, god, vanish, heal, feed, speed, nick, ping, seen, afk, suicide, gamemode, and user info
- Add inventory commands: give, clear, hat, more, repair, trash, enderchest, and invsee
- Add world commands: time, weather, day, night, sun, rain, storm, burn, smite, and near
- Add chat commands: broadcast, private message, and reply
- Add admin commands: help, list, rank management, bot control, and config reload
- Add GUI framework with paginated inventories, glass panes, and click-action items
- Add vote system with Votifier listener, configurable rewards, vote streaks, and party vote bonuses
- Add player stats and achievement tracking with configurable stat types and milestones
- Add world border service with configurable radius and safe wild teleport within bounds
- Add head drop listener for player kills
- Add custom server list MOTD and ping listener
- Add service registry pattern for centralized service access across the plugin

