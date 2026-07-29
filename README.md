# Warband

**A vanilla+ mob AI and difficulty overhaul where the world fights back with strategy.**

Warband makes hostile mobs smarter, not spongier. Regions learn from the players who spend time there, squads coordinate around roles and terrain, and illagers become rival factions with grudges, bounty hunters, and strongholds.

- Tactical mob squads with leaders, bruisers, marksmen, support roles, morale, regrouping, flanking, pressure, and last-known-position searches
- Regional or distance-based difficulty with depth scaling, dimension pressure, death mercy, and multiplayer presence
- Mob-family tactics: skeleton smoke screens and dusk perches, spider webs and leap-strikes, creeper stalking, zombie surrounds and stacks, blaze high-ground pressure, witch potion support, piglin morale, hoglin stampedes, phantom harassment, natural jockey acquisition, and more
- Multiplayer-aware pressure with threat memory, anti-dogpile targeting, local smart-mob budgets, death mercy areas, and limited shared squad intel
- Illager factions with doctrines, banners, named ranks, mansion/outpost strongholds, Warmarshals, faction territories, grudges, heat that decays and shifts with rival kills, bounty hunters, raid finale boss summons, and rival interceptions
- Boss upgrades, anti-farm behavior, golem support tools, goat horn interactions, debug commands, and a broad `config/warband.properties` tuning surface
- Modded mobs can be opted into Warband's behaviour pools via `customMobPools`, gaining the matching tactics and roles and squadding up with their vanilla counterparts

## Full Documentation -> [Wiki](https://github.com/Overshock-Studios/Warband/wiki)

The wiki covers tactics, mob families, difficulty modes, multiplayer behavior, illager factions, commands, configuration, compatibility, anti-farm behavior, boss abilities, and testing tools.

## Requirements

- Fabric API

Warband is server-side and can be added to an existing world.

## Compatibility

- **[Illager Invasion](https://modrinth.com/mod/illager-invasion)**: soft, automatic support; its illagers join the faction system.
- **[The Lost Castle](https://modrinth.com/mod/the-lost-castle)**: its castle is treated as a faction seat.
- **Structure mods**: mansions and outposts are detected by structure tag, so compatible pillager strongholds can opt in.
- **[Ascendant Armory](https://modrinth.com/mod/ascendant-armory)**: supported from the Ascendant Armory side. Warband-stamped mobs can influence core drops when both mods are installed.
- **[True Ending](https://modrinth.com/mod/true-ending)**: auto-detected. When loaded, Warband's Ender Dragon phase abilities are suppressed so True Ending's dragon overhaul runs unobstructed; Wither abilities are unaffected.
- **[Stormie's Spiders](https://modrinth.com/mod/stormies-spiders)**: auto-detected. When loaded, Warband defers its ceiling-crawl goal to Stormie's realistic climbing; web throws, sticky paths, and leap-strikes still apply.

## License

MIT.
