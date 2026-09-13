$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$path = Join-Path $root 'crates/tauri-app/src/commands/instances.rs'
$content = [System.IO.File]::ReadAllText($path, $utf8NoBom)

$oldBlock = @'
    let needs_update = index.mc_version != instance.minecraft_version
        || index
            .loader
            .map(|l| l != instance.mod_loader)
            .unwrap_or(false)
        || (index.loader_version.is_some()
            && index.loader_version.as_ref() != instance.loader_version.as_ref());

    if needs_update {
        let update_req = UpdateInstanceRequest {
            name: None,
            description: None,
            minecraft_version: Some(index.mc_version.clone()),
            mod_loader: index.loader,
            loader_version: index.loader_version.clone(),
        };
'@

$newBlock = @'
    let loader_differs = match index.loader.as_ref() {
        Some(loader) => *loader != instance.mod_loader,
        None => false,
    };

    let loader_version_differs = match index.loader_version.as_ref() {
        Some(version) => Some(version) != instance.loader_version.as_ref(),
        None => false,
    };

    let needs_update = index.mc_version != instance.minecraft_version
        || loader_differs
        || loader_version_differs;

    if needs_update {
        let update_req = UpdateInstanceRequest {
            name: None,
            description: None,
            minecraft_version: Some(index.mc_version.clone()),
            mod_loader: index.loader.clone(),
            loader_version: index.loader_version.clone(),
        };
'@

$oldBlockNormalized = $oldBlock -replace "`r`n", "`n"
$contentNormalized = $content -replace "`r`n", "`n"

if ($contentNormalized -notmatch [regex]::Escape($oldBlockNormalized)) {
    Write-Host 'EXPECTED BLOCK NOT FOUND. No changes were made.'
    Write-Host 'Paste the current content of the needs_update section from'
    Write-Host 'crates\tauri-app\src\commands\instances.rs and I will target it exactly.'
    exit 1
}

$newBlockNormalized = $newBlock -replace "`r`n", "`n"
$result = $contentNormalized.Replace($oldBlockNormalized, $newBlockNormalized)
$result = $result -replace "`n", "`r`n"

[System.IO.File]::WriteAllText($path, $result, $utf8NoBom)

Write-Host 'WROTE  crates/tauri-app/src/commands/instances.rs'
Write-Host ''
Write-Host 'Change: index.loader is now borrowed with as_ref() for the comparison'
Write-Host 'and cloned into the update request. index.loader_version likewise.'
Write-Host ''
Write-Host 'Re-run:'
Write-Host '  npm run tauri'