# ToB Point Tracker

Tracks individual and team Theatre of Blood points during a raid, and turns them into purple (unique) chances.
Works in Entry, Normal and Hard Mode.

## How ToB points work

The game doesn't show ToB points, so the plugin calculates them using the model from the
[OSRS Wiki ToB loot calculator](https://oldschool.runescape.wiki/w/Calculator:Theatre_of_Blood_loot):

| Source | Points |
|---|---|
| Each room completed (6 rooms) | +3 per player (18 max) |
| MVP: most boss damage in a segment | Maiden 2, Bloat 2, Nylocas 1, Sotetseg 1, Xarpus 2, Verzik P1/P2/P3 2 each (14 total) |
| Death | -4 to the player who died (a player can't go below 0) |

- **Personal max:** 32. **Team max:** `18 × team size + 14` (solo 32, duo 50, trio 68, 4-man 86, 5-man 104).
- **Team purple chance:** `base × team points / team max`. The base is 1/9.1 in Normal, 1/7.7 in Hard, and 0 in Entry, where uniques can't roll.
- **Who gets it:** each player's chance is `your points / team points`.

MVP points always total 14, so **team points (and therefore team purple chance) only depend on team size and deaths**.
The plugin gets these exactly on its own. Splitting MVP points between players requires knowing everyone's boss damage.

## Party integration

Each client only knows its own damage. When you're in a RuneLite party, the plugin sends your per-segment boss damage
to party members after each boss/phase, so teammates running the plugin can attribute MVP points.

Teammates don't need the plugin. Without it:

- **Total** (team points) and the team purple chance are still exact.
- They're grouped into Total instead of getting their own overlay line, like Raid Points Overlay's Total / You layout.
- Your MVP points are exact when your damage beats all the boss damage the plugin saw but can't attribute.
  Otherwise they're estimated by assuming that damage was split evenly between the teammates without data.
  Estimated values are shown with a `~`, and the tooltip says how many MVP points were estimated.

## Detection

- **Team:** ToB party name varcs (`TOB_CLIENT_NAME0-4`), with team size locked to the players seen in the raid.
- **Mode:** boss NPC ids (`*_STORY` / `*_HARD` variants), plus the "Wave '...' (X Mode) complete!" chat message.
- **Room completion:** boss death forms (Maiden/Xarpus death NPCs, Verzik phase transitions, death bat), boss
  `ActorDeath`, or the wave-complete chat message. Each one is enough on its own.
- **Deaths:** player `ActorDeath`, with the party health orbs (`TOB_CLIENT_P0-4` reaching the dead value) as a backup.
  Deaths are deduplicated per room.

## Known limitations

- Players who skip a room (stand outside the barrier) are assumed to have taken part.
- MVP is assumed to be based on damage to the boss itself (not adds), matching the wiki's "Most damage on" table.
