# Quick Release Script - One command to rule them all!
# Usage: .\quick-release.ps1 "Your commit message here" [major|minor|patch]

param(
    [Parameter(Mandatory=$true, Position=0)]
    [string]$CommitMessage,

    [Parameter(Mandatory=$false, Position=1)]
    [ValidateSet("major", "minor", "patch")]
    [string]$ReleaseType = "patch"
)

$ErrorActionPreference = "Stop"

Write-Host "=======================================================" -ForegroundColor Blue
Write-Host "   Quick Release Workflow" -ForegroundColor Blue
Write-Host "=======================================================" -ForegroundColor Blue
Write-Host "Commit: " -ForegroundColor Yellow -NoNewline
Write-Host $CommitMessage
Write-Host "Release Type: " -ForegroundColor Yellow -NoNewline
Write-Host $ReleaseType
Write-Host ""

# Step 1: Build
Write-Host "[1/7] Building JAR..." -ForegroundColor Yellow
$buildOutput = & .\gradlew.bat build --warning-mode all 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "X Build failed! Fix errors before releasing." -ForegroundColor Red
    exit 1
}
Write-Host "OK Build successful" -ForegroundColor Green

# Step 2: Stage all changes
Write-Host "[2/7] Staging changes..." -ForegroundColor Yellow
git add -A
git reset HEAD logs/ 2>$null | Out-Null
Write-Host "OK Changes staged" -ForegroundColor Green

# Step 3: Commit with attribution
Write-Host "[3/7] Committing..." -ForegroundColor Yellow
$commitMsg = @"
$CommitMessage

Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>
"@
git commit -m $commitMsg | Out-Null
Write-Host "OK Committed" -ForegroundColor Green

# Step 4: Push
Write-Host "[4/7] Pushing to GitHub..." -ForegroundColor Yellow
git push 2>&1 | Out-Null
Write-Host "OK Pushed" -ForegroundColor Green

# Step 5: Release
Write-Host "[5/7] Running release workflow ($ReleaseType)..." -ForegroundColor Yellow
& .\release.ps1 $ReleaseType
Write-Host "OK Release created" -ForegroundColor Green

# Step 6: Rebuild with new version
Write-Host "[6/7] Rebuilding JAR with new version..." -ForegroundColor Yellow
$rebuildOutput = & .\gradlew.bat build --warning-mode all 2>&1 | Out-Null
Write-Host "OK JAR rebuilt" -ForegroundColor Green

# Step 7: Deploy to test environments
Write-Host "[7/7] Deploying to test environments..." -ForegroundColor Yellow

# Get the new version from gradle.properties
$version = (Get-Content gradle.properties | Select-String "mod_version=").ToString().Split('=')[1]
$jarFile = "build\libs\landscaper-$version.jar"

if (-not (Test-Path $jarFile)) {
    Write-Host "X JAR file not found: $jarFile" -ForegroundColor Red
    Write-Host "Skipping deployment (CI/CD will build signed version)" -ForegroundColor Yellow
} else {
    # Deploy to unRAID server
    Write-Host "  Deploying to unRAID server..." -ForegroundColor Cyan
    scp $jarFile root@192.168.68.42:/d/code/minecraft/docker-data/mods/ 2>&1 | Out-Null
    ssh root@192.168.68.42 "docker restart minecraft-modupdater-test" 2>&1 | Out-Null
    Write-Host "  OK unRAID server updated and restarted" -ForegroundColor Green

    # Deploy to local CurseForge test client
    Write-Host "  Deploying to local test client..." -ForegroundColor Cyan
    Copy-Item $jarFile "C:\Users\Bill\curseforge\minecraft\Instances\test\mods\"
    Write-Host "  OK Local test client updated" -ForegroundColor Green
    Write-Host "  ! Restart Minecraft client to load new version" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=======================================================" -ForegroundColor Green
Write-Host "   Release Complete!" -ForegroundColor Green
Write-Host "=======================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Released: " -ForegroundColor Blue -NoNewline
Write-Host "v$version" -ForegroundColor Green
Write-Host "Deployed to:" -ForegroundColor Blue
Write-Host "  OK unRAID server (restarted)" -ForegroundColor Green
Write-Host "  OK Local test client" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Blue
Write-Host "  - Watch CI/CD: gh run watch"
Write-Host "  - Restart local Minecraft client to test"
Write-Host "  - View release: https://github.com/wcholmes42/minecraft-landscaper/releases"
Write-Host ""
