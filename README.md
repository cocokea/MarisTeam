# MarisTeam

MarisTeam is a Folia-safe team management plugin with GUI support and bilingual message files.

## What It Handles

- Team creation and member management
- GUI-driven team actions
- Separate admin tools for moderation and maintenance
- English and Vietnamese message resources

## Requirements

- Paper / Folia 1.21+
- Java 21

## Installation

1. Put the plugin jar in `plugins`.
2. Start the server once.
3. Review the generated config, GUI, and message files.
4. Restart the server.

## Quick Setup

1. Open `/team` with a test account.
2. Create a team and invite another player.
3. Test accept, deny, leave, and team chat flow.
4. Review both language folders before going live.

## Player Command

- `/team` - Open or use team features.

## Admin Command

- `/teamadmin` - Admin operations for the plugin.

## Team Flow

A normal team setup test should include:

1. create a team
2. invite a player
3. accept the invite
4. test team chat
5. remove or leave the team

## Files

- `config.yml` - Main plugin settings.
- `sounds.yml` - Sound effects used by the plugin.
- `guis/en` - English GUI layouts.
- `guis/vi` - Vietnamese GUI layouts.
- `message_en.yml` - English messages.
- `message_vi.yml` - Vietnamese messages.

## MarisSettings Integration

If `MarisSettings` is installed, MarisTeam can use:

- `TEAM_TOGGLE`
- `TEAM_CHAT`

## Common Mistakes

- Editing only one language set and forgetting the second.
- Testing invite flow without testing team chat toggle behavior.
- Treating `/teamadmin` as optional when you still need a maintenance path for production.

## Notes

- This plugin is marked as Folia supported.
- If you localize the server, keep GUI files and message files aligned to avoid mismatched labels.