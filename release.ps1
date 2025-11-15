# Release script - bumps version and creates git tag
# Usage: .\release.ps1 [major|minor|patch]

param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateSet("major", "minor", "patch")]
    [string]$BumpType
)

$ErrorActionPreference = "Stop"

# Get current version from gradle.properties
$currentVersion = (Get-Content gradle.properties | Select-String "^mod_version=").ToString().Split('=')[1]

if (-not $currentVersion) {
    Write-Host "Error: Could not find current version in gradle.properties" -ForegroundColor Red
    exit 1
}

Write-Host "Current version: $currentVersion" -ForegroundColor Green

# Parse version into components
$parts = $currentVersion.Split('.')
$major = [int]$parts[0]
$minor = [int]$parts[1]
$patch = [int]$parts[2]

# Increment based on bump type
switch ($BumpType) {
    "major" {
        $major++
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor++
        $patch = 0
    }
    "patch" {
        $patch++
    }
}

$newVersion = "$major.$minor.$patch"
Write-Host "New version: $newVersion" -ForegroundColor Green

# Update version in gradle.properties
Write-Host "Updating gradle.properties..." -ForegroundColor Yellow
$content = Get-Content gradle.properties
$content = $content -replace "^mod_version=.*$", "mod_version=$newVersion"
$content | Set-Content gradle.properties

# Commit changes
Write-Host "Committing changes..." -ForegroundColor Yellow
git add gradle.properties
$commitMsg = @"
Update to v$newVersion

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>
"@
git commit -m $commitMsg

# Create and push tag
Write-Host "Creating tag v$newVersion..." -ForegroundColor Yellow
git tag "v$newVersion"

Write-Host "Pushing to GitHub..." -ForegroundColor Yellow
git push origin master
git push origin "v$newVersion"

Write-Host "OK Release v$newVersion created!" -ForegroundColor Green
Write-Host "OK CI/CD workflow will build and sign the JAR" -ForegroundColor Green
Write-Host "OK Watch progress: gh run watch" -ForegroundColor Green
Write-Host "OK View release: https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v$newVersion" -ForegroundColor Green
