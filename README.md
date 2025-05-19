# RC-Homes

Hard fork of zP-Homes

An infinite homes plugin for Paper with MySQL/MariaDB support.

- Supports 1.18+, tested on 1.21
- Uses MySQL or MariaDB (maybe SQLite support if enough people care)
- Fast performance, even when huge amounts of homes are set
- Admins can see/edit nearby homes of players
- Convenient commands for swapping between locations (temporary homes)
- Supports wildcards for deleting homes (wip)
- Your zP-Homes 0.13.0 database will carry over! No need to start over.
  - Switching back to zP-Homes probably won't work after migration. Don't count on it.

# Commands

## Homes Commands (Permission: `rchomes.user`)

- `/home [name]` - Teleports to the specified home. Defaults to `home`.
- `/homes` - Lists all your homes
- `/homes search [name]` - Searches for homes matching the name.
- `/homes fuzzy [name]` - Searches for homes that almost match the name.
- `/sethome [name]` - Sets a home at your current location. Defaults to `home`.
- `/newhome [name]` - Like `/sethome`, but won't override existing homes.
- `/delhome [name]` - Deletes the specified home.

WIP: `/delhome` will allow using wildcards to mass-delete homes starting
with a prefix. (will ask for confirmation)

## Homes Manager Commands (Permission: `rchomes.admin`)

- `/homemanager area <n>` - Shows everyone's homes detected within `n` blocks.
- `/homemanager delhome <name> <username>` - Deletes a player's home.

## Swap Commands (Permission: `rchomes.swap`)

If you play on a server that has this plugin, you'll quickly find
yourself setting a "temporary" home every single time you want to
return to some insignificant location. As a quality of life feature,
RC-Homes has a "swap" feature that does all this for you.

"Swap home" really just refers to a special home named `__swap`.
That's how it works under the hood. You can set or warp to this home
manually if you want more control or are scripting something.

It won't let you use `/swap` if you don't have this home set yet.
Run `/brb` at least once first.

- `/brb` - Set your swap home. Short for `/sethome __swap`
- `/ret` - Return to your swap home. Short for `/home __swap`
- `/swap` - Swaps your current location with the home `__swap`.

Basically, use `/ret` if you don't care about your current location and
just want to return to the swap home without overwriting it. Use `/swap`
if you care about both your current location and the swap home.

If this all sounds confusing, don't worry about it. Having unlimited
homes makes you think differently about teleportation, and you'll
eventually see the value using in this feature.

# Installation

Run `./gradlew shadowJar` to build the plugin. The jar file will be at `./build/libs/lib-all.jar`.

Still need to figure out how to make this simpler. Sorry.
