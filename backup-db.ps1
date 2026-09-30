$ErrorActionPreference = "Stop"

$containerName = "eltroasortment--mysql"
$databaseName = "eltroAssortment"
$backupDirectory = "D:\EltroBackups"

$backupId = [Guid]::NewGuid().ToString("N")
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"

$fileName = "${databaseName}_${timestamp}_${backupId}.sql"
$backupPath = Join-Path $backupDirectory $fileName
$partialPath = "$backupPath.partial"

$containerDumpPath = "/tmp/eltro-backup-$backupId.sql"
$containerConfigPath = "/tmp/eltro-backup-$backupId.cnf"

$previousOutputEncoding = $OutputEncoding
$passwordPointer = [IntPtr]::Zero
$plainPassword = $null
$escapedPassword = $null
$clientConfiguration = $null

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Nie znaleziono polecenia docker."
    }

    $running = & docker inspect `
        --format "{{.State.Running}}" `
        $containerName

    if ($LASTEXITCODE -ne 0 -or "$running".Trim() -ne "true") {
        throw "Kontener $containerName nie jest uruchomiony."
    }

    New-Item `
        -ItemType Directory `
        -Path $backupDirectory `
        -Force | Out-Null

    $securePassword = Read-Host `
        "Podaj haslo root do MySQL" `
        -AsSecureString

    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
        $securePassword
    )

    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
        $passwordPointer
    )

    # Escapowanie hasla do pliku konfiguracyjnego MySQL.
    $escapedPassword = $plainPassword.Replace('\', '\\')
    $escapedPassword = $escapedPassword.Replace('"', '\"')
    $escapedPassword = $escapedPassword.Replace("`r", '\r')
    $escapedPassword = $escapedPassword.Replace("`n", '\n')
    $escapedPassword = $escapedPassword.Replace("`t", '\t')

    $clientConfiguration = @"
[client]
user=root
password="$escapedPassword"
"@

    # UTF-8 dla danych przekazywanych przez stdin do kontenera.
    $OutputEncoding = New-Object System.Text.UTF8Encoding($false)

    # Plik z haslem ma uprawnienia tylko dla jego wlasciciela.
    # Shell usuwa go po zakonczeniu eksportu.
    $dumpCommand = @(
        "umask 077;"
        "trap 'rm -f $containerConfigPath' EXIT;"
        "tr -d '\r' > $containerConfigPath &&"
        "mysqldump"
        "--defaults-extra-file=$containerConfigPath"
        "--single-transaction"
        "--quick"
        "--routines"
        "--events"
        "--triggers"
        "--no-tablespaces"
        "--set-gtid-purged=OFF"
        "--default-character-set=utf8mb4"
        "--result-file=$containerDumpPath"
        $databaseName
    ) -join " "

    Write-Host "Eksport bazy $databaseName..."

    # Haslo trafia przez stdin, a nie przez argumenty polecenia.
    $clientConfiguration | & docker exec `
        -i `
        $containerName `
        sh -c $dumpCommand

    if ($LASTEXITCODE -ne 0) {
        throw "Eksport MySQL nie powiodl sie. Kopia nie zostala zatwierdzona."
    }

    Write-Host "Kopiowanie pliku na dysk D:..."

    & docker cp `
        "${containerName}:$containerDumpPath" `
        $partialPath

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie skopiowac eksportu z kontenera."
    }

    if (-not (Test-Path -LiteralPath $partialPath)) {
        throw "Nie znaleziono skopiowanego pliku."
    }

    $backupFile = Get-Item -LiteralPath $partialPath

    if ($backupFile.Length -eq 0) {
        throw "Plik eksportu jest pusty."
    }

    Move-Item `
        -LiteralPath $partialPath `
        -Destination $backupPath

    $sizeKB = [Math]::Round(
        (Get-Item -LiteralPath $backupPath).Length / 1KB,
        2
    )

    Write-Host ""
    Write-Host "Kopia zapisana poprawnie." -ForegroundColor Green
    Write-Host "Plik: $backupPath"
    Write-Host "Rozmiar: $sizeKB KB"
    Write-Host "Nastepny krok: sprawdzenie odtworzenia w osobnej bazie."
}
finally {
    $OutputEncoding = $previousOutputEncoding

    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(
            $passwordPointer
        )
    }

    $plainPassword = $null
    $escapedPassword = $null
    $clientConfiguration = $null

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker exec $containerName rm -f `
            $containerConfigPath `
            $containerDumpPath 2>$null | Out-Null
    }

    if (Test-Path -LiteralPath $partialPath) {
        Remove-Item -LiteralPath $partialPath -Force
    }
}