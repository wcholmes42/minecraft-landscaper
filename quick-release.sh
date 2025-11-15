#!/bin/bash
# Quick Release Script - One command to rule them all!
# Usage: ./quick-release.sh "Your commit message here" [major|minor|patch]
#
# This script:
# 1. Builds the JAR
# 2. Stages all changes
# 3. Commits with your message (adds Claude Code attribution)
# 4. Pushes to GitHub
# 5. Runs release.sh to bump version and trigger CI/CD

set -e  # Exit on any error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if commit message provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Commit message required!${NC}"
    echo "Usage: ./quick-release.sh \"Your commit message\" [major|minor|patch]"
    echo ""
    echo "Examples:"
    echo "  ./quick-release.sh \"Fix bug in flatten mode\" patch"
    echo "  ./quick-release.sh \"Add new desert biome palette\" minor"
    echo "  ./quick-release.sh \"Complete rewrite\" major"
    exit 1
fi

COMMIT_MSG="$1"
RELEASE_TYPE="${2:-patch}"  # Default to patch if not specified

# Validate release type
if [[ ! "$RELEASE_TYPE" =~ ^(major|minor|patch)$ ]]; then
    echo -e "${RED}Error: Release type must be major, minor, or patch${NC}"
    exit 1
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   Quick Release Workflow${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${YELLOW}Commit:${NC} $COMMIT_MSG"
echo -e "${YELLOW}Release Type:${NC} $RELEASE_TYPE"
echo ""

# Step 1: Build
echo -e "${YELLOW}[1/5] Building JAR...${NC}"
./gradlew build --warning-mode all > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}✗ Build failed! Fix errors before releasing.${NC}"
    exit 1
fi

# Step 2: Stage all changes
echo -e "${YELLOW}[2/5] Staging changes...${NC}"
git add -A
# Unstage logs if accidentally added
git reset HEAD logs/ > /dev/null 2>&1 || true
echo -e "${GREEN}✓ Changes staged${NC}"

# Step 3: Commit with attribution
echo -e "${YELLOW}[3/5] Committing...${NC}"
git commit -m "$(cat <<EOF
$COMMIT_MSG

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
echo -e "${GREEN}✓ Committed${NC}"

# Step 4: Push
echo -e "${YELLOW}[4/5] Pushing to GitHub...${NC}"
git push
echo -e "${GREEN}✓ Pushed${NC}"

# Step 5: Release
echo -e "${YELLOW}[5/5] Running release workflow ($RELEASE_TYPE)...${NC}"
./release.sh $RELEASE_TYPE

echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}   🎉 Release Complete!${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  • Watch CI/CD: ${YELLOW}gh run watch${NC}"
echo "  • Deploy to test: ${YELLOW}scp build/libs/landscaper-*.jar root@192.168.68.42:/d/code/minecraft/docker-data/mods/${NC}"
echo "  • View release: ${YELLOW}https://github.com/wcholmes42/minecraft-landscaper/releases${NC}"
echo ""
