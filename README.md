# Eltro — panel zarządzania sklepem

Aplikacja do obsługi produktów, klientów, dostaw, sprzedaży
i stanów magazynowych sklepu z elektronarzędziami.

## Technologie

- Java 21
- Spring Boot 3.2.3
- Spring JDBC
- Spring Security
- MySQL
- Flyway
- Docker
- Maven Wrapper
- JUnit 5, Mockito i Testcontainers
- HTML, CSS i JavaScript

## Funkcje

- Dodawanie i edycja produktów oraz cen.
- Wyszukiwanie produktów i klientów.
- Klienci prywatni i firmowi, dane kontaktowe oraz rabaty.
- Przyjęcia towaru z ceną zakupu i narzutem.
- Sprzedaż z rabatem oraz obliczeniem kwot netto i brutto.
- Aktualizacja stanów magazynowych.
- Korekty sprzedaży i dostaw.
- Historia korekt dostaw.
- Zapis autora sprzedaży, przyjęcia towaru i korekt.
- Logowanie i role ADMIN oraz SELLER.
- Zarządzanie kontami, blokowanie i zmiana hasła.
- Ochrona operacji zapisu za pomocą CSRF.

## Wymagania lokalne

- Java 21 dostępna przez JAVA_HOME lub PATH.
- Docker Desktop uruchomiony.
- Kontener MySQL: eltroasortment--mysql.
- MySQL dostępny lokalnie na porcie 3306.
- Baza: eltroAssortment.

Skrypty lokalne są przygotowane dla Windows i PowerShell.
Na innym komputerze należy skonfigurować MySQL oraz dostosować
nazwę kontenera i ścieżki.

## Konfiguracja hasła bazy

Aplikacja odczytuje hasło z DB_PASSWORD.

Przy uruchamianiu z IntelliJ ustaw tę zmienną w konfiguracji
uruchomienia AssortmentApplication.

Nie zapisuj rzeczywistego hasła w application.properties
ani w repozytorium.

Skrypt start-app.ps1 pyta o hasło i przekazuje je procesowi Java.

## Budowanie i testy

W głównym folderze projektu:

    .\mvnw.cmd clean package

Komenda uruchamia testy i buduje wykonywalny JAR:

    target\assortment-0.0.1-SNAPSHOT.jar

Same testy:

    .\mvnw.cmd test

Testcontainers uruchamia oddzielne bazy MySQL dla testów
integracyjnych. Docker Desktop musi być uruchomiony.

## Uruchamianie bez IntelliJ

W głównym folderze projektu:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-app.ps1

Panel:

    http://localhost:8080

Aplikacja uruchamiana tym skryptem nasłuchuje na 127.0.0.1.
Okno terminala musi pozostać otwarte.

Zatrzymanie aplikacji: Ctrl+C.

## Role

ADMIN zarządza produktami, cenami, ustawieniami i kontami.
Może także korygować dostawy i sprzedaże oraz odczytywać
historię korekt dostaw.

SELLER odczytuje dane, obsługuje klientów i rabaty,
przyjmuje dostawy oraz tworzy sprzedaże.

Uprawnienia są egzekwowane w backendzie. Ukrywanie przycisków
w interfejsie jest dodatkowym ułatwieniem dla użytkownika.

## Magazyn i transakcje

Sprzedaż i jej ruchy magazynowe są zapisywane w jednej transakcji.
Błąd powoduje wycofanie całej operacji.

Pomniejszenie stanu magazynowego odbywa się przez warunkowy UPDATE,
który nie pozwala zejść poniżej zera.

Korekta sprzedaży zachowuje wcześniejsze pozycje jako nieaktywne
oraz zapisuje nową wersję i historię korekty.

Korekta dostawy zapisuje poprzednie i nowe wartości,
a stan produktu zmienia o różnicę ilości.

Ceny historyczne i rabaty pozycji sprzedaży są zapisane w bazie,
więc zmiana bieżącego cennika lub rabatu klienta nie zmienia
już zapisanej sprzedaży.

## Migracje bazy

Migracje znajdują się w:

    src/main/resources/db/migration

Flyway wykonuje je podczas uruchamiania aplikacji.

Nie zmieniaj zawartości już wykonanych migracji.
Kolejne zmiany schematu dodawaj jako nowe pliki migracyjne.

## Kopia zapasowa

Uruchom:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backup-db.ps1

Kopie są zapisywane w:

    D:\EltroBackups

Podczas eksportu nie wykonuj zmian struktury tabel ani migracji.
Pliki SQL zawierają dane klientów i hashe haseł użytkowników.
Nie umieszczaj ich w repozytorium.

## Sprawdzenie odtworzenia

Skrypt:

    restore-backup-test.ps1

Przed uruchomieniem ustaw w nim backupPath na wybraną kopię.

Uruchom:

    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\restore-backup-test.ps1

Skrypt odtwarza kopię w osobnym kontenerze, sprawdza odczyt tabel
i powiązania pozycji sprzedaży, a następnie usuwa kontener testowy.

## Weryfikacja pierwszej wersji

Przeprowadzono:

- 18 przechodzących testów automatycznych.
- Próbę przyjęcia towaru, sprzedaży i korekt przez interfejs.
- Sprawdzenie odrzucenia korekty powodującej ujemny stan.
- Eksport oraz odtworzenie kopii bazy w osobnym MySQL.
- Uruchomienie wykonywalnego JAR-a poza IntelliJ.

## Zakres

Projekt jest pierwszą wersją do pilotażu w sklepie.
Nie obejmuje integracji z systemami fiskalnymi
i nie jest pełnym zamiennikiem systemu ERP.

Backup na drugim dysku tego samego komputera warto dodatkowo
kopiować na osobny nośnik lub do odpowiednio zabezpieczonego
zewnętrznego miejsca.