# Changelog

All notable changes to this project will be documented in this file.

## [1.4] - 2026-03-23

- Rewrote bot chat rules so fake players naturally react to what others say in chat instead of mostly ignoring each other, while still keeping them as strangers
- Added strict anti-repetition rules requiring bots to check the last 5+ messages and avoid repeating both wording and meaning
- Made bot messages shorter and more casual (2-8 words), matching how real players type in game chat
- Added Discord hatred rule — bots always respond negatively if anyone mentions Discord
- Added Discord mention handling in BotChatManager with a 15% chance to respond negatively, otherwise silently ignored
- Removed the entire stat backfill system from BotDataSeeder, including the backfillStats method, achievement thresholds, and growthMultiplier method — all stat growth now happens live via BotStatGrowthTask

# Changelog

All notable changes to this project will be documented in this file.

## [2.8] - 2026-03-23

- Fix "last seen" timestamp in welcome message by capturing it before updating, so returning players see the correct time since their previous join
- Remove redundant PlayerStateService lookup from sendWelcomeMessage by passing the pre-fetched timestamp as a parameter

## [2.7] - 2026-03-23

- Configure lftp to auto-confirm SFTP host keys during deployment, avoiding interactive prompts in CI
- Add retry policy (max 3 retries) to lftp for more resilient SFTP uploads

## [2.6] - 2026-03-23

- Replace sftp-upload-action with lftp for deploying the JAR to the server
- Add a verification step to confirm the JAR exists before uploading

## [2.5] - 2026-03-23

- Switch deploy workflow from appleboy/scp-action to Dylan700/sftp-upload-action for server uploads
- Simplify file upload config using inline path mapping instead of separate source/target/strip_components

## [2.4] - 2026-03-23

- Replace manual sshpass/SFTP script with appleboy/scp-action for deploying the plugin jar to the server
- Remove runtime apt-get install of sshpass, no longer needed with the dedicated action

## [2.3] - 2026-03-23

- Switch sshpass to use environment variable (-e) instead of passing the password directly via command line (-p) in the deploy workflow

## [2.2] - 2026-03-23

- Switch SFTP deploy from heredoc to batch file mode for more reliable command execution

## [2.1] - 2026-03-23

- Add 30-second connection timeout to SFTP deploy step to avoid hanging indefinitely
- Quote the heredoc delimiter to prevent variable expansion inside the SFTP command block
- Add explicit `bye` command to cleanly close the SFTP session after upload

## [2.0] - 2026-03-23

- Replace third-party SFTP Deploy Action with direct sshpass/sftp commands for deploying the plugin JAR to the server
- Pass SFTP credentials via environment variables instead of action inputs

## [1.9] - 2026-03-23

- Update SFTP deploy path to target the "1. Core Minecraft" server directory

## [1.8] - 2026-03-23

- Add GitHub Actions workflow for automatic build and SFTP deploy to the server on push to main
- Add periodic bot stat growth system that increments online bot stats every 10 minutes with personality-scaled random gains
- Replace deterministic hash-based bot stats with organic growth model where new bots start at zero and backfill stats proportional to their age
- Add configurable stat growth rates, personality multipliers, zero-gain chance, and absolute stat caps in BotConfig
- Sync bot stats to Supabase from actual YAML data instead of generating random values each sync cycle
- Add "resetdata" subcommand to /fakeplayers that deletes all bot player data files and clears bot rows from Supabase
- Add deleteAllBotStats method to StatsSyncService for bulk-removing bot entries from the website database
- Add confirmation prompt and language entries for the reset data workflow

## [1.7] - 2026-03-23

- Decomposed the monolithic BotService into focused single-responsibility managers: BotLifecycleManager (join/leave cycling), BotChatManager (chat responses), BotVoteManager (vote simulation), BotBroadcaster (message formatting and tab list), and BotDataSeeder (fake player data)
- Centralized all bot system constants, thresholds, and timing values into BotConfig, replacing magic numbers scattered across BotService
- Consolidated all bot message pools into BotMessages as a single source of truth, deduplicating strings from BotService, BotTraits, and MessageCommand
- Added personality-aware chat behavior: bots now have response chance multipliers per personality type (quiet, casual, social, tryhard, chill) that affect how likely they are to respond
- Added typing delay simulation based on message length so short messages like "lol" send fast while longer sentences take more time
- Added AFK/silence windows where bots temporarily stop chatting and optionally announce going AFK and returning
- Added self-initiated messages where bots spontaneously talk about what they're doing based on their personality
- Added bot-to-bot conversation with chain depth limiting to prevent infinite reply loops
- Added peak hour weighting to join/leave cycling so bot activity follows a realistic daily pattern
- Moved fallback DM replies from MessageCommand into the shared BotMessages pool
- Removed the empty BotsModule shell class that had no-op enable/disable methods
- Reduced BotService from ~900 lines to a thin orchestrator delegating to the new managers
- Updated VoteService.processVote call to pass the correct boolean parameter

## [1.6] - 2026-03-23

- Rewrote the first-join welcome message to be more immersive and descriptive
- Removed the separate wild welcome chat messages from WildTeleportService, keeping only the title
- Replaced deprecated getBedSpawnLocation() with getRespawnLocation() for bed spawn check
- Softened the no-bed warning tone for returning players

## [1.5] - 2026-03-23

- Add first-join tips explaining there is no spawn, urging players to place a bed immediately
- Warn returning players who still have no bed spawn location to place a bed
- Refactor join message logic to show first-join tips or last-seen time based on join status

## [1.4] - 2026-03-23

- Add welcome message on player join with clickable links to the website, Discord, vote page, and Diamond rank store
- Show personalized greeting for returning players and a generic welcome for first-time joins
- Display "last seen" time for returning players showing how long ago they last joined
- Rework vote command link formatting to use a cleaner "Click Here" style instead of lang-file-driven text

## [1.3] - 2026-03-23

- Add on_conflict=username query parameter to player stats upsert endpoint to handle duplicate username conflicts
- Add markBotsOffline method to set all online bots to offline status via Supabase PATCH request

## [1.2] - 2026-03-23

- Add StatsSyncService to periodically sync player stats (kills, deaths, blocks, playtime, etc.) to Supabase every 2 minutes for website leaderboards and profiles
- Add SubscriptionService to sync Diamond rank with Supabase diamond_subscriptions table, granting/revoking Diamond based on subscription status
- Add persistent pending-diamond.yml queue so Diamond rank changes survive server restarts when players are offline
- Update DiamondListener to check subscription status on join before showing the custom join message, with a 2-second delay to let the async sync complete
- Sync player stats to Supabase on quit in PlayerListener before unloading player data
- Bulk sync all offline player data from playerdata files on startup so historical players appear in the stats table
- Sync bot players to Supabase with randomized stats and deterministic join dates
- Mark all players offline in Supabase on server shutdown
- Add supabase-url and supabase-anon-key config entries to config.yml

## [1.1] - 2026-03-23

- Add Diamond rank perks system with DiamondService for managing trails, join messages, and priority queue
- Add /craft command for Diamond players to open a portable crafting table
- Add /joinmessage command to set, view, or clear a custom join message (64 char limit)
- Add /particles command with a GUI selector for 10 cosmetic particle trails (heart, flame, soul, enchant, note, cherry blossom, end portal, snowflake, emerald, diamond dust)
- Add DiamondListener for priority queue bypass when the server is full and broadcasting custom join messages
- Persist Diamond player data (trails and join messages) to diamond.yml
- Add language entries for all Diamond perk feedback messages

## [1.3] - 2026-03-22

- Increased bot response chances across the board: death reactions from 20% to 35%, returning player joins from 15% to 30%, direct mentions/questions from 80% to 90%, and regular chat from 40% to 60%
- Bumped ambient chatter probability from 15% to 40%
- Shortened the activity scheduler interval from 5-10 minutes down to 2-5 minutes

## [1.2] - 2026-03-22

- Add config auto-patcher that backfills missing keys from the bundled default config.yml without overwriting user customizations
- Prepend the server prefix to the Discord command's clickable message
- Add configurable join/quit messages toggle (join-quit-messages config option) that suppresses join, quit, and bot join/leave messages when disabled
- Block phantom spawning entirely
- Bots now track players who accuse others of being bots and respond with hostility toward those players in future interactions
- Multiple bots react defensively when a player calls people bots, NPCs, or fake accounts
- Replace famous YouTuber/streamer names in the bot name pool with generic, ordinary-sounding usernames to avoid recognition
- Reduce bot diamond armor chance from 15% to 3%
- Tighten tree feller log search radius from 2-block to 1-block neighbors to prevent merging adjacent trees
- Reduce max horizontal trunk radius from 9 to 5 blocks
- Change tree feller leaf collection to still gather leaves near foreign logs but stop BFS spread through them, improving boundary detection between neighboring trees
- Bots that exceed the current max are now gradually drained with staggered leave messages when min/max settings change
- Prevent bots from joining when already at max capacity
- Add quit.normal language key for player leave messages

## [1.1] - 2026-03-22

- Add tree felling module with animated falling, scanning for connected logs, and configurable settings
- Add /rules command with a paginated GUI showing server rules
- Add /discord command with clickable invite link
- Add claim resizing support via /claim resize and the claim management GUI
- Add bot name validation module to filter inappropriate or duplicate names
- Expand bot service with AI-powered private message replies, dynamic join/leave scheduling, and configurable min/max online range
- Add min, max, and range subcommands to /bots for controlling fake player counts at runtime
- Improve bot chat engine with longer memory, more personality variety, and context-aware responses
- Rework bot name generator to pull from a larger pool of realistic usernames
- Update /msg and /reply to route messages to bots through the AI reply system with a 40% chance to ignore
- Add bot status display to show min/max range alongside online and pool counts
- Show hint to use /claim resize when a new claim overlaps the player's own existing claim
- Add claim area and total area exceeded checks to the resize flow
- Add overlap detection helpers to ClaimIndex and ClaimRegion
- Add tree fell listener to handle block break events for the tree felling module
- Expand ClaimWandListener with left-click support for selecting claim corner 1
- Add join/quit message suppression for bot fake players in PlayerListener
- Update BotTabListener to refresh the tab list header/footer with current fake player counts
- Lower minimum rank for home, enderchest, invsee, more, repair, fly, feed, heal, and god commands from MODERATOR to OPERATOR
- Lower minimum rank for hat and trash commands from MODERATOR to DIAMOND
- Lower minimum rank for nick command from MODERATOR to DIAMOND
- Add vote streak tracking and streak-based reward multipliers to VoteService
- Add mute checking to PunishmentService
- Update BotUtil with helper methods for fake player UUID generation
- Add new language entries for discord, rules, claim resizing, bot range controls, and tree felling
- Add tree felling and discord config sections to config.yml

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

