$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
$NL = [Environment]::NewLine

function Write-Info   { param([string]$M) Write-Host "  $M" }
function Write-Step   { param([string]$M) Write-Host ''; Write-Host "== $M" -ForegroundColor Cyan }
function Write-Ok     { param([string]$M) Write-Host "  OK  $M" -ForegroundColor Green }
function Write-Warn   { param([string]$M) Write-Host "  !!  $M" -ForegroundColor Yellow }
function Write-Fail   { param([string]$M) Write-Host "  XX  $M" -ForegroundColor Red }
function Write-Hint   { param([string]$M) Write-Host "     $M" -ForegroundColor DarkGray }

function Read-YesNo {
    param([string]$Prompt, [bool]$Default = $true)
    $suffix = if ($Default) { '[Y/n]' } else { '[y/N]' }
    while ($true) {
        $answer = Read-Host "$Prompt $suffix"
        if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
        switch ($answer.Trim().ToLower()) {
            'y'     { return $true }
            'yes'   { return $true }
            'n'     { return $false }
            'no'    { return $false }
            default { Write-Warn 'Enter y or n.' }
        }
    }
}

function Read-WithDefault {
    param([string]$Prompt, [string]$Default = '')
    if ([string]::IsNullOrWhiteSpace($Default)) {
        $answer = Read-Host $Prompt
        return $answer
    }
    $answer = Read-Host "$Prompt [$Default]"
    if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
    return $answer
}

function Read-VersionInput {
    param([string]$Current)
    $pattern = '^\d+\.\d+\.\d+(-[A-Za-z0-9.\-]+)?(\+[A-Za-z0-9.\-]+)?$'
    while ($true) {
        $answer = Read-Host "New version (current: $Current)"
        if ([string]::IsNullOrWhiteSpace($answer)) {
            Write-Warn 'Version cannot be empty.'
            continue
        }
        $trimmed = $answer.Trim().TrimStart('v')
        if ($trimmed -match $pattern) { return $trimmed }
        Write-Warn 'Invalid format. Use MAJOR.MINOR.PATCH with optional -prerelease and +build.'
        Write-Hint 'Examples: 1.0.0   1.2.3-alpha   2.0.0-beta.1   1.5.0+build.42'
    }
}

function Read-Choice {
    param([string]$Prompt, [string[]]$Valid)
    while ($true) {
        $answer = Read-Host $Prompt
        if ([string]::IsNullOrWhiteSpace($answer)) { return $Valid[0] }
        $trimmed = $answer.Trim().ToLower()
        if ($Valid -contains $trimmed) { return $trimmed }
        Write-Warn ("Enter one of: " + ($Valid -join ', '))
    }
}

function Read-MultilineFromEditor {
    param([string]$Initial)
    $tempPath = Join-Path ([System.IO.Path]::GetTempPath()) ("pretupid-notes-" + [Guid]::NewGuid().ToString('N') + '.md')
    [System.IO.File]::WriteAllText($tempPath, $Initial, (New-Object System.Text.UTF8Encoding($false)))
    $editor = $env:EDITOR
    if ([string]::IsNullOrWhiteSpace($editor)) {
        Start-Process -FilePath 'notepad.exe' -ArgumentList $tempPath -Wait
    } else {
        Start-Process -FilePath $editor -ArgumentList $tempPath -Wait
    }
    if (-not (Test-Path -LiteralPath $tempPath)) { return $Initial }
    $edited = [System.IO.File]::ReadAllText($tempPath)
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    return $edited
}

function Get-WorkspaceVersion {
    $cargoPath = Join-Path $root 'Cargo.toml'
    if (-not (Test-Path -LiteralPath $cargoPath)) { return $null }
    $text = [System.IO.File]::ReadAllText($cargoPath)
    $wsMatch = [regex]::Match($text, '\[workspace\.package\]([\s\S]*?)(?=\n\[|$)')
    if (-not $wsMatch.Success) { return $null }
    $verMatch = [regex]::Match($wsMatch.Value, '(?m)^\s*version\s*=\s*"([^"]+)"')
    if (-not $verMatch.Success) { return $null }
    return $verMatch.Groups[1].Value
}

function Set-WorkspaceVersion {
    param([string]$NewVersion)
    $cargoPath = Join-Path $root 'Cargo.toml'
    $text = [System.IO.File]::ReadAllText($cargoPath)
    $wsMatch = [regex]::Match($text, '\[workspace\.package\]([\s\S]*?)(?=\n\[|$)')
    if (-not $wsMatch.Success) { throw 'Cargo.toml has no [workspace.package] section' }
    $wsSection = $wsMatch.Value
    $newSection = [regex]::Replace($wsSection, '(?m)^(\s*version\s*=\s*")[^"]+(")', ('${1}' + $NewVersion + '${2}'))
    $newText = $text.Replace($wsSection, $newSection)
    [System.IO.File]::WriteAllText($cargoPath, $newText, (New-Object System.Text.UTF8Encoding($false)))
}

function Get-GitHubRepo {
    $remoteUrl = (git -C $root remote get-url origin 2>$null) -replace "`r","" -replace "`n",""
    if (-not $remoteUrl) { return $null }
    if ($remoteUrl -match 'github\.com[:/](?<owner>[^/]+)/(?<name>[^/.]+)(?:\.git)?$') {
        return "$($Matches.owner)/$($Matches.name)"
    }
    return $null
}

function Get-PreviousTag {
    param([string]$CurrentTag)
    $prev = git -C $root describe --tags --abbrev=0 "$CurrentTag^" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    if (-not $prev) { return $null }
    return ($prev -replace "`r","" -replace "`n","").Trim()
}

function Get-CommitLines {
    param([string]$From, [string]$To)
    if ([string]::IsNullOrWhiteSpace($From)) {
        $out = git -C $root log $To --pretty=format:"- %s" --no-merges
    } else {
        $out = git -C $root log "$From..$To" --pretty=format:"- %s" --no-merges
    }
    if (-not $out) { return @() }
    return ($out -split '\r?\n') | Where-Object { $_.Trim().Length -gt 0 }
}

function Build-ReleaseNotes {
    param(
        [string]$Version,
        [string]$Repo,
        [string]$PreviousTag,
        [string]$InstallerName
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Pretupid Launcher $Version")
    $lines.Add('')
    $lines.Add("Released $(Get-Date -Format 'yyyy-MM-dd')")
    $lines.Add('')
    $lines.Add('## What is new')
    $lines.Add('')

    $commits = Get-CommitLines -From $PreviousTag -To 'HEAD'
    if ($commits.Count -gt 0) {
        foreach ($c in $commits) { $lines.Add($c) }
    } else {
        $lines.Add('- Maintenance and stability improvements')
    }

    if (-not [string]::IsNullOrWhiteSpace($InstallerName)) {
        $lines.Add('')
        $lines.Add('## Downloads')
        $lines.Add('')
        $lines.Add("- Windows installer: $InstallerName")
    }

    if (-not [string]::IsNullOrWhiteSpace($PreviousTag)) {
        $lines.Add('')
        $lines.Add('## Full changelog')
        $lines.Add('')
        $lines.Add("https://github.com/$Repo/compare/$PreviousTag...v$Version")
    } else {
        $lines.Add('')
        $lines.Add('## Full changelog')
        $lines.Add('')
        $lines.Add("https://github.com/$Repo/commits/v$Version")
    }

    return ($lines -join $NL)
}

Write-Host ''
Write-Host 'Pretupid Launcher - interactive release' -ForegroundColor Magenta
Write-Host '=======================================' -ForegroundColor Magenta

# --- Preflight ---

Write-Step 'Preflight'

$required = @(
    @{ Name = 'git';   Hint = 'Install Git for Windows' },
    @{ Name = 'gh';    Hint = 'Install from https://cli.github.com/' },
    @{ Name = 'node';  Hint = 'Install Node.js 20+' },
    @{ Name = 'npm';   Hint = 'Comes with Node.js' },
    @{ Name = 'cargo'; Hint = 'Install Rust toolchain' }
)

foreach ($tool in $required) {
    if (-not (Get-Command $tool.Name -ErrorAction SilentlyContinue)) {
        Write-Fail "$($tool.Name) not on PATH. $($tool.Hint)"
        exit 1
    }
    Write-Ok "$($tool.Name) found"
}

$authStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Fail 'gh is not authenticated. Run: gh auth login'
    exit 1
}
Write-Ok 'gh authenticated'

if (-not $env:TAURI_SIGNING_PRIVATE_KEY) {
    Write-Fail 'TAURI_SIGNING_PRIVATE_KEY env var not set.'
    Write-Hint 'The updater requires a signed installer.'
    Write-Hint 'Generate keys once:  npm run tauri signer generate -- -w ~/.pretupid-signing/pretupid.key'
    Write-Hint 'Then:  $env:TAURI_SIGNING_PRIVATE_KEY = Get-Content ~/.pretupid-signing/pretupid.key -Raw'
    exit 1
}
Write-Ok 'Signing key present'

if (-not $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD) {
    Write-Fail 'TAURI_SIGNING_PRIVATE_KEY_PASSWORD env var not set.'
    exit 1
}
Write-Ok 'Signing key password present'

# --- Repo and branch ---

Write-Step 'Repository'

$repo = Get-GitHubRepo
if (-not $repo) {
    Write-Fail 'Cannot parse GitHub repository from origin remote.'
    exit 1
}
Write-Ok "Repository: $repo"

$branch = (git -C $root rev-parse --abbrev-ref HEAD) -replace "`r","" -replace "`n",""
Write-Ok "Branch: $branch"

# --- Version selection ---

Write-Step 'Version'

$currentVersion = Get-WorkspaceVersion
if (-not $currentVersion) {
    Write-Fail 'Cannot read [workspace.package] version from Cargo.toml.'
    exit 1
}

Write-Host ''
Write-Host "  Current workspace version:  $currentVersion" -ForegroundColor White
Write-Host "  Would release as tag:      v$currentVersion" -ForegroundColor White
Write-Host ''
Write-Host '  [c] Continue with this version' -ForegroundColor Gray
Write-Host '  [e] Enter a new version' -ForegroundColor Gray
Write-Host '  [q] Quit' -ForegroundColor Gray
Write-Host ''

$versionChoice = Read-Choice -Prompt '  Choice [c]' -Valid @('c','e','q')

if ($versionChoice -eq 'q') {
    Write-Host ''
    Write-Host 'Aborted.' -ForegroundColor Yellow
    exit 0
}

$version = $currentVersion
$versionChanged = $false

if ($versionChoice -eq 'e') {
    $newVersion = Read-VersionInput -Current $currentVersion
    if ($newVersion -ne $currentVersion) {
        Set-WorkspaceVersion -NewVersion $newVersion
        $version = $newVersion
        $versionChanged = $true
        Write-Ok "Cargo.toml updated to $newVersion"
    } else {
        Write-Info "Version unchanged ($currentVersion)"
    }
}

$tag = "v$version"

Write-Host ''
Write-Host "  Final: $version  ->  tag $tag" -ForegroundColor White
Write-Host ''

if (-not (Read-YesNo -Prompt '  Proceed with this version?' -Default $true)) {
    Write-Host 'Aborted.' -ForegroundColor Yellow
    exit 0
}

# --- Sync versions ---

Write-Step 'Sync versions across the workspace'

Push-Location $root
try {
    npm run sync-version
    if ($LASTEXITCODE -ne 0) { throw 'sync-version failed' }
} finally {
    Pop-Location
}

$tauriConfPath = Join-Path $root 'crates/tauri-app/tauri.conf.json'
$conf = Get-Content -LiteralPath $tauriConfPath -Raw | ConvertFrom-Json
if ($conf.version -ne $version) {
    Write-Fail "tauri.conf.json version is $($conf.version), expected $version"
    exit 1
}
Write-Ok "tauri.conf.json, package.json files synced to $version"

# --- Endpoint check ---

$expectedEndpoint = "https://github.com/$repo/releases/latest/download/latest.json"
$endpoints = $conf.plugins.updater.endpoints
if (-not $endpoints -or $endpoints -notcontains $expectedEndpoint) {
    Write-Warn "Updater endpoint does not match repository."
    Write-Info "Setting to: $expectedEndpoint"
    $conf.plugins.updater.endpoints = @($expectedEndpoint)
    $confText = $conf | ConvertTo-Json -Depth 20
    [System.IO.File]::WriteAllText($tauriConfPath, $confText + $NL, $utf8NoBom)
    Write-Ok 'Updater endpoint updated'
}

# --- Tag availability ---

$existingTag = git -C $root tag -l $tag
if ($existingTag) {
    Write-Fail "Tag $tag already exists locally. Choose a different version."
    exit 1
}
$remoteTag = git -C $root ls-remote --tags origin "refs/tags/$tag"
if ($remoteTag) {
    Write-Fail "Tag $tag already exists on origin. Choose a different version."
    exit 1
}
Write-Ok "Tag $tag is available"

# --- Working tree ---

Write-Step 'Working tree'

$status = git -C $root status --porcelain
if ($status) {
    Write-Warn 'Uncommitted changes detected:'
    $status -split '\r?\n' | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object {
        Write-Host "     $_" -ForegroundColor Gray
    }
    Write-Host ''

    $commitChoice = Read-Choice -Prompt '  [a] Commit all changes  [s] Stash  [q] Quit  [c] Continue anyway' -Valid @('a','s','q','c')

    if ($commitChoice -eq 'q') {
        Write-Host 'Aborted.' -ForegroundColor Yellow
        exit 0
    }

    if ($commitChoice -eq 's') {
        git -C $root stash push -u -m "build-final auto-stash $(Get-Date -Format 'o')"
        Write-Ok 'Changes stashed'
    } elseif ($commitChoice -eq 'a') {
        $defaultMessage = if ($versionChanged) { "Release $version" } else { "Pre-release snapshot for $version" }
        $message = Read-WithDefault -Prompt '  Commit message' -Default $defaultMessage
        git -C $root add -A
        git -C $root commit -m $message
        if ($LASTEXITCODE -ne 0) {
            Write-Fail 'Commit failed.'
            exit 1
        }
        Write-Ok 'All changes committed'
    } else {
        Write-Warn 'Continuing with uncommitted changes. The release will build the current tree.'
    }
} else {
    Write-Ok 'Working tree clean'
}

# --- Install frontend deps ---

Write-Step 'Install dependencies'

Push-Location (Join-Path $root 'frontend')
try {
    npm install --silent
    if ($LASTEXITCODE -ne 0) { throw 'frontend npm install failed' }
} finally {
    Pop-Location
}
Write-Ok 'Frontend dependencies installed'

# --- Release metadata ---

Write-Step 'Release metadata'

$defaultTitle = "Pretupid Launcher $version"
$releaseTitle = Read-WithDefault -Prompt '  Release title' -Default $defaultTitle

$previousTag = Get-PreviousTag -CurrentTag $tag
if ($previousTag) {
    Write-Info "Previous tag: $previousTag"
    Write-Hint "Commits since then will be listed in the notes."
} else {
    Write-Info 'No previous tag found.'
    Write-Hint 'The notes will list all commits up to HEAD.'
}

$autoNotes = Build-ReleaseNotes -Version $version -Repo $repo -PreviousTag $previousTag -InstallerName ''

Write-Host ''
Write-Host '  Auto-generated release notes:' -ForegroundColor White
Write-Host ''
$autoNotes -split '\r?\n' | ForEach-Object { Write-Host "    $_" -ForegroundColor Gray }
Write-Host ''

$editNotes = Read-YesNo -Prompt '  Edit these notes in an external editor?' -Default $false
$releaseNotes = $autoNotes
if ($editNotes) {
    $releaseNotes = Read-MultilineFromEditor -Initial $autoNotes
    Write-Ok 'Notes updated'
}

$releaseNotesPath = Join-Path ([System.IO.Path]::GetTempPath()) ("pretupid-release-$version.md")
[System.IO.File]::WriteAllText($releaseNotesPath, $releaseNotes, $utf8NoBom)

# --- Build ---

Write-Step 'Build frontend bundle'

Push-Location (Join-Path $root 'frontend')
try {
    npm run build
    if ($LASTEXITCODE -ne 0) { throw 'frontend build failed' }
} finally {
    Pop-Location
}
Write-Ok 'Frontend bundle built'

Write-Step 'Build release binary'

Push-Location (Join-Path $root 'crates/tauri-app')
try {
    cargo build --release
    if ($LASTEXITCODE -ne 0) { throw 'cargo build failed' }
} finally {
    Pop-Location
}
Write-Ok 'Release binary built'

Write-Step 'Bundle installer and updater manifest'

Push-Location $root
try {
    cargo tauri build --config crates/tauri-app/tauri.conf.json
    if ($LASTEXITCODE -ne 0) { throw 'tauri bundle failed' }
} finally {
    Pop-Location
}
Write-Ok 'Installer and manifest produced'

# --- Locate artifacts ---

Write-Step 'Locate artifacts'

$candidateBundles = @(
    (Join-Path $root 'crates/tauri-app/target/release/bundle/nsis'),
    (Join-Path $root 'target/release/bundle/nsis')
)
$bundleDir = $null
foreach ($candidate in $candidateBundles) {
    if (Test-Path -LiteralPath $candidate) { $bundleDir = $candidate; break }
}
if (-not $bundleDir) {
    Write-Fail 'NSIS bundle directory not found.'
    exit 1
}
Write-Ok "Bundle: $bundleDir"

$installer = Get-ChildItem -LiteralPath $bundleDir -Filter '*.exe' |
    Where-Object { $_.Name -notmatch 'uninstall' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $installer) {
    Write-Fail 'No installer .exe found.'
    exit 1
}
Write-Ok "Installer: $($installer.Name)  ($([math]::Round($installer.Length / 1MB, 2)) MB)"

$sigFile = Get-ChildItem -LiteralPath $bundleDir -Filter '*.sig' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $sigFile) {
    Write-Fail 'No .sig file. Signing did not run. Check the env vars.'
    exit 1
}
Write-Ok "Signature: $($sigFile.Name)"

$manifestCandidates = @(
    (Join-Path $bundleDir 'latest.json'),
    (Join-Path $root 'crates/tauri-app/target/release/bundle/latest.json'),
    (Join-Path $root 'target/release/bundle/latest.json')
)
$manifest = $null
foreach ($candidate in $manifestCandidates) {
    if (Test-Path -LiteralPath $candidate) { $manifest = Get-Item -LiteralPath $candidate; break }
}
if (-not $manifest) {
    Write-Fail 'latest.json not found.'
    exit 1
}
$manifestJson = Get-Content -LiteralPath $manifest.FullName -Raw | ConvertFrom-Json
if ($manifestJson.version -ne $version) {
    Write-Fail "Manifest version is $($manifestJson.version), expected $version"
    exit 1
}
Write-Ok "Manifest version $($manifestJson.version)"

$finalNotes = Build-ReleaseNotes -Version $version -Repo $repo -PreviousTag $previousTag -InstallerName $installer.Name
if ($editNotes) {
    $finalNotes = $releaseNotes
}
[System.IO.File]::WriteAllText($releaseNotesPath, $finalNotes, $utf8NoBom)

# --- Confirmation ---

Write-Step 'Confirmation'

Write-Host ''
Write-Host '  About to:' -ForegroundColor White
Write-Host "    Create git tag:      $tag" -ForegroundColor Gray
Write-Host "    Push tag to:         origin" -ForegroundColor Gray
Write-Host "    Create GitHub release: $releaseTitle" -ForegroundColor Gray
Write-Host "    Attach assets:" -ForegroundColor Gray
Write-Host "      $($installer.Name)" -ForegroundColor Gray
Write-Host "      $($sigFile.Name)" -ForegroundColor Gray
Write-Host "      latest.json" -ForegroundColor Gray
Write-Host "    Publish at:          https://github.com/$repo/releases/tag/$tag" -ForegroundColor Gray
Write-Host ''

if (-not (Read-YesNo -Prompt '  Push and publish now?' -Default $true)) {
    Write-Host ''
    Write-Warn 'Aborted before tag push.'
    Write-Hint 'The build artifacts are ready. Re-run the script to publish when ready.'
    Write-Hint "Installer path: $($installer.FullName)"
    exit 0
}

# --- Tag, push, release ---

Write-Step 'Create and push tag'

git -C $root tag -a $tag -m "Pretupid Launcher $version"
if ($LASTEXITCODE -ne 0) {
    Write-Fail 'Failed to create tag.'
    exit 1
}
Write-Ok "Tag $tag created"

git -C $root push origin $branch
if ($LASTEXITCODE -ne 0) {
    git -C $root tag -d $tag | Out-Null
    Write-Fail "Failed to push branch $branch."
    exit 1
}
Write-Ok "Branch $branch pushed"

git -C $root push origin $tag
if ($LASTEXITCODE -ne 0) {
    git -C $root tag -d $tag | Out-Null
    Write-Fail 'Failed to push tag.'
    exit 1
}
Write-Ok "Tag $tag pushed"

Write-Step 'Publish GitHub release'

$ghArgs = @(
    'release', 'create', $tag,
    '--repo', $repo,
    '--title', $releaseTitle,
    '--notes-file', $releaseNotesPath,
    '--target', $branch,
    $installer.FullName,
    $sigFile.FullName,
    $manifest.FullName
)

gh @ghArgs
if ($LASTEXITCODE -ne 0) {
    Write-Fail 'GitHub release creation failed.'
    Write-Hint 'The tag was pushed. To clean up:'
    Write-Hint "  git push origin :refs/tags/$tag"
    Write-Hint "  git tag -d $tag"
    exit 1
}

Remove-Item -LiteralPath $releaseNotesPath -Force -ErrorAction SilentlyContinue

Write-Step 'Done'

Write-Host ''
Write-Host "  Release $tag published." -ForegroundColor Green
Write-Host "  https://github.com/$repo/releases/tag/$tag" -ForegroundColor Green
Write-Host ''
Write-Host '  Updater endpoint:' -ForegroundColor Cyan
Write-Host "    https://github.com/$repo/releases/latest/download/latest.json" -ForegroundColor Cyan
Write-Host ''
Write-Host '  Existing installations will:' -ForegroundColor Cyan
Write-Host '    1. Detect the new version on next launch' -ForegroundColor Gray
Write-Host '    2. Download the installer in the background' -ForegroundColor Gray
Write-Host '    3. Verify the signature against the embedded public key' -ForegroundColor Gray
Write-Host '    4. Install silently in place' -ForegroundColor Gray
Write-Host '    5. Relaunch the new version' -ForegroundColor Gray
Write-Host '    6. Show the What is new overlay with the notes from this release' -ForegroundColor Gray
Write-Host ''