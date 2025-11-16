# Landscaper Mod - Deployment Instructions

### Files Needed

**Location:** `D:\code\minecraft\Landscaper\build\libs\`

**Current Version:** v3.0.3

**Required Files:**
```
landscaper-3.0.3.jar
```

**Config File:**
```
C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json
```

### Server Installation Steps

1. **Copy mod JAR to server mods folder:**
   ```bash
   # If using SCP/SSH
   scp build/libs/landscaper-3.0.3.jar username@server:/path/to/server/mods/

   # Or send via file transfer
   # File: build/libs/landscaper-3.0.3.jar
   # Destination: server/mods/
   ```

2. **Copy config file to server config folder:**
   ```bash
   # If using SCP/SSH
   scp "C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json" username@server:/path/to/server/config/

   # Or send via file transfer
   # File: C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json
   # Destination: server/config/
   ```

3. **Restart the server**
   ```bash
   # Stop server gracefully
   # Start server again
   ```

4. **Verify installation:**
   - Check server logs for: `[landscaper]` initialization messages
   - No errors related to Landscaper mod
   - Server should show: "Loaded mod: Terrain Naturalization Tools 3.0.3"

---

## For Players (Client Installation)

### Required Files

**Mod JAR:** `landscaper-3.0.3.jar`

**Download from:**
- GitHub Releases: https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v3.0.3
- OR get from server admin

### Installation Steps

#### Option A: CurseForge Launcher (Recommended)

1. **Open CurseForge App**
2. **Select your Minecraft instance**
3. **Click on the "..." menu** → **Open Folder**
4. **Navigate to the `mods` folder**
5. **Copy `landscaper-3.0.3.jar` into the `mods` folder**
6. **Restart Minecraft**

#### Option B: Manual Installation

**Windows:**
```
1. Press Win+R
2. Type: %appdata%\.minecraft
3. Navigate to: mods\ folder (create if doesn't exist)
4. Copy landscaper-3.0.3.jar into mods folder
5. Restart Minecraft
```

**macOS:**
```
1. Open Finder
2. Press Cmd+Shift+G
3. Go to: ~/Library/Application Support/minecraft
4. Navigate to: mods/ folder (create if doesn't exist)
5. Copy landscaper-3.0.3.jar into mods folder
6. Restart Minecraft
```

**Linux:**
```
1. Open terminal
2. Navigate to: ~/.minecraft/mods/
3. Copy landscaper-3.0.3.jar into mods folder
4. Restart Minecraft
```

### Client Configuration (Optional)

**Config file will be auto-generated on first launch.**

Default location:
- Windows: `%appdata%\.minecraft\config\landscaper-naturalization.json`
- macOS: `~/Library/Application Support/minecraft/config/landscaper-naturalization.json`
- Linux: `~/.minecraft/config/landscaper-naturalization.json`

**You can adjust settings in-game by pressing the `K` key!**

---

## Verification & Testing

### For Server Admin

After installation, verify:
```
✓ Server starts without errors
✓ Landscaper mod appears in mod list
✓ Players can connect
✓ No version mismatch errors
```

### For Players

After installation, verify:
```
✓ Minecraft launches without crashes
✓ Can connect to server
✓ Mod appears in Mods menu (if using Forge)
✓ Config GUI opens (press K in-game)
```

### Testing the Mod

**In-Game Test (Creative Mode Recommended):**

1. **Get the Naturalization Staff:**
   - Open creative inventory (E)
   - Search for "Naturalization"
   - Take the "Naturalization Staff"

2. **Test Basic Functionality:**
   - Right-click on grass (TOP of blocks only)
   - Should see terrain transform
   - Message appears in action bar

3. **Test Mode Cycling:**
   - Press `V` to cycle through 10 modes
   - Current mode shows in item tooltip

4. **Test Config GUI:**
   - Press `K` to open settings
   - Should see 8 sliders
   - Adjust radius slider
   - Click Save

5. **Test New Modes (v3.0+):**
   - Cycle to **FILL** mode → click on grass above a cave → fills cave
   - Cycle to **FLATTEN** mode → click on a hill → levels terrain
   - Cycle to **FLOOD** mode → click in a valley → creates water lake
   - Cycle to **NATURALIZE** mode → click on flat land → creates organic hills

---

## Troubleshooting

### "Version Mismatch" Error

**Cause:** Server and client have different mod versions

**Solution:**
- Ensure both server and all clients use **landscaper-3.0.3.jar**
- Remove older versions from mods folder

### "Missing Mod" Error

**Cause:** Server has mod but client doesn't (or vice versa)

**Solution:**
- Landscaper mod **required on both server AND client**
- Install on both sides

### Config Not Syncing in Multiplayer

**Cause:** Per-player settings stored server-side

**Solution:**
- Each player has their own settings on the server
- Pressing K and saving will sync your personal settings to the server
- Your settings persist across sessions on that server

### Mod Not Showing Up

**Symptoms:** Mod not in creative menu, no config GUI

**Check:**
1. Correct Minecraft version? (Must be 1.20.1)
2. Forge installed? (Version 47.4.0 or compatible)
3. Mod in correct mods folder?
4. Restart Minecraft after adding mod?

---

## Quick Reference

### Controls
- **V** - Cycle mode forward (Shift+V reverse)
- **K** - Open config GUI
- **H** - Toggle highlight overlay
- **M** - Toggle messy edge randomization
- **C** - Toggle circle/square shape
- **Right-Click** - Apply terrain transformation (top of blocks only)

### 10 Available Modes
1. Grass Only
2. Grass + Plants
3. Messy Natural
4. Messy + Plants
5. Path Only
6. Messy Path
7. **FILL (new v3.0)** - Fills air gaps
8. **FLATTEN (new v3.0)** - Levels terrain
9. **FLOOD (new v3.0)** - Creates water
10. **NATURALIZE (new v3.0)** - Organic erosion

### Support

**Issues:** https://github.com/wcholmes42/minecraft-landscaper/issues
**Latest Release:** https://github.com/wcholmes42/minecraft-landscaper/releases
