# ElyHealPot

ElyHealPot is a client-side Fabric mod for Minecraft 1.21.11 that displays cooldown indicators in the hotbar and inventory.

![Icon](src/main/resources/assets/elyhealpot/icon.png)

## Features

- **Healing II Potion Cooldown Simulation:**
  Simulates a cooldown animation (sweep overlay) when consuming Instant Health II potions. Automatically disables the simulated sweep if the server applies native Minecraft cooldowns.
- **Top-Left Cooldown Numbers:**
  Renders cooldown numbers in the top-left corner of the slot so they do not overlap item stack counts.
- **Vanilla Cooldown Numbers:**
  Optional display of cooldown numbers on native cooldown items (Ender Pearls, Chorus Fruit, Shields, Wind Charges, etc.).

## Commands

| Command | Description |
|---|---|
| `/elyhealpot` | Show current settings and available commands |
| `/elyhealpot mode funtime` | Set healing potion cooldown to 20.0 seconds |
| `/elyhealpot mode spacetimes` | Set healing potion cooldown to 18.4 seconds |
| `/elyhealpot vanillacd <on\|off>` | Enable or disable cooldown numbers for vanilla items |
| `/elyhealpot decimals <low\|always\|none>` | Configure decimal display mode (default: `low`, decimals when < 10s) |

## Building

```bash
./gradlew build
```

Compiled jar will be located at `build/libs/elyhealpot-1.0.0.jar`.

## Authors

- [wellymode](https://github.com/wellymode)
- Antigravity

## License

This project is licensed under the [MIT License](LICENSE).
