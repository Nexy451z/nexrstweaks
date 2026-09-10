# NexRSTweaks

Small quality-of-life tweaks for Refined Storage on NeoForge.

## Features

- **Damaged tools in JEI recipe transfer (+ button)** — tools with reduced durability (hoes, pickaxes, …) are no longer treated as a missing ingredient when transferring a recipe into the RS crafting grid. A fallback only kicks in when the exact-match lookup fails, and it still requires the same item.
- **Bucket-like container auto-refill** — when crafting on the RS crafting grid consumes a container item (water bucket, lava bucket, …) and leaves the empty container in the slot, the tweak pulls the source item from the network, refills the slot and returns the empty container to the network.

## Supported versions

| Branch | Minecraft | NeoForge | Refined Storage |
|---|---|---|---|
| `main` | 1.21.1 | 21.1.x | 2.0.9 |
| `port/26.1.2` | 26.1.2 | 26.1.2.x | 3.2.1 |

## Requirements

- **Refined Storage** (the mod does nothing without it).
- The RS JEI integration is additionally required for the tool-transfer tweak.
- Mixins are declared with `required:false` + `defaultRequire:0`, so if an RS update changes the targets the tweaks are skipped silently instead of crashing.

## Build

```powershell
.\gradlew.bat build
# output: build\libs\nexrstweaks-<version>.jar
```

## License

MIT License — see [LICENSE](LICENSE).
