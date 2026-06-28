# What the plugin abuses
A lot of Minecraft mods allow for translation into people's native language using lang files. You can actually get a player to translate something for you using signs, but they'd need the mod that the translation is from to be able to translate it.

# What the plugin does
This plugin asks each joining player to translate something from each banned mod, and if they do manage to translate it they then get kicked.
Additionally, this plugin has a megalist which contains these translations for lots of mods, allowing you to apply it to your own blacklist.

# Commands
- `mb search mega SEARCH_TERM 1-10` - Searches the megalist for mods with a similar nama to the search term, with paging
- `mb search local SEARCH_TERM 1-10` - Searches the local blacklist for mods with a similar name to the search term, with paging

- `mb apply MOD_ID` - Adds the referenced mod to the blacklist
- `mb applyany SEARCH_TERM` - Adds any mods with a similar name to the blacklist

- `mb remove MOD_ID` - Removes the referenced mod from the blacklist
- `mb removeany SEARCH_TERM` - Removes any mods with a similar name from the blacklist

- `mb check USERNAME` - Reverifies the inputted player to ensure they don't have any banned mods
- `mb update` - Updates the megalist to the newest version from GitHub