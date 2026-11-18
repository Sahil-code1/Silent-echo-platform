# Loads KEY=VALUE pairs from a .env file into the current PowerShell session only.
# It never prints values. Dot-source it so the variables stay in your session:
#   . .\scripts\load-env.ps1
# Optional: . .\scripts\load-env.ps1 -Path ".\other.env"
param([string]$Path = ".env")

if (-not (Test-Path -LiteralPath $Path)) {
    throw "No file named '$Path' was found. Copy .env.example to .env and fill it in first."
}

$count = 0
foreach ($raw in Get-Content -LiteralPath $Path) {
    $line = $raw.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { continue }
    $eq = $line.IndexOf("=")
    if ($eq -lt 1) { continue }
    $key = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    [Environment]::SetEnvironmentVariable($key, $value, "Process")
    $count++
}
Write-Host "Loaded $count variables from $Path into this session. Values were not printed."
