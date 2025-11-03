# Minecraft Landscaper Mod

A Minecraft Forge 1.20.1 mod that provides terrain naturalization tools for easy landscape editing.

## Features

### Naturalization Staff
A craftable tool that transforms terrain in a configurable radius with multiple modes.

**Crafting Recipe:**
- Diamond + Emerald + Grass Block + Dirt + Stone

**Usage:**
- Right-click on the TOP of blocks only
- Press V to cycle through modes
- Radius: 5 blocks (configurable)
- Works in Overworld only (configurable)

### Modes

1. **Grass Only** - Clean grass terrain, destroys all vegetation
   - Surface: Grass blocks
   - Below: Dirt (3 layers)
   - Deep: Stone
   - Clears all plants, flowers, tall grass

2. **Grass + Plants** - Natural grass with sparse vegetation
   - Same terrain as Grass Only
   - WFC-inspired plant placement (prevents clusters)
   - 7.5% vegetation chance per grass block
   - Distribution: 85% grass types, 15% flowers
   - Includes: short grass, tall grass, large ferns, various flowers

3. **Messy** - Natural variation terrain
4. **Messy + Plants** - Messy terrain with vegetation
5. **Path Only** - Pure dirt paths
6. **Messy Path** - Varied path terrain

### Technical Features

- **3-pass system:** Clear vegetation → Place terrain → Remove item drops
- **Wave Function Collapse aesthetics:** Prevents duplicate plants adjacent to each other
- **Smart surface detection:** Skips trees/structures, finds actual terrain
- **Safe block whitelist:** Won't replace ores, chests, or valuable blocks (31 blocks configurable)
- **Biome-specific palettes:** Detects biome and uses appropriate blocks/vegetation
- **Underwater support:** Sand/gravel/clay/stone layers, messy mode adds mud/coarse dirt variety
- **Client-server sync:** Network packets for multiplayer mode synchronization

### Biome-Specific Palettes

**Beach/Ocean/River:**
- Blocks: Sand (70-90%), gravel, clay, coarse dirt
- Vegetation: Kelp (40%), seagrass (60%) - tripled in messy mode
- Messy mode: Adds mud and varied layering

**Desert/Badlands:**
- Blocks: Sand (85%), red sand, sandstone
- Vegetation: Dead bush (80%), cactus (20%)

**Taiga:**
- Blocks: Grass (75%), podzol, coarse dirt
- Vegetation: Ferns (60%), grass, spruce saplings

**Jungle:**
- Blocks: Grass (70%), podzol, mossy cobblestone
- Vegetation: Ferns (50%), tall grass, jungle saplings, vines

**Savanna:**
- Blocks: Grass (80%), coarse dirt, red sand
- Vegetation: Tall grass (90%), acacia saplings

**Swamp:**
- Blocks: Grass (75%), mud, clay
- Vegetation: Tall grass, ferns, mushrooms, lily pads

**Mushroom Island:**
- Blocks: Mycelium (90%), dirt
- Vegetation: Red/brown mushrooms

**Plains/Forest (default):**
- Blocks: Grass (85%), gravel, path, farmland
- Vegetation: WFC-inspired mix (85% grass types, 15% flowers)

### Configuration

Config file: `config/landscaper-naturalization.json`

- Safe blocks list (28 default terrain blocks)
- Radius (1-10, default: 5)
- Resource consumption toggle
- Overworld-only toggle

## Repository

https://github.com/wcholmes42/minecraft-landscaper

## Development

Built with Claude Code - an iterative development process focusing on:
- Fixing vegetation clearing issues
- Implementing WFC-inspired aesthetics
- Balancing sparse vs dense vegetation
- Proper surface detection and layering
