# <img src="icon.png" alt="" width="48" height="48" align="top"> Chanced [![Plugin Installs](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/chanced)](https://runelite.net/plugin-hub/crejaud) [![Plugin Rank](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/chanced)](https://runelite.net/plugin-hub)

Alerts you (chat message, optional Discord webhook embed with screenshot) when something could have killed you and didn't:
its max hit was at least your HP and you survived.

In chat:

> You were chanced by Scurrius: max hit 8 (Magic), hit 0, 8 HP left.

On Discord:

> **Chanced!**
> **Durial321** was chanced by **Scurrius**
> Max hit 8 · Actual hit 0 · HP 8 → 8 · Attack style Magic · Your prayer Protect from Melee · World 302

## How a chance is decided

Every game tick on which you take damage:

```mermaid
flowchart TD
    A[Damage hitsplats on you this tick] --> B{Still alive after the tick?}
    B -- No --> X[No alert]
    B -- Yes --> C[HP at the hit = HP after the tick + damage taken<br/>food or brew eaten that tick counts]
    C --> D[Match each hit to an attack seen landing]
    D --> E{What landed?}
    E -- Projectile aimed at you --> F[Style from the projectile,<br/>else worst of the NPC's ranged/magic]
    E -- Attack animation --> G[Style from the animation,<br/>else worst of the NPC's styles]
    E -- Nothing seen --> H{Nearest NPC attacking you<br/>has a special on the wiki?}
    H -- Yes --> I[Use the special's max<br/>e.g. Rockfall]
    H -- No --> J[Worst of its normal styles]
    F & G & J --> L{Correct protection prayer up?}
    L -- "No" --> M[Full wiki max hit]
    L -- "Yes, NPC" --> N{Did the hit get through anyway?}
    N -- No --> O[Blocked: max 0]
    N -- Yes --> P[It was a special, e.g. prayer smash:<br/>use the special's max]
    L -- "Yes, player" --> Q[Estimated max x 0.6]
    I & M & O & P & Q --> R[Sum the max of every attack this tick<br/>plus damage-over-time at the damage dealt]
    R --> S{Sum >= HP at the hit?}
    S -- Yes --> T[Chanced! Chat message + Discord]
    S -- No --> X
```

- **Max hits** come from one download of the OSRS Wiki's `infobox_monster` data (id, name, max hit, attack style),
  cached in `.runelite/chanced/max-hits-v2.json` for 24 hours.
- **Attack styles** come from the names of RuneLite's animation and projectile IDs (`NPC_RAT_BOSS_ATTACK_MAGIC_01`
  is a magic attack), bundled with the plugin as a snapshot. An attack not in the snapshot falls back to the NPC's
  wiki attack styles, taking the worst case for you.
- Type `::chancedrefresh` to download the max hits again.
- **Several hits on one tick** are added together. Two hitsplats from one attack count its max once.
- **Players** (Wilderness, PvP and Deadman worlds, or anywhere with *Outside PvP areas*): the max hit is a worst-case
  estimate from their visible gear with 99s, boosting potions, Piety/Rigour, a berserker ring, dragon bolts and 25%
  for special attacks. Shown as `~`.

## Settings

Hover a setting in RuneLite for its description. The *Send test* checkboxes untick themselves after sending.

Message placeholders: `%USERNAME%` `%SOURCE%` `%MAXHIT%` `%DAMAGE%` `%HP%` (HP before the hit) `%HPLEFT%` `%STYLE%`
`%PRAYER%` `%WORLD%`.

## Network use

- OSRS Wiki API: one download of monster max hits per day.
- Your Discord webhook, only when Discord is enabled.

## Development

```sh
./gradlew build    # compile + unit tests
./gradlew run      # launch RuneLite with the plugin (developer mode)
./gradlew --init-script config/checkstyle/checkstyle.gradle checkstyleMain checkstyleTest   # RuneLite code style
```

Optional, when shipping an update anyway: refresh the attack style snapshot from the current RuneLite API.

```sh
python scripts/generate_attack_styles.py path/to/runelite-api-<version>.jar
```
