# Status line: shows plan usage and saves it for the usage guard hook.
# Claude Code sends session data as JSON on stdin. The rate_limits field
# exists only for Pro and Max subscriptions, after the first API response.
# Compatible with Windows PowerShell 5.1 and PowerShell 7.

$ErrorActionPreference = 'SilentlyContinue'
$limit = if ($env:POS_USAGE_LIMIT) { [int]$env:POS_USAGE_LIMIT } else { 70 }
$stateDir = Join-Path $PSScriptRoot 'usage'
$stateFile = Join-Path $stateDir 'usage.json'

$raw = [Console]::In.ReadToEnd()
$data = $null
try { $data = $raw | ConvertFrom-Json } catch { }

if ($data -and $data.rate_limits) {
    New-Item -ItemType Directory -Force -Path $stateDir | Out-Null
    $state = @{
        rate_limits = $data.rate_limits
        saved_at    = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    }
    $tmp = "$stateFile.tmp"
    ($state | ConvertTo-Json -Depth 5) | Set-Content -Path $tmp -Encoding UTF8
    Move-Item -Path $tmp -Destination $stateFile -Force
}

function Pct($value) {
    if ($null -eq $value) { return '?' }
    return [string][math]::Floor([double]$value)
}

$model = if ($data -and $data.model) { $data.model.display_name } else { '?' }
$five = if ($data -and $data.rate_limits -and $data.rate_limits.five_hour) { Pct $data.rate_limits.five_hour.used_percentage } else { '?' }
$week = if ($data -and $data.rate_limits -and $data.rate_limits.seven_day) { Pct $data.rate_limits.seven_day.used_percentage } else { '?' }
$ctx = if ($data -and $data.context_window) { Pct $data.context_window.used_percentage } else { '0' }

Write-Output "[$model] 5h: $five% | semana: $week% | limite: $limit% | contexto: $ctx%"
