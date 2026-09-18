$ErrorActionPreference = 'Stop'

$localPort = 15432
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'

function Test-LocalPortListening {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $getNetTcpConnection = Get-Command 'Get-NetTCPConnection' -ErrorAction SilentlyContinue
    if ($null -ne $getNetTcpConnection) {
        try {
            $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
                Select-Object -First 1
            return $null -ne $listener
        }
        catch {
            # 조회 결과가 없거나 cmdlet을 사용할 수 없으면 netstat로 확인한다.
        }
    }

    $netstat = Get-Command 'netstat.exe' -ErrorAction SilentlyContinue
    if ($null -eq $netstat) {
        return $false
    }

    $portPattern = '^\s*TCP\s+\S+:' + $Port + '\s+\S+\s+LISTENING\s*\d*\s*$'
    $listenerLine = & $netstat.Source -ano -p tcp 2>$null |
        Select-String -Pattern $portPattern |
        Select-Object -First 1

    return $null -ne $listenerLine
}

# 프로젝트 환경 파일은 값 노출이나 변경 없이 읽기만 한다.
if (Test-Path -LiteralPath $envPath -PathType Leaf) {
    $null = Get-Content -LiteralPath $envPath -ErrorAction Stop
}

if (Test-LocalPortListening -Port $localPort) {
    Write-Host "localhost:$localPort 포트가 이미 사용 중입니다. SSH 터널을 중복 실행하지 않습니다."
    exit 0
}

$ssh = Get-Command 'ssh.exe' -ErrorAction SilentlyContinue
if ($null -eq $ssh) {
    Write-Error 'ssh 명령어를 찾을 수 없습니다. Windows OpenSSH Client가 설치되어 있는지 확인해 주세요.'
    exit 1
}

$sshArguments = @(
    '-N'
    '-L', "${localPort}:localhost:15432"
    '-o', 'ExitOnForwardFailure=yes'
    '-o', 'ServerAliveInterval=30'
    '-o', 'ServerAliveCountMax=3'
    'meet-dev'
)

try {
    $sshProcess = Start-Process `
        -FilePath $ssh.Source `
        -ArgumentList $sshArguments `
        -WindowStyle Hidden `
        -PassThru
}
catch {
    Write-Error 'SSH 터널 프로세스를 시작하지 못했습니다. OpenSSH와 meet-dev SSH 설정을 확인해 주세요.'
    exit 1
}

$deadline = (Get-Date).AddSeconds(5)
do {
    Start-Sleep -Milliseconds 250

    if (Test-LocalPortListening -Port $localPort) {
        Write-Host '개발 DB SSH 터널이 시작되었습니다.'
        Write-Host "로컬 접속 주소: localhost:$localPort"
        exit 0
    }

    $sshProcess.Refresh()
    if ($sshProcess.HasExited) {
        Write-Error 'SSH 터널 프로세스가 종료되었습니다. meet-dev SSH 별칭, ssh-agent 및 서버 연결 상태를 확인해 주세요.'
        exit 1
    }
} while ((Get-Date) -lt $deadline)

if (-not $sshProcess.HasExited) {
    Stop-Process -Id $sshProcess.Id -Force -ErrorAction SilentlyContinue
}

Write-Error "5초 안에 localhost:$localPort 포트가 LISTEN 상태가 되지 않았습니다. SSH 설정과 서버 상태를 확인해 주세요."
exit 1
