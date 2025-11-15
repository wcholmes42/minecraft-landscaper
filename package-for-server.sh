#!/bin/bash
# Package Landscaper mod for server deployment
# Creates a zip with everything the server admin needs

VERSION=$(grep "mod_version=" gradle.properties | cut -d'=' -f2)
PACKAGE_DIR="landscaper-server-${VERSION}"
ZIP_FILE="landscaper-server-${VERSION}.zip"

echo "📦 Packaging Landscaper v${VERSION} for server deployment..."

# Create package directory
mkdir -p "$PACKAGE_DIR"

# Copy mod JAR
echo "  → Copying mod JAR..."
cp "build/libs/landscaper-${VERSION}.jar" "$PACKAGE_DIR/"

# Copy config file
echo "  → Copying config file..."
cp "/c/Users/Bill/curseforge/minecraft/Instances/test/config/landscaper-naturalization.json" "$PACKAGE_DIR/" 2>/dev/null || \
    echo "WARNING: Config file not found at test instance location"

# Copy instructions
echo "  → Copying deployment instructions..."
cp DEPLOYMENT-INSTRUCTIONS.md "$PACKAGE_DIR/README.md"

# Create zip
echo "  → Creating zip archive..."
if command -v zip &> /dev/null; then
    zip -r "$ZIP_FILE" "$PACKAGE_DIR" > /dev/null
    rm -rf "$PACKAGE_DIR"
    echo "✅ Package created: $ZIP_FILE"
else
    echo "✅ Package created: $PACKAGE_DIR/ (zip not available, directory created instead)"
fi

echo ""
echo "📤 Send to server admin:"
echo "   File: $ZIP_FILE (or $PACKAGE_DIR folder)"
echo ""
echo "📥 Players download from:"
echo "   https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v${VERSION}"
