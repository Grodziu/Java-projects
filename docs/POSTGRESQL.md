# PostgreSQL — gdzie są dane i jak je pokazać

## Co jest zainstalowane

`0-Przygotuj-projekt.cmd` / `scripts/start-db.ps1` przygotowuje natywny PostgreSQL 17 w `%USERPROFILE%/BidRoomData/tools/postgresql17/pgsql`. Nie potrzebuje Dockera, nie zastępuje innych instalacji PostgreSQL i nie instaluje automatycznie pgAdmin.

Aktywny klaster leży w **`%USERPROFILE%/BidRoomData/postgres17-data`**, poza repozytorium i OneDrive. Rekordy PostgreSQL są w wewnętrznych plikach klastra — nie edytujemy ich ręcznie i nie kopiujemy katalogu podczas działania. Do przenoszenia używamy dumpa.

| Ustawienie lokalne | Wartość |
|---|---|
| Host | `127.0.0.1` |
| Port | `55432` |
| Baza aplikacji | `bidroom_dev` |
| Baza testów integracyjnych | `bidroom_test` — testy czyszczą swoje dane |
| Użytkownik DB aplikacji | `bidroom` |
| Hasło DB | pole `BIDROOM_DB_PASSWORD` w prywatnym `%USERPROFILE%/BidRoomData/server-secrets.json` |
| Administrator DB | `bidroom_admin`; osobny sekret `BIDROOM_DB_ADMIN_PASSWORD` |
| Administrator aplikacji | login `administrator`; osobne hasło `BIDROOM_BOOTSTRAP_ADMIN_PASSWORD` |

To trzy różne role/hasła. Konto aplikacji nie loguje się do pgAdmin; pgAdmin używa konta PostgreSQL. Na każdym nowym komputerze dewelopera skrypty generują własne sekrety. Nie przepisuj hasła Piotra i nie dodawaj tego pliku do GitHuba. Pełna wartość hasła może kończyć się znakiem `=` — jest częścią hasła.

## Najprostszy podgląd bez dodatkowych programów

Po uruchomieniu bazy otwórz **Baza-BidRoom.cmd**. To `psql` z domyślnym trybem tylko do odczytu, połączeniem do `bidroom_dev` i automatycznie pobranym lokalnym hasłem.

```sql
\dt
\d app_user
SELECT id, login_normalized, display_name, role, enabled FROM app_user ORDER BY id;
SELECT auction_number, title, status, current_price, bid_count, end_at FROM auction ORDER BY auction_number;
SELECT * FROM bid ORDER BY accepted_at DESC LIMIT 20;
SELECT event_type, message, received_at FROM diagnostic_log ORDER BY received_at DESC LIMIT 30;
\q
```

Nie pokazuj kolumn hashy haseł ani wyników poufnej konfiguracji na publicznym zrzucie. Dla zwykłego pokazu wystarczają powyższe kolumny.

## pgAdmin i diagram

Jeżeli chcesz graficzny podgląd, zainstaluj [pgAdmin dla Windows](https://www.pgadmin.org/download/pgadmin-4-windows/). W **Register → Server** podaj dowolną nazwę (np. BidRoom lokalnie), następnie dane z tabeli: host, port, maintenance database `bidroom_dev`, username `bidroom`, jego hasło DB. Połącz się po uruchomieniu bazy.

Rozwiń **Databases → bidroom_dev → Schemas → public → Tables**. Prawy przycisk na tabeli → **View/Edit Data → First 100 Rows**. Dla diagramu użyj **ERD For Database** z menu bazy lub **Generate ERD** dla schematu, zależnie od wersji interfejsu; narzędzie odczytuje relacje FK z PostgreSQL. Funkcje generowania diagramu z bazy/schematu/tabeli opisuje [dokumentacja ERD pgAdmin](https://www.pgadmin.org/docs/pgadmin4/latest/erd_tool.html).

Bez instalowania pgAdmin: **Schemat-bazy.cmd** otwiera [gotowy diagram SVG](database-schema.svg). [Opis relacji i Mermaid](DATABASE_SCHEMA.md) oraz [model danych](DATABASE.md) są w repo. Faktyczny schemat tworzą migracje `server/src/main/resources/db/migration/V001__initial_schema.sql` i kolejne. Flyway uruchamia je przy starcie serwera — nie trzeba ręcznie wklejać SQL do pgAdmin.

## Co gdzie zapisujemy

| Tabela / katalog | Zawartość |
|---|---|
| `app_user` | Konta, proste numery BIGINT, role; hasła wyłącznie jako hash |
| `auction` / `bid` | Aukcje, terminy i przyjęte oferty |
| `auction_photo` | Zdjęcie przypisane FK do aukcji; bajty obrazu i metadane, centralnie na serwerze |
| `auction_report` | Metadane automatycznych raportów |
| `command_result` | Wyniki requestId, chroniące przed podwojeniem polecenia |
| `auction_event` / `security_audit` | Transakcyjny audyt aukcji i operacji dotyczących kont |
| `diagnostic_log` | Logi obu stron; `TASK_STARTED`, `TASK_FINISHED`, `REPORT_GENERATED` i inne zdarzenia |
| Indeks `client_log_dedup` w `diagnostic_log` | Deduplikacja wpisów klienta po identyfikatorach instancji i zdarzenia |
| `flyway_schema_history` | Wykonane wersje migracji |
| `%USERPROFILE%/BidRoomData/server/reports/` | Osobne `aukcja-NR-tytul.txt` oraz `raport-ogolny.txt` |
| `%USERPROFILE%/BidRoomData/server/console*.log` | Diagnostyka uruchomienia/awarii procesu, gdy zapis do bazy jest jeszcze niemożliwy |

Zdjęcia **nie są zewnętrznymi linkami ani plikami przechowywanymi tylko u wystawiającego**. Trafiają przez serwer do PostgreSQL i są objęte backupem. Klient ma jedynie ograniczony cache. Wszystkie komputery łączą się z serwerem Java, a nie bezpośrednio z bazą.

## Backup i VPS

`scripts/backup-db.ps1` zapisuje dump poza projektem w `%USERPROFILE%/BidRoomData/backups`. `scripts/restore-test-db.ps1` odtwarza kopię do oddzielnej bazy testowej. Zachowuj też prywatny sekret HMAC, aby po migracji móc rozpoznawać dawne powtórzenia requestId. Nigdy nie publikuj backupów.

Na VPS baza nazywa się `bidroom`, działa wewnątrz sieci Compose pod `postgres:5432` i nie publikuje portu na Internet. Dane są w wolumenie `production-db`. Podgląd przez SSH:

```bash
sudo docker compose --env-file /etc/bidroom/server.env -f /opt/bidroom/deploy/compose.production.yaml exec postgres psql -U postgres -d bidroom
```

To administracyjny podgląd na serwerze. Nie otwieraj portu PostgreSQL publicznie dla klientów BidRoom. Wdrożenie, backup Linux i migracja: [DEPLOYMENT](DEPLOYMENT.md).
