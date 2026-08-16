$ErrorActionPreference = "Stop"
$root = 'C:\Users\Yannosay\Desktop\pretupid-launcher'

# Check if native access flag exists
$launcherPath = "$root\crates\mc-launch\src\launcher.rs"
$launcher = [System.IO.File]::ReadAllText($launcherPath, [System.Text.Encoding]::UTF8)
if ($launcher -match 'enable-native-access') {
    Write-Host "Native access flag: PRESENT"
} else {
    Write-Host "Native access flag: MISSING"
}
if ($launcher -match 'terminate_process') {
    Write-Host "Terminate process: PRESENT"
} else {
    Write-Host "Terminate process: MISSING"
}

# Check launch store for anyRunning
$storePath = "$root\frontend\src\stores\launch.ts"
$store = [System.IO.File]::ReadAllText($storePath, [System.Text.Encoding]::UTF8)
if ($store -match 'anyRunning') {
    Write-Host "anyRunning in store: PRESENT"
} else {
    Write-Host "anyRunning in store: MISSING"
}

# Check InstanceDetailView for watch
$detailPath = "$root\frontend\src\views\InstanceDetailView.vue"
$detail = [System.IO.File]::ReadAllText($detailPath, [System.Text.Encoding]::UTF8)
if ($detail -match 'watch\(\(\) => launchState.value.running') {
    Write-Host "Running watch: PRESENT"
} else {
    Write-Host "Running watch: MISSING"
}
if ($detail -match 'copyConsole') {
    Write-Host "copyConsole: PRESENT"
} else {
    Write-Host "copyConsole: MISSING"
}

# Check LibraryView for disabled
$libraryPath = "$root\frontend\src\views\LibraryView.vue"
$library = [System.IO.File]::ReadAllText($libraryPath, [System.Text.Encoding]::UTF8)
if ($library -match 'instance-card--disabled') {
    Write-Host "Disabled cards: PRESENT"
} else {
    Write-Host "Disabled cards: MISSING"
}