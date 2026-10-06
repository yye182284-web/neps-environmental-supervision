$ErrorActionPreference = 'SilentlyContinue'

Write-Host '=== NEPS local preflight ===' -ForegroundColor Cyan

function Show-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { Write-Host "[OK] $Name -> $($command.Source)" -ForegroundColor Green }
    else { Write-Host "[MISSING] $Name" -ForegroundColor Red }
}

function Show-Port([int]$Port, [string]$Name) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen | Select-Object -First 1
    if ($listener) {
        $process = Get-Process -Id $listener.OwningProcess
        Write-Host "[LISTENING] $Name : $Port ($($process.ProcessName))" -ForegroundColor Green
    } else { Write-Host "[FREE] $Name : $Port" -ForegroundColor Yellow }
}

Show-Command 'java'
Show-Command 'node'
Show-Command 'npm'
Show-Port 3306 'Local MySQL'
Show-Port 3307 'Docker MySQL'
Show-Port 9000 'Backend'
Show-Port 8080 'Frontend'
Show-Port 11434 'Ollama'

if ($env:NEPS_DB_PASSWORD) { Write-Host '[OK] NEPS_DB_PASSWORD is set' -ForegroundColor Green }
else { Write-Host '[ACTION] Configure NEPS_DB_PASSWORD in the backend run configuration' -ForegroundColor Yellow }

Write-Host 'AI is optional. Without Ollama, knowledge-base fallback answers remain available.' -ForegroundColor Cyan
