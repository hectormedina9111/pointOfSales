# Usage guard: stops Claude Code when the 5-hour or weekly plan usage
# reaches the limit (70% by default). Runs before every tool call and
# every prompt. Reads the values the status line saved.
# Compatible with Windows PowerShell 5.1 and PowerShell 7.

$ErrorActionPreference = 'SilentlyContinue'
$limit = if ($env:POS_USAGE_LIMIT) { [int]$env:POS_USAGE_LIMIT } else { 70 }
$stateFile = Join-Path (Split-Path $PSScriptRoot -Parent) 'usage\usage.json'

# Read hook input so the caller does not block on a full pipe.
[void][Console]::In.ReadToEnd()

# No data yet (first prompt of the session): let it pass.
if (-not (Test-Path $stateFile)) { exit 0 }

$state = $null
try { $state = Get-Content -Raw -Path $stateFile | ConvertFrom-Json } catch { exit 0 }
if (-not $state -or -not $state.rate_limits) { exit 0 }

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$reasons = @()

foreach ($pair in @(@('five_hour', 'limite de 5 horas'), @('seven_day', 'limite semanal'))) {
    $window = $state.rate_limits.($pair[0])
    if (-not $window) { continue }
    # A window whose reset time has passed is no longer valid.
    if ([long]$window.resets_at -le $now) { continue }
    $used = [double]$window.used_percentage
    if ($used -ge $limit) {
        $reset = [DateTimeOffset]::FromUnixTimeSeconds([long]$window.resets_at).ToLocalTime().ToString('HH:mm "del" dd/MM')
        $reasons += "$($pair[1]) al $([math]::Floor($used))%, se reinicia a las $reset"
    }
}

if ($reasons.Count -gt 0) {
    $message = "Uso del plan sobre el $limit%: " + ($reasons -join '; ') + '. Trabajo detenido. Para continuar, espera el reinicio o sube el limite con POS_USAGE_LIMIT.'
    @{ continue = $false; stopReason = $message } | ConvertTo-Json -Compress
}
exit 0
