# Uruchomienie BidRoom 1.1.1

Projekt działa na Windows x64 z Java 21 i PostgreSQL 17. Interfejs to JavaFX 21. Laptop używa lokalnego PostgreSQL bez Dockera; aktywne dane, narzędzia i sekrety znajdują się pod `%USERPROFILE%\BidRoomData`, poza OneDrive. Nie zmieniamy globalnego PATH ani istniejących instalacji Javy i PostgreSQL.

## Przygotowanie i budowanie

Skrót dla nowego dewelopera: **0-Przygotuj-projekt.cmd**, następnie **1-Start-serwera.cmd** → komunikat **SERWER GOTOWY** → **2-Start-klienta.cmd**. Szczegóły przekazania: [HANDOFF](HANDOFF.md), [IntelliJ](INTELLIJ.md), [PostgreSQL](POSTGRESQL.md).

W PowerShell, w katalogu projektu:

```powershell
./scripts/check-env.ps1
./scripts/setup-tools.ps1
./scripts/start-db.ps1
. ./scripts/env.ps1
./mvnw.cmd -B -ntp clean package
```

`setup-tools.ps1` przygotowuje Temurin 21.0.13 i Maven 3.9.16. Wrapper weryfikuje SHA-256 dystrybucji Maven. `env.ps1` ustawia środowisko tylko bieżącego procesu i ogranicza heap Maven do 384 MiB. Jeżeli istnieje własne `JAVA_HOME`, powinno wskazywać JDK 21.

`start-db.ps1` przygotowuje PostgreSQL 17.11, bazy `bidroom_dev` i `bidroom_test`, osobną rolę aplikacji oraz losowe sekrety. Nie korzysta z cudzej instancji PostgreSQL 18. Port bazy: **127.0.0.1:55432**. `shared_buffers=32MB`, maksymalnie 20 połączeń. Plik `server-secrets.json` otrzymuje prywatne ACL użytkownika i SYSTEM. Nie kopiuj go do repozytorium ani instalatora.

## Zwykła praca

Na przygotowanym laptopie uruchom **`1-Start-serwera.cmd`**, a po komunikacie **SERWER GOTOWY** — **`2-Start-klienta.cmd`**. Pierwszy plik uruchamia bazę i sam serwer. Drugi sprawdza odpowiedź protokołu BidRoom i dopiero wtedy otwiera klienta. Dotychczasowy **`BidRoom.cmd`** zachowuje skrót do wszystkich trzech kroków naraz. Po zamknięciu okna użyj **`Stop-BidRoom.cmd`**, aby zatrzymać własne usługi i zwolnić pamięć. Pliki CMD używają Windows PowerShell z `RemoteSigned` wyłącznie w uruchomionym procesie; nie zmieniają globalnych zasad.

```powershell
./scripts/start-server.ps1
./scripts/start-client.ps1
```

Domyślny port aplikacji to **5001**. Port proponowany w pakiecie startowym, 5000, był blokowany przez Windows. Serwer nasłuchuje wyłącznie na loopback. Klient łączy się automatycznie; formularz adresu i portu nie jest potrzebny.

Zarejestruj własne konto w aplikacji. Login: 3–32 znaki ASCII, hasło: 12–128 znaków. Administrator `administrator` powstaje jednorazowo z losowym hasłem zapisanym lokalnie w `server-secrets.json`; skrypty nie wypisują tego hasła. Administrator może tworzyć konta i blokować użytkowników. Nie istnieje fabryczne hasło. Przy kopiowaniu z JSON skopiuj pełną wartość bez cudzysłowów, także końcowy znak `=`, jeśli występuje. Sprawdzone konto administratora jest aktywne. Przycisk „Pokaż hasło” pomaga zauważyć błędne wklejenie.

Serwer: heap 64–256 MiB. Klient: 32–192 MiB. Uruchamiane JAR-y są kopiowane do katalogu procesu poza źródłami, aby działający klient nie blokował następnego builda w Windows. Raporty powstają automatycznie na serwerze w `%USERPROFILE%\BidRoomData\server\reports`. Przycisk „Pobierz raport .txt” zapisuje dodatkową kopię na komputerze klienta. Rejestr wszystkich raportów: `raport-ogolny.txt` w tym samym katalogu. Tokeny pozostają w pamięci; oczekujące oferty i bufor diagnostyczny są na dysku bez haseł i tokenów.

Zamknij klienta przyciskiem okna. Zatrzymanie własnych usług:

```powershell
./scripts/stop-server.ps1
./scripts/stop-db.ps1
```

`stop-server.ps1` wysyła sygnał do własnego serwera i czeka na kontrolowane zamknięcie. Nie kończy obcych procesów. Zatrzymanie PostgreSQL zachowuje dane. Nie uruchamiaj `start-server.ps1` ponownie, jeśli zapisany proces nadal działa.

Powyższe bezpośrednie polecenia PS1 wykonano również w PowerShell 7. W nowym Windows PowerShell 5.1 można otworzyć lokalną sesję przez `powershell.exe -NoProfile -ExecutionPolicy RemoteSigned`; ustawienie obowiązuje tylko do zamknięcia tej sesji. Skrypty mają UTF-8 z BOM, aby polskie znaki były poprawne również w 5.1.

## Testy

```powershell
./scripts/test.ps1
./scripts/test.ps1 -Integration
./scripts/test-gui.ps1
./scripts/test-layout.ps1
./scripts/test-recovery.ps1
```

Pierwsze polecenie uruchamia szybkie testy jednostkowe. Drugie wymaga działającego PostgreSQL i używa **wyłącznie `bidroom_test`**; usuwa z niej dane testów, sprawdza wyścigi, restart, rollback i TLS. Brak środowiska oznacza niezaliczony test, nie sukces ani zamianę bazy na H2.

`test-gui.ps1` wymaga działającego lokalnego serwera i wcześniejszego `package`. Tworzy syntetyczne konta i aukcje w bazie developerskiej, uruchamia rzeczywiste JavaFX, klika kontrolki przez test JavaFX, sprawdza ofertę/podwójne kliknięcie/reconnect/wynik/eksport/formularz publikacji i zapisuje zrzuty w `docs/screenshots`. Czeka na prawdziwy koniec aukcji. Nie jest częścią każdego builda.

`test-layout.ps1` uruchamia kolejno trzy procesy JavaFX ze skalą wymuszoną wyłącznie dla danego procesu (100%, 125%, 150%) i obszarem odpowiadającym ekranowi 1366×768. Zapisuje sceny i rzeczywisty odczyt skali. Nie zmienia ustawień ekranu Windows; nie zastępuje sprawdzenia przechodzenia między monitorami o różnych DPI.

`test-recovery.ps1` wymaga działającego serwera developerskiego i aktualnego `package`. **Na krótko zatrzymuje wyłącznie własny klaster PostgreSQL BidRoom**, obserwuje niedostępność, włącza bazę i sprawdza automatyczny powrót serwera oraz ten sam wynik oferty bez duplikatu. Uruchamiaj osobno, bez testów integracyjnych/pomiaru i bez pracy użytkowników w tej bazie.

## Pokaz i pomiar

```powershell
./scripts/demo.ps1
./scripts/load-test.ps1
```

Pokaz otwiera **trzy osobne procesy JavaFX** na trzech syntetycznych kontach i dwie aukcje. Dwóch klientów próbuje złożyć tę samą ofertę. Po zapisaniu zrzutów procesy demonstracyjne zamykają się. Można także ręcznie uruchomić klientów przez `start-client.ps1 -Session osoba1`, `osoba2`, `osoba3`. Każda nazwa sesji ma osobny bufor plikowy.

Pomiar jest osobnym poleceniem: 10 klientów bez GUI, trzy aukcje, 30 s, najwyżej jedna próba/klienta/s. Uruchamia własny serwer na 5002 z bazą testową i następnie go zamyka. Nie uruchamiaj go równocześnie z testami integracyjnymi. Wyniki: `docs/validation/loadtest-*.json*`. Nie traktuj konfigurowalnego limitu 32 połączeń jako pomiaru maksymalnej wydajności.

## Instalator i kopie

```powershell
./scripts/build-installer.ps1
./scripts/backup-db.ps1
./scripts/restore-test-db.ps1 -Backup 'pełna ścieżka do własnej kopii.dump'
```

Instalator `dist/BidRoom-1.1.1.exe` zawiera runtime, JavaFX i biblioteki klienta. Jest instalacją dla bieżącego użytkownika, ze skrótem i deinstalacją; domyślnie pliki programu trafiają do `%LOCALAPPDATA%/BidRoomApp`, oddzielnie od danych `%LOCALAPPDATA%/BidRoom/client`. Wersja lokalna wskazuje 127.0.0.1:5001; sama instalacja nie uruchamia serwera i nie udostępnia aplikacji przez Internet. Nie wymaga systemowej Javy na komputerze klienta. Build instalatora sam pobiera WiX 3.14.1 do katalogu narzędzi BidRoom.

Backup trafia domyślnie poza repozytorium. Odtwarzanie tworzy **nową `bidroom_restore_test`** i odmawia nadpisania już istniejącej bazy. Nie dotyka developerskiej. Kopia zawiera konta i logi, dlatego pozostaje prywatna.

## Przeniesienie serwera

Zobacz `deploy/server.env.example`, `deploy/bidroom.service`, `deploy/Dockerfile` i `deploy/compose.production.yaml`. Są przygotowaną konfiguracją dla przyszłego Linux, bez wykonanego wdrożenia. Dane PostgreSQL pozostają w prywatnej sieci kontenerów. Produkcja wymaga TLS, prawdziwej domeny i PKCS12 zawierającego klucz oraz pełny łańcuch certyfikatów.

Po przygotowaniu własnego serwera można zbudować klienta:

```powershell
./scripts/build-installer.ps1 -Production -ServerHost 'rzeczywista-domena-serwera' -ServerPort 5001
```

To przykład argumentu, a nie istniejący adres usługi. Profil publiczny nie pozwala wyłączyć weryfikacji TLS ani przejść na jawny TCP po błędzie certyfikatu. Hosting, domena, certyfikat i test z dwóch niezależnych sieci pozostają osobnym etapem, wymagającym rzeczywistych danych wdrożenia.



## Gdzie są baza, zdjęcia i logi?

- Aktywna baza: `%USERPROFILE%\BidRoomData\postgres17-data`, poza OneDrive. Pliki są wewnętrznym formatem PostgreSQL; rekordów nie edytuje się w Eksploratorze.
- `Baza-BidRoom.cmd` otwiera lokalny `psql`, domyślnie tylko do odczytu, bez wypisywania hasła. `\dt` pokazuje tabele, `\d app_user` strukturę, `\q` kończy sesję. Przykład: `SELECT id,login_normalized,display_name,role FROM app_user ORDER BY id;`.
- Graficzny schemat: `Schemat-bazy.cmd` lub [database-schema.svg](database-schema.svg). Jeśli wolisz pgAdmin/DBeaver: host `127.0.0.1`, port `55432`, baza `bidroom_dev`, użytkownik `bidroom`, hasło z prywatnego `server-secrets.json`. Projekt nie instaluje tych dodatkowych programów automatycznie.
- Zdjęcia: tabela `auction_photo`, przypisanie przez `auction_id`; kopia `pg_dump` zawiera obrazy. Nowe aukcje wymagają zdjęcia przed publikacją. Dotychczasowych fotografii nie było w danych — stare aukcje pokazują oznaczoną ilustrację kategorii.
- Logi działania klienta/serwera: `diagnostic_log`; w aplikacji Administracja → Logi klienta i serwera. Zdarzenia wątków: `TASK_STARTED`, `TASK_FINISHED`; raporty: `REPORT_GENERATED`.
- Trwały audyt licytacji: `auction_event` oraz tabela ofert `bid`. Tworzenie/blokowanie kont: `security_audit`. Raport wynikowy nie zastępuje tych zapisów.
- Awaryjny bufor serwera: `%USERPROFILE%\BidRoomData\server\diagnostics`; konsola serwera: `console.log`, `console-error.log`. Bufor zainstalowanego klienta: `%LOCALAPPDATA%\BidRoom\client`; klienta uruchomionego skryptem: `%USERPROFILE%\BidRoomData\clients\NAZWA_SESJI`.

## Wspólne testy i wersja 1.1

Wersja 1.1 aktualizuje protokół do v2 i numery kont do `BIGINT`. Zachowuje konta, hasła, aukcje, oferty oraz zapisane wyniki poleceń. Stary klient 1.0 wymaga aktualizacji wraz z serwerem. Nie trzeba zakładać kont ponownie. Przed aktualizacją wykonaj kopię, zamknij stare klienty, zatrzymaj serwer, zbuduj nową wersję i uruchom `BidRoom.cmd`; Flyway wykona migracje.

Do pokazów z różnych sieci wybieramy osobny VPS. [Instrukcja wdrożenia](DEPLOYMENT.md) wyjaśnia pakiet serwera, TLS, adres w instalatorze i odbiór dwóch sieci. Lokalny `dist/BidRoom-1.1.1.exe` wskazuje `127.0.0.1`; nie wysyłaj go jako działającego klienta internetowego. Dla uczestników trzeba zbudować profil publiczny z prawdziwym adresem VPS. Wysyła się cały instalator z `dist`, a nie pojedynczy plik programu wyjęty z katalogu instalacji.
