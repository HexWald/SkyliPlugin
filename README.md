# SkyliPlugin

`/skyli` plays a wolf whine with a small puff of breath. Players nearby hear it and see a chat message. Inspired by NexusGrief.

## Install

Put the JAR from [Releases](https://github.com/HexWald/SkyliPlugin/releases) in `plugins/` and restart the server. Built with Java 21 and tested on Paper 1.21.1.

## Commands

| Command | What it does |
| --- | --- |
| `/skyli quiet` | Quieter sound, shorter range and less breath |
| `/skyli deep` | Lower pitch and more breath |
| `/skyli mute` | Toggle sound, chat and breath from other players |
| `/skyli bind double-sneak` | Tap sneak twice |
| `/skyli bind sneak-swap` | Swap hands while sneaking |
| `/skyli bind off` | Turn off the bind |
| `/skyli reload` | Reload the config |

Add `quiet` or `deep` after the gesture to set its mode. Run `/skyli bind` to check your current bind. Mute and binds are saved per player. If you also use BurpPlugin or FartPlugin, pick a different gesture for each plugin.

## Config

Edit `plugins/Skyli/config.yml`. The default radius is 10 blocks and the cooldown is 5 seconds. Set `sound.radius`, `messages.radius` and `breath.radius` for separate ranges. A radius of 0 hides that effect from other players. Sound volume also affects how far the client can hear it. Set `cooldown` to 0 to disable it.

`sound.key` accepts names such as `minecraft:entity.wolf.death`. Set `breath.enabled` to `false` to turn off the particles. Messages support `&` colors. Use `%player%` in chat messages and `%time%` in the cooldown message. An empty chat message hides that line.

`skyli.use` is allowed by default. Reloading from the console or as an operator uses `skyli.reload`.

## Build

Run `mvn clean package` with JDK 21. The JAR is in `target/`.
