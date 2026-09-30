$ErrorActionPreference = "Stop"

$containerName = "eltroasortment--mysql"
$jarPath = Join-Path $PSScriptRoot "target\assortment-0.0.1-SNAPSHOT.jar"

$previousDbPassword = $env:DB_PASSWORD
$passwordPointer = [IntPtr]::Zero

try {
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Nie znaleziono JAR-a. Najpierw wykonaj .\mvnw.cmd clean package"
    }

    # Najpierw sprawdzamy JAVA_HOME, potem Java dostepna w PATH.
    $javaPath = $null

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"

        if (Test-Path -LiteralPath $candidate) {
            $javaPath = $candidate
        }
    }

    if (-not $javaPath) {
        $javaCommand = Get-Command java -ErrorAction SilentlyContinue

        if ($javaCommand) {
            $javaPath = $javaCommand.Source
        }
    }

    if (-not $javaPath) {
        throw "Nie znaleziono Java. Projekt wymaga Java 21."
    }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Nie znaleziono polecenia docker."
    }

    # Nie uruchamiamy drugiej aplikacji na zajetym porcie.
    $listener = Get-NetTCPConnection `
        -LocalPort 8080 `
        -State Listen `
        -ErrorAction SilentlyContinue

    if ($listener) {
        throw "Port 8080 jest zajety. Zatrzymaj aplikacje w IntelliJ lub inne uruchomienie."
    }

    $running = & docker inspect `
        --format "{{.State.Running}}" `
        $containerName

    if ($LASTEXITCODE -ne 0) {
        throw "Nie znaleziono kontenera bazy lub Docker Desktop nie dziala."
    }

    if ("$running".Trim() -ne "true") {
        Write-Host "Uruchamianie kontenera MySQL..."

        & docker start $containerName | Out-Null

        if ($LASTEXITCODE -ne 0) {
            throw "Nie udalo sie uruchomic kontenera MySQL."
        }
    }

    Write-Host "Oczekiwanie na MySQL..."

    $ready = $false

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        & docker exec $containerName sh -c `
            "mysqladmin --protocol=TCP --host=127.0.0.1 ping >/dev/null 2>&1"

        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }

        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        throw "MySQL nie odpowiada. Sprawdz kontener w Docker Desktop."
    }

    $securePassword = Read-Host `
        "Podaj haslo do bazy MySQL uzywane przez aplikacje" `
        -AsSecureString

    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
        $securePassword
    )

    # Haslo otrzyma proces Java jako zmienna srodowiskowa.
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        $passwordPointer
    )

    Write-Host ""
    Write-Host "Uruchamianie aplikacji..."
    Write-Host "Adres: http://localhost:8080"
    Write-Host "Zatrzymanie: Ctrl+C"
    Write-Host ""

    & $javaPath `
        -jar $jarPath `
        --server.address=127.0.0.1 `
        --server.port=8080

    if ($LASTEXITCODE -ne 0) {
        throw "Aplikacja zakonczyla sie bledem. Sprawdz logi powyzej."
    }
}
finally {
    $env:DB_PASSWORD = $previousDbPassword

    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(
            $passwordPointer
        )
    }
}