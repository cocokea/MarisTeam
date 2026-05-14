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

## Player Command

- `/team` - Open or use team features.

## Admin Command

- `/teamadmin` - Admin operations for the plugin.

## Files

- `config.yml` - Main plugin settings.
- `sounds.yml` - Sound effects used by the plugin.
- `guis/en` - English GUI layouts.
- `guis/vi` - Vietnamese GUI layouts.
- `message_en.yml` - English messages.
- `message_vi.yml` - Vietnamese messages.

## Notes

- This plugin is marked as Folia supported.
- If you localize the server, keep GUI files and message files aligned to avoid mismatched labels.