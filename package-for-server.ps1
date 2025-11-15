# Package Landscaper mod for server deployment
# Creates a folder with everything the server admin needs

$version = (Get-Content gradle.properties | Select-String "mod_version=").ToString().Split('=')[1]
$packageDir = "landscaper-server-$version"

Write-Host "Package Landscaper v$version for server deployment..." -ForegroundColor Cyan

# Create package directory
if (Test-Path $packageDir) {
    Remove-Item -Recurse -Force $packageDir
}
New-Item -ItemType Directory -Path $packageDir | Out-Null

# Copy Landscaper mod JAR
Write-Host "  Copying Landscaper mod JAR..." -ForegroundColor White
Copy-Item "build\libs\landscaper-$version.jar" $packageDir\

# Copy ModUpdater mod JAR
Write-Host "  Copying ModUpdater mod JAR..." -ForegroundColor White
$modUpdaterFound = $false

$testModUpdater = Get-ChildItem "C:\Users\Bill\curseforge\minecraft\Instances\test\mods\modupdater-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($testModUpdater) {
    Copy-Item $testModUpdater.FullName $packageDir\
    Write-Host "    OK ModUpdater copied from test instance" -ForegroundColor Green
    $modUpdaterFound = $true
}
else {
    $buildModUpdater = Get-ChildItem "D:\code\minecraft\Updater\build\libs\modupdater-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($buildModUpdater) {
        Copy-Item $buildModUpdater.FullName $packageDir\
        Write-Host "    OK ModUpdater copied from build folder" -ForegroundColor Green
        $modUpdaterFound = $true
    }
}

if (-not $modUpdaterFound) {
    Write-Host "    WARNING: ModUpdater JAR not found" -ForegroundColor Yellow
}

# Copy config file
Write-Host "  Copying config file..." -ForegroundColor White
$configPath = "C:\Users\Bill\curseforge\minecraft\Instances\test\config\landscaper-naturalization.json"
if (Test-Path $configPath) {
    Copy-Item $configPath $packageDir\
}
else {
    Write-Host "    WARNING: Config file not found" -ForegroundColor Yellow
}

# Copy instructions
Write-Host "  Copying deployment instructions..." -ForegroundColor White
Copy-Item DEPLOYMENT-INSTRUCTIONS.md "$packageDir\README.md"

# Create zip
Write-Host "  Creating zip archive..." -ForegroundColor White
try {
    Compress-Archive -Path $packageDir -DestinationPath "$packageDir.zip" -Force
    Write-Host "OK Package created: $packageDir.zip" -ForegroundColor Green
}
catch {
    Write-Host "OK Package created: $packageDir\ (folder only)" -ForegroundColor Green
}

Write-Host ""
Write-Host "Send to server admin:" -ForegroundColor Cyan
Write-Host "   File: $packageDir.zip (or $packageDir folder)" -ForegroundColor White
Write-Host ""
Write-Host "Players download from:" -ForegroundColor Cyan
Write-Host "   https://github.com/wcholmes42/minecraft-landscaper/releases/tag/v$version" -ForegroundColor White
Write-Host ""

# Show package contents
Write-Host "Package contents:" -ForegroundColor Cyan
Get-ChildItem $packageDir | Format-Table Name, @{Label="Size";Expression={"{0:N0} KB" -f ($_.Length/1KB)}} -AutoSize
