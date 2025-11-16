#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if version bump type provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Version bump type required${NC}"
    echo "Usage: ./release.sh <major|minor|patch> [commit message]"
    echo ""
    echo "Examples:"
    echo "  ./release.sh patch                    # 2.0.0 -> 2.0.1"
    echo "  ./release.sh patch 'Fix ruby_block'   # With custom message"
    echo "  ./release.sh minor                    # 2.0.0 -> 2.1.0"
    echo "  ./release.sh major                    # 2.0.0 -> 3.0.0"
    exit 1
fi

BUMP_TYPE="$1"
COMMIT_MSG="${2:-}"

# Validate bump type
if [[ ! "$BUMP_TYPE" =~ ^(major|minor|patch)$ ]]; then
    echo -e "${RED}Error: Invalid bump type '$BUMP_TYPE'${NC}"
    echo "Must be one of: major, minor, patch"
    exit 1
fi

# Get current version from gradle.properties
CURRENT_VERSION=$(grep "^mod_version=" gradle.properties | sed "s/mod_version=\(.*\)/\1/")

if [ -z "$CURRENT_VERSION" ]; then
    echo -e "${RED}Error: Could not find current version in gradle.properties${NC}"
    exit 1
fi

echo -e "${GREEN}Current version: ${CURRENT_VERSION}${NC}"

# Parse version into components
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

# Increment based on bump type
case "$BUMP_TYPE" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
echo -e "${GREEN}New version: ${NEW_VERSION}${NC}"

# Update version in gradle.properties
echo -e "${YELLOW}Updating gradle.properties...${NC}"
sed -i "s/^mod_version=.*$/mod_version=${NEW_VERSION}/" gradle.properties

# Commit ALL changes (not just version files)
echo -e "${YELLOW}Committing all changes...${NC}"
git add -A

# Build commit message
if [ -n "$COMMIT_MSG" ]; then
    FULL_MSG="$COMMIT_MSG

Update to v${NEW_VERSION}

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
else
    FULL_MSG="Update to v${NEW_VERSION}

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
fi

git commit -m "$FULL_MSG"

# Create and push tag
echo -e "${YELLOW}Creating tag v${NEW_VERSION}...${NC}"
git tag "v${NEW_VERSION}"

echo -e "${YELLOW}Pushing to GitHub...${NC}"
git push origin master
git push origin "v${NEW_VERSION}"

echo -e "${GREEN}✓ Release v${NEW_VERSION} created!${NC}"
echo -e "${GREEN}✓ CI/CD workflow will build and sign the JAR${NC}"
echo -e "${GREEN}✓ Watch progress: gh run watch${NC}"
echo -e "${GREEN}✓ View release: https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v${NEW_VERSION}${NC}"
