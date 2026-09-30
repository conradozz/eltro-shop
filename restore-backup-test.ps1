$ErrorActionPreference = "Stop"

$sourceContainer = "eltroasortment--mysql"

$backupPath = "D:\EltroBackups\eltroAssortment_2026-09-30_21-53-49_723a8067126d4e2895bd3d3d756f6b83.sql"

$testId = [Guid]::NewGuid().ToString("N")
$testContainer = "eltro-restore-test-$testId"
$testDatabase = "eltro_restore_test"
$containerBackupPath = "/tmp/eltro-restore.sql"

$containerCreated = $false

try {
    if (-not (Test-Path -LiteralPath $backupPath)) {
        throw "Nie znaleziono pliku kopii: $backupPath"
    }

    if ((Get-Item -LiteralPath $backupPath).Length -eq 0) {
        throw "Plik kopii jest pusty."
    }

    # Uzywamy dokladnie tego samego obrazu co baza sklepu.
    $imageId = & docker inspect `
        --format "{{.Image}}" `
        $sourceContainer

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie ustalic obrazu MySQL."
    }

    $imageId = "$imageId".Trim()

    Write-Host "Uruchamianie osobnego MySQL do testu odtworzenia..."

    # Brak sieci, brak udostepnionych portow i brak wolumenu bazy sklepu.
    # Puste haslo dotyczy tylko tego tymczasowego kontenera.
    & docker run `
        --detach `
        --rm `
        --name $testContainer `
        --network none `
        --env MYSQL_ALLOW_EMPTY_PASSWORD=yes `
        $imageId | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie uruchomic kontenera testowego."
    }

    $containerCreated = $true
    $ready = $false

    Write-Host "Oczekiwanie na gotowosc MySQL..."

    for ($attempt = 1; $attempt -le 90; $attempt++) {
        # TCP sprawdza docelowy serwer, nie tymczasowy serwer inicjalizacji.
        & docker exec $testContainer sh -c `
            "mysqladmin --protocol=TCP --host=127.0.0.1 --user=root ping >/dev/null 2>&1"

        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }

        Start-Sleep -Seconds 2
    }

    if (-not $ready) {
        & docker logs --tail 50 $testContainer
        throw "MySQL testowy nie uruchomil sie w ciagu 3 minut."
    }

    Write-Host "Tworzenie pustej bazy testowej..."

    & docker exec $testContainer mysql `
        --user=root `
        "--execute=CREATE DATABASE $testDatabase CHARACTER SET utf8mb4;"

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie utworzyc bazy testowej."
    }

    Write-Host "Kopiowanie pliku SQL..."

    & docker cp `
        $backupPath `
        "${testContainer}:$containerBackupPath"

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie skopiowac pliku SQL."
    }

    Write-Host "Odtwarzanie kopii..."

    # Import odbywa sie wewnatrz kontenera, bez zmiany kodowania przez PowerShell.
    $restoreCommand = @(
        "mysql"
        "--user=root"
        "--default-character-set=utf8mb4"
        "--database=$testDatabase"
        "< $containerBackupPath"
    ) -join " "

    & docker exec $testContainer sh -c $restoreCommand

    if ($LASTEXITCODE -ne 0) {
        throw "Import SQL zakonczyl sie bledem."
    }

    Write-Host ""
    Write-Host "Liczba odtworzonych rekordow:"

    $countSql = @"
SELECT 'products' AS table_name, COUNT(*) AS row_count FROM products
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'sales', COUNT(*) FROM sales
UNION ALL SELECT 'sale_items', COUNT(*) FROM sale_items
UNION ALL SELECT 'stock_movements', COUNT(*) FROM stock_movements
UNION ALL SELECT 'shop_settings', COUNT(*) FROM shop_settings
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'sale_revisions', COUNT(*) FROM sale_revisions
UNION ALL SELECT 'delivery_corrections', COUNT(*) FROM delivery_corrections
UNION ALL SELECT 'flyway_schema_history', COUNT(*) FROM flyway_schema_history;
"@

    & docker exec $testContainer mysql `
        --user=root `
        "--database=$testDatabase" `
        --table `
        "--execute=$countSql"

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie odczytac odtworzonych tabel."
    }

    Write-Host ""
    Write-Host "Sprawdzenie powiazan pozycji sprzedazy:"

    $relationSql = @"
SELECT COUNT(*) AS orphan_sale_items
FROM sale_items si
LEFT JOIN sales s ON s.id = si.sale_id
LEFT JOIN products p ON p.id = si.product_id
WHERE s.id IS NULL OR p.id IS NULL;
"@

    $orphanCount = & docker exec $testContainer mysql `
        --user=root `
        "--database=$testDatabase" `
        --batch `
        --skip-column-names `
        "--execute=$relationSql"

    if ($LASTEXITCODE -ne 0) {
        throw "Nie udalo sie sprawdzic powiazan sprzedazy."
    }

    if ("$orphanCount".Trim() -ne "0") {
        throw "W kopii sa pozycje sprzedazy bez produktu lub sprzedazy."
    }

    Write-Host "Brak osieroconych pozycji sprzedazy."

    Write-Host ""
    Write-Host "TEST ODTWORZENIA ZAKONCZONY POPRAWNIE." `
        -ForegroundColor Green
}
finally {
    if ($containerCreated) {
        Write-Host ""
        Write-Host "Usuwanie tymczasowego kontenera i jego danych..."

        $previousErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"

        try {
            & docker rm --force --volumes $testContainer 2>$null | Out-Null

            if ($LASTEXITCODE -ne 0) {
                Write-Warning "Sprawdz, czy kontener $testContainer zostal usuniety."
            }
        }
        finally {
            $ErrorActionPreference = $previousErrorPreference
        }
    }
}