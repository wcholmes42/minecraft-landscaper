# Minecraft Landscaper Mod

A Minecraft Forge 1.20.1 mod that provides terrain naturalization tools for easy landscape editing.

**Repository:** https://github.com/wcholmes42/minecraft-landscaper
**Current Version:** 2.2.3

---

## Development Environment

### Requirements
- **Java 17** (required by Minecraft 1.20.1)
- **Minecraft:** 1.20.1
- **Forge:** 47.4.0
- **Mappings:** Official Mojang mappings

### Build Commands
```bash
./gradlew build              # Build mod JAR -> build/libs/landscaper-{version}.jar
./gradlew runClient          # Run Minecraft client with mod (for testing)
./gradlew runServer          # Run dedicated server
./gradlew clean              # Clean build artifacts
./gradlew --refresh-dependencies  # Refresh if dependency issues occur
```

### Build and Test Workflow

**IMPORTANT: Follow this process for all releases:**

1. **Build the JAR**
   ```bash
   ./gradlew build
   ```
   Output: `build/libs/landscaper-{version}.jar`

2. **Deploy to unRAID Test Server**
   ```bash
   scp build/libs/landscaper-{version}.jar root@192.168.68.42:/d/code/minecraft/docker-data/mods/
   ssh root@192.168.68.42 "docker restart minecraft-modupdater-test"
   ```
   - Server path: `/d/code/minecraft/docker-data/` (mounted to `/data` in container)
   - Docker container: `minecraft-modupdater-test`
   - **IMPORTANT:** If changing config settings, also sync the config file:
     ```bash
     scp "C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json" root@192.168.68.42:/d/code/minecraft/docker-data/config/
     ```

3. **Deploy to Local CurseForge Test Client**
   ```bash
   cp build/libs/landscaper-*.jar /c/Users/Bill/curseforge/minecraft/Instances/test/mods/
   ```
   - Client path: `C:\Users\Bill\curseforge\minecraft\Instances\test\mods\`
   - Restart Minecraft client to load new JAR

4. **Test Checklist**
   - [ ] All 6 modes work correctly
   - [ ] Highlight matches actual affected blocks (messy edge randomization)
   - [ ] All keybindings work (V, K, H, M, C)
   - [ ] Multiplayer synchronization (mode changes visible to all players)
   - [ ] Safe block whitelist (doesn't destroy ores/chests)
   - [ ] Performance is acceptable at large radii
   - [ ] No crashes or errors in logs

5. **Release to GitHub**
   ```bash
   ./release.sh <major|minor|patch>
   ```
   - Auto-increments version in `gradle.properties`
   - Commits with standardized message
   - Creates git tag `v{version}`
   - Pushes to GitHub (triggers CI/CD)
   - GitHub Actions builds, signs, and publishes JAR to releases

### Output Artifacts
- **Primary:** `build/libs/landscaper-{version}.jar`
- **Also generated:** Sources JAR, dev JAR
- **GitHub Releases:** Automated via `.github/workflows/release.yml`

---

## Architecture & Code Structure

### Package Organization
```
com.wcholmes.landscaper/
├── Landscaper.java              # Main mod class (@Mod annotation)
├── KeyBindings.java             # [MISPLACED - should be in client/]
├── client/
│   ├── ClientEventHandler.java # Client-side event handling
│   └── HighlightRenderer.java  # Block highlight rendering
├── config/
│   ├── NaturalizationConfig.java  # JSON config management
│   └── ConfigScreen.java          # In-game GUI settings
├── item/
│   ├── NaturalizationStaff.java   # Core item logic (787 lines)
│   ├── NaturalizationMode.java    # Mode enum (6 modes)
│   └── BiomePalette.java          # Biome-specific block selection
└── network/
    ├── ModPackets.java            # Network channel registration
    └── CycleModePacket.java       # Client-server mode sync
```

### Key Classes

**Landscaper.java** - Main mod initialization
- MODID: `"landscaper"`
- Uses `DeferredRegister` for item registration (Forge pattern)
- Registers network packets via `ModPackets.register()`
- Loads config via `NaturalizationConfig.load()`
- Adds items to creative tabs dynamically

**NaturalizationStaff.java** - Core terrain modification logic (787 lines)
- Extends `Item`
- Implements 3-pass terrain modification system
- Handles resource consumption (optional)
- DoS protection: 1-second cooldown per player
- Biome detection and palette selection
- Wave Function Collapse-inspired vegetation placement
- Surface finding algorithm (searches up/down for safe blocks)
- Circle/square shape support with optional messy edges

**NaturalizationMode.java** - Mode management
- Enum: GRASS_ONLY, GRASS_WITH_PLANTS, MESSY, MESSY_WITH_PLANTS, PATH, MESSY_PATH
- Flags: `allowVariation`, `addPlants`, `pathOnly`
- Navigation: `next()` and `previous()` methods

**BiomePalette.java** - Biome-specific block selection
- Static utility class
- 10+ biome types (Desert, Taiga, Jungle, Swamp, Ocean, Beach, etc.)
- Thread-safe randomness via `ThreadLocalRandom`
- Different surface blocks and vegetation per biome

**NaturalizationConfig.java** - Configuration system
- Format: JSON with Gson (pretty-printed)
- Location: `config/landscaper-naturalization.json`
- Settings: radius (1-50), consume_resources, overworld_only, show_highlight, messy_edge, circle_shape
- 33 default safe blocks (terrain types that won't be replaced)
- Auto-creates config with descriptions on first load
- Lazy loading pattern

### Key Design Patterns

**DeferredRegister Pattern** (Forge standard)
- Type-safe, lazy registration during mod loading phase
- Registry name: `landscaper:naturalization_staff`

**NBT-based Item State**
- Current mode stored in ItemStack NBT
- Persists across saves/loads
- Synced via network packets in multiplayer

**Client-Server Synchronization**
- Client updates local state immediately (responsive UI)
- Sends packet to server for authoritative update
- Server validates and applies changes
- Network protocol version: "1" (never blocks clients/servers)

**Thread-Safe Randomness**
- Uses `ThreadLocalRandom` instead of `Random`
- Prevents thread contention in server environment

---

## Core Technical Systems

### 3-Pass Terrain Modification System
**Critical implementation detail to prevent item duplication exploits:**

1. **Pass 1: Clear vegetation**
   - Sets vegetation and safe blocks to AIR
   - Only affects blocks in configured safe list

2. **Pass 2: Place terrain**
   - Places new terrain blocks from surface downward
   - Layers: Surface → Subsurface (3 blocks) → Deep stone
   - Uses biome-specific palettes

3. **Pass 3: Clean up item drops**
   - Removes all dropped items in radius
   - Prevents resource duplication

### Wave Function Collapse Aesthetics
**Prevents clustered, unnatural-looking vegetation:**

- Before placing vegetation, checks 8 neighboring positions
- Rejects placement if 2+ identical plants are nearby
- Creates sparse, natural-looking patterns
- Only applies to plant modes (GRASS_WITH_PLANTS, MESSY_WITH_PLANTS)
- Base vegetation chance: 7.5% per grass block
- Distribution: 85% grass types, 15% flowers

### Surface Detection Algorithm
**Finds actual terrain surface, not trees/structures:**

1. Searches upward from clicked position (handles underground use)
2. Falls back to downward search if needed
3. Only recognizes "safe blocks" from config whitelist
4. Stops at first safe block = surface
5. Skips trees, structures, ores, chests

### DoS Protection
**Prevents server lag from spam-clicking:**

- `ConcurrentHashMap` for thread-safe cooldown tracking
- 1-second cooldown per player (non-creative mode)
- Automatic cleanup of old entries (5-minute threshold)
- Probabilistic cleanup (1% chance per use to avoid overhead)

### Biome-Aware Palettes
**Runtime biome detection for appropriate terrain:**

Supported biomes:
- Beach/Ocean/River: Sand (70-90%), gravel, clay | Kelp (40%), seagrass (60%)
- Desert/Badlands: Sand (85%), red sand, sandstone | Dead bush (80%), cactus (20%)
- Taiga: Grass (75%), podzol, coarse dirt | Ferns (60%), spruce saplings
- Jungle: Grass (70%), podzol, mossy cobble | Ferns (50%), jungle saplings, vines
- Savanna: Grass (80%), coarse dirt, red sand | Tall grass (90%), acacia saplings
- Swamp: Grass (75%), mud, clay | Tall grass, ferns, mushrooms, lily pads
- Mushroom Island: Mycelium (90%), dirt | Red/brown mushrooms
- Plains/Forest (default): Grass (85%), gravel, path, farmland | WFC mix

**Underwater handling:**
- Detects water blocks at surface
- Uses sand/gravel/clay/stone layers
- Messy mode adds mud and coarse dirt variety
- Tripled vegetation chance for kelp/seagrass

### Safe Block Whitelist
**33 terrain blocks that won't be replaced (prevents destroying valuable blocks):**

Configurable list includes:
- Basic terrain: Stone, dirt, grass, sand, gravel, clay, etc.
- Natural variants: Podzol, mycelium, coarse dirt, mud
- Path blocks: Dirt path, farmland
- No ores, chests, or player-placed valuable blocks

**Important:** This list is configurable via `config/landscaper-naturalization.json`

---

## Features & Usage

### Crafting Recipe
Diamond + Emerald + Grass Block + Dirt + Stone

### Controls
- **Right-click:** Apply terrain transformation (TOP of blocks only)
- **V:** Cycle mode forward (Shift+V: reverse)
- **K:** Open settings GUI
- **H:** Toggle highlight overlay
- **M:** Toggle messy edge randomization
- **C:** Toggle circle/square shape

### Modes

1. **Grass Only** - Clean grass terrain
   - Surface: Grass blocks
   - Below: Dirt (3 layers)
   - Deep: Stone
   - Destroys all vegetation

2. **Grass + Plants** - Natural grass with sparse vegetation
   - Same terrain as Grass Only
   - WFC-inspired plant placement (prevents clusters)
   - 7.5% vegetation chance per grass block
   - 85% grass types, 15% flowers

3. **Messy** - Natural variation terrain
   - Randomized surface blocks (biome-dependent)
   - Natural-looking terrain variance

4. **Messy + Plants** - Messy terrain with vegetation
   - Combines messy terrain with WFC vegetation

5. **Path Only** - Pure dirt paths
   - Clean dirt path blocks

6. **Messy Path** - Varied path terrain
   - Mixed path blocks with variation

### Configuration Options

**File:** `config/landscaper-naturalization.json`

- **radius:** 1-50 (default: 5) - Effect radius in blocks
- **consume_resources:** true/false - Whether to consume inventory items
- **overworld_only:** true/false - Restrict to Overworld dimension
- **show_highlight:** true/false - Show block highlight overlay
- **messy_edge:** true/false - Randomize edges for natural look
- **circle_shape:** true/false - Circle vs square shape
- **safe_blocks:** List of block IDs that won't be replaced

Access via GUI (press K in-game) or edit JSON directly.

---

## Development Notes

### Code Quality
- Client-only code isolated in `client/` package (Dist.CLIENT)
- Thread-safe randomness via `ThreadLocalRandom`
- DoS protection via cooldown system
- Safe block whitelist prevents destroying valuable blocks
- Resource consumption optional/configurable

### Known Issues
- `KeyBindings.java` is in wrong package (should be in `client/`)

### Missing Features
- No unit tests currently exist
- No integration tests for multiplayer sync

### Testing Checklist
When making changes, test:
- [ ] All 6 modes work correctly
- [ ] Different biomes (desert, ocean, taiga, jungle, swamp, mushroom, plains)
- [ ] Underwater vs land placement
- [ ] Circle vs square shapes
- [ ] Messy edge randomization
- [ ] Multiplayer synchronization (mode changes visible to all players)
- [ ] Safe block whitelist (doesn't destroy ores/chests)
- [ ] Resource consumption mode (when enabled)
- [ ] DoS protection (spam-clicking has cooldown)
- [ ] WFC vegetation placement (no duplicate plants adjacent)

### Development History
Built with Claude Code - iterative development focusing on:
- Fixing vegetation clearing issues
- Implementing WFC-inspired aesthetics for natural-looking sparse vegetation
- Balancing sparse vs dense vegetation
- Proper surface detection and layering
- Incremental radius system and improved highlighting (v2.2.3)
