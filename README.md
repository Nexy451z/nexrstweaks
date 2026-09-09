# NexRSTweaks

Quality-of-life tweaks for **Refined Storage 2** on **Minecraft 1.21.1** (NeoForge 21.1.x).

## Features

1. **Fuzzy tool matching for the JEI `+` transfer button** — on the RS Crafting Grid, recipes whose
   ingredients include tools (hoe, pickaxe, axe, etc.) can be transferred even when the matching tool
   in your inventory / network has lost some durability. The vanilla-exact check by Refined Storage's
   own transfer handler is kept, and only when it fails we retry with the same-item/durability-variant
   match. Strict-component items (potions, enchanted books, etc.) are never relaxed.

2. **Automatic container refill** — after crafting on the RS Crafting Grid with a bucket-like item
   (water bucket, lava bucket, milk bucket, modded containers — anything whose `craftingRemainingItem`
   is the leftover container), the leftover container is returned to the network and the ingredient is
   pulled from the network back into the grid slot. If the network does not hold the ingredient, the
   mod does nothing and the container behaves vanilla (stays in the slot). Empty containers you placed
   yourself are never touched — the refill only fires on an observed "ingredient → leftover" transition.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.235+
- Refined Storage 2 (tested with 2.0.9) — the mod does nothing without it

## Building

```powershell
./gradlew build
```

Pin the Refined Storage version in `gradle.properties`:

```properties
refinedstorage_version=2.0.9
```

## License

MIT
