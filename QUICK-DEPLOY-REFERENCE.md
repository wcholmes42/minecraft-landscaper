# Quick Deployment Reference

## 📦 Package Everything for Server Admin

```bash
./package-for-server.sh
```
This creates a zip file with:
- ✅ Mod JAR (landscaper-3.0.3.jar)
- ✅ Config file (landscaper-naturalization.json)
- ✅ Installation instructions (README.md)

**Send this zip file to "the boy" (server admin)**

---

## 📤 Manual File Collection

### File 1: Mod JAR
**Location:** `D:\code\minecraft\Landscaper\build\libs\landscaper-3.0.3.jar`
**Size:** ~101 KB
**Destination:** Server `mods/` folder

### File 2: Config File
**Location:** `C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json`
**Size:** ~2 KB
**Destination:** Server `config/` folder

### File 3: Instructions
**Location:** `D:\code\minecraft\Landscaper\DEPLOYMENT-INSTRUCTIONS.md`
**For:** Server admin and players

---

## 🎮 For Players (Client Installation)

**Option 1: Direct Download**
```
1. Go to: https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v3.0.3
2. Download: landscaper-3.0.3.jar
3. Put in: %appdata%\.minecraft\mods\
4. Restart Minecraft
```

**Option 2: Get from Server Admin**
```
Ask server admin for: landscaper-3.0.3.jar
Put in your mods folder
Restart Minecraft
```

---

## 🔧 Server Admin Quick Steps

```bash
# 1. Upload mod to server
scp landscaper-3.0.3.jar username@server:/path/to/server/mods/

# 2. Upload config to server
scp landscaper-naturalization.json username@server:/path/to/server/config/

# 3. Restart server
# (Use your server's restart command)

# 4. Verify in server logs
# Look for: "Loaded mod: Terrain Naturalization Tools 3.0.3"
```

---

## ⚠️ Important Notes

### For Server Admin
- ✅ Must install in server `mods/` folder
- ✅ Config goes in server `config/` folder
- ✅ **Restart server after installation**
- ❌ Don't mix different versions

### For Players
- ✅ Must install same version as server (3.0.3)
- ✅ Goes in client `mods/` folder
- ✅ Config auto-generates on first launch
- ✅ Adjust settings in-game with `K` key
- ❌ Must have Forge 47.4.0+ for Minecraft 1.20.1

### Version Compatibility
```
Server Version:  landscaper-3.0.3.jar
Client Version:  landscaper-3.0.3.jar  (MUST MATCH!)
Minecraft:       1.20.1
Forge:           47.4.0 or higher
```

---

## 🚀 After Installation

### Test Procedure

**Server Admin:**
1. Start server
2. Check logs for errors
3. Join server in creative mode
4. Type `/give @s landscaper:naturalization_staff`
5. Test right-click on grass

**Players:**
1. Launch Minecraft
2. Connect to server
3. Open creative inventory (E)
4. Search "naturalization"
5. Test the staff
6. Press K to open config GUI
7. Test all 10 modes (press V to cycle)

---

## 📋 Checklist for Server Admin

Before going live:
- [ ] Mod JAR uploaded to server mods folder
- [ ] Config file uploaded to server config folder
- [ ] Server restarted successfully
- [ ] No errors in server logs
- [ ] Tested in creative mode - staff works
- [ ] Notified all players to install v3.0.3
- [ ] Provided download link or JAR file to players

---

## 📞 Support

**Issues?** https://github.com/wcholmes42/minecraft-landscaper/issues
**Latest Version?** https://github.com/wcholmes42/minecraft-landscaper/releases
