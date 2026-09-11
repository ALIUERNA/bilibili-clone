# Stops the running bili-web server (used by the start / rebuild scripts).
#
# Two ways to find it:
#   1. java.exe processes whose command line contains "bili-web" (the jar name)
#   2. whatever listens on port 8080 -- but only if it is a java.exe (safety:
#      we never kill an unrelated program that happens to use that port)
#
# Kept ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 files with the
# system ANSI code page and would garble non-ASCII text.

$killed = @()

# --- 1. by command line ---
$byName = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*bili-web*' }

foreach ($p in $byName) {
    try {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
        $killed += $p.ProcessId
        Write-Host "[info] stopped bili-web server, PID $($p.ProcessId)"
    } catch {
        Write-Host "[warn] could not stop PID $($p.ProcessId): $($_.Exception.Message)"
    }
}

# --- 2. by port 8080 ---
try {
    $listeners = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
} catch {
    $listeners = $null
}

foreach ($l in $listeners) {
    if ($killed -contains $l.OwningProcess) { continue }
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($l.OwningProcess)" -ErrorAction SilentlyContinue
    if ($proc -and $proc.Name -eq 'java.exe') {
        try {
            Stop-Process -Id $proc.ProcessId -Force -ErrorAction Stop
            $killed += $proc.ProcessId
            Write-Host "[info] stopped java process holding port 8080, PID $($proc.ProcessId)"
        } catch {
            Write-Host "[warn] could not stop PID $($proc.ProcessId): $($_.Exception.Message)"
        }
    } elseif ($proc) {
        Write-Host "[warn] port 8080 is used by $($proc.Name) (PID $($proc.ProcessId)) -- not touching it"
    }
}

if ($killed.Count -eq 0) {
    Write-Host "[info] no running bili-web server found"
}

Start-Sleep -Milliseconds 900
exit 0
