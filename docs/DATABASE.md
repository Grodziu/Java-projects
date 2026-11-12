# PostgreSQL i trwałość danych

Model jest wdrożony przez migracje V001–V003 w `server/src/main/resources/db/migration/`. [Schemat graficzny](database-schema.svg) i [opis relacji](DATABASE_SCHEMA.md) odpowiadają wersji 1.1. Wyłącznie moduł serwera ma sterownik PostgreSQL i dane dostępu. Flyway utrzymuje dodatkowo własną tabelę historii migracji.

## Zasady

- PostgreSQL 17; JDBC, HikariCP, Flyway. Przy wyborze wersji Flyway sprawdź wymagany moduł obsługi PostgreSQL. Przypnij kompatybilne wersje w buildzie.
- Migracje `server/src/main/resources/db/migration/V001__initial_schema.sql` itd.; nie zmieniaj migracji, która była już użyta we współdzielonej bazie.
- `BIGINT IDENTITY` dla `app_user.id` i czytelnego `auction.auction_number`; UUID dla pozostałych identyfikatorów, `BIGINT` dla punktów i numerów wersji, `TIMESTAMPTZ` dla czasu; aplikacja operuje w UTC.
- PostgreSQL zapisuje czas z precyzją mikrosekund. Normalizuj `Instant` do tej precyzji przed decyzją i utrwaleniem, żeby pamięć, testy i DB nie różniły się nanosekundami. To nie jest zaokrąglanie reguły do całych sekund.
- Constraints, FK i indeksy uzupełniają walidację Javy. Wszystkie wartości użytkownika w SQL parametryzowane. Sortowanie tylko z listy dozwolonej.
- `Connection` nie jest współdzielony między zadaniami. Jedna operacja wypożycza jedno połączenie, kończy transakcję i zwraca je przez `try-with-resources`.

## Tabele

| Tabela | Kluczowe kolumny i ograniczenia |
|---|---|
| `app_user` | `id`, `login_normalized UNIQUE`, `display_name`, `password_hash`, `password_salt`, `password_algorithm`, `password_iterations`, `role`, `enabled`, `created_at`, `updated_at`; role USER/ADMIN, bez usuwania historii. |
| `auction` | `id`, `owner_id FK`, `title`, `description`, `category`, `status`, `starting_price`, `min_increment`, `start_at`, `initial_end_at`, `end_at`, `duration_seconds`, `current_price NULL`, `leader_id NULL FK`, `bid_count`, `version`, `published_at NULL`, `finished_at NULL`, `cancel_reason NULL`, `created_at`, `updated_at`. |
| `auction_photo` | `auction_id PK/FK`, `version`, `content BYTEA` do 2 MiB, `sha256`, `width`, `height`, `created_at`. Obraz znajduje się na serwerze w PostgreSQL, razem z kopią bazy. |
| `auction_report` | `auction_id PK/FK`, `filename UNIQUE`, `sha256`, `bytes`, `generated_at`. Pliki są trwałe w `reports/` na serwerze, odtwarzane automatycznie po utracie. |
| `bid` | `id`, `auction_id FK`, `bidder_id FK`, `sequence_no`, `amount`, `accepted_at`, `command_request_id`; UNIQUE `(auction_id, sequence_no)`, UNIQUE `(bidder_id, command_request_id)`, dodatnia kwota. |
| `auction_event` | `id`, `auction_id FK`, `auction_version`, `event_type`, `actor_id NULL FK`, `occurred_at`, `details JSONB`; publikacja/start/oferta/przedłużenie/zakończenie/anulowanie. UNIQUE `(auction_id, auction_version, event_type)` przy najwyżej jednym takim typie na zmianę. |
| `command_result` | `user_id FK`, `request_id`, `operation`, `input_digest`, `result JSONB`, `created_at`; PK `(user_id, request_id)`, wynik bez sekretów i z utrwalonym czasem decyzji. |
| `security_audit` | `id`, `actor_id NULL`, `target_user_id NULL`, `action`, `occurred_at`, `details JSONB`; tworzenie/blokowanie kont, administracja i istotne zdarzenia bezpieczeństwa. |
| `diagnostic_log` | `id`, `origin` CLIENT/SERVER, `origin_instance_id`, `event_id`, `reported_by_user_id NULL`, `client_occurred_at NULL`, `received_at`, `level`, `event_type`, `auction_id NULL`, `command_request_id NULL`, `message`; deduplikacja po źródle i ID. |

Deduplikacja logów klienta: unikatowy `(reported_by_user_id, origin_instance_id, event_id)` dla CLIENT; wszystkie trzy wartości wymagane po przyjęciu paczki. Dla SERVER osobny unikatowy `(origin_instance_id, event_id)` z indeksem częściowym. Nie polegaj na zwykłym UNIQUE obejmującym nullable userId, który dopuszcza wielokrotne NULL. Audyt serwera nie jest nadpisywany przez deklarację klienta.

`current_price` i `leader_id` są NULL przy braku ofert; wynik FINISHED z ofertami wynika z utrwalonej ostatniej oferty i tych pól. Dla CANCELLED nie ogłaszaj lidera zwycięzcą. Wartości punktów i przebicia 1..10^12; liczba ofert/wersja nieujemna. Warunki czasu sprawdzaj przy publikacji; szkic może mieć niekompletne terminy. Opublikowane terminy nie są NULL i `end_at >= initial_end_at > start_at`.

Nie usuwaj użytkowników/aukcji kaskadowo wraz z ofertami. Konta blokuj, aukcje anuluj. `published_at` jest ustawiane raz przy publikacji; NULL oznacza prywatny szkic także po anulowaniu. Publiczne katalogi, archiwa i odczyty nie ujawniają takich szkiców. Kategorie można przechowywać jako ustalone kody. Sesje działają w pamięci — nie jest potrzebna tabela tokenów w pierwszej wersji.

## Indeksy i odczyty

- Katalog: `(status, start_at, id)`; osobnego indeksu po `end_at` nie dodano w 1.0.0. Jego potrzebę należy oprzeć na pomiarze większej historii.
- Własne aukcje: `(owner_id, created_at, id)`.
- Oferty: unikatowy `(auction_id, sequence_no)` wystarcza do historii i pobierania stron.
- Zdarzenia aukcji: `(auction_id, occurred_at, id)`.
- Logi: `(received_at, id)`, `(reported_by_user_id, received_at)`, `(auction_id, received_at)` oraz indeksy deduplikacji.
- Nie dodawaj wyszukiwarki pełnotekstowej ani indeksu dla każdego pola bez potrzeby. Wszystkie listy mają paginację; nie pobieraj całej historii do UI.

## Transakcje

Podstawowy poziom READ COMMITTED plus `SELECT ... FOR UPDATE` dla zmienianej aukcji. Aktualizuj `version=version+1` i zapisuj tę samą wersję w snapshotach i zdarzeniach. Numer oferty przydzielaj z chronionego `bid_count+1`, nie z `MAX(...)` bez blokady.

Przyjęcie oferty obejmuje w jednej transakcji: sprawdzenie stanu, nowy `bid`, aktualizację `auction`, zdarzenie przyjęcia i opcjonalnego przedłużenia, `command_result`. Zakończenie zapisuje stan/wynik i zdarzenie końca atomowo. Nie ma okna, w którym zaakceptowana cena istnieje tylko w pamięci.

Blokada Javy ogranicza wyścigi między automatami i klientami; blokada wiersza oraz constraints zabezpieczają trwały stan. Konfiguruj limit oczekiwania na połączenie i blokadę, np. 3 s, oraz `statement_timeout`, np. 5 s. Ustawienia raportów mogą być dłuższe, ale ograniczone. Błędy DB nie ujawniają tekstu SQL klientowi.

Rejestracja korzysta z UNIQUE na znormalizowany login, także gdy dwa żądania przyjdą jednocześnie. Uprawnienia i blokowanie ostatniego administratora sprawdzaj w transakcji chroniącej cały zbiór administratorów (np. ustalona transakcyjna blokada doradcza). Sprawdzenie liczby administratorów poza transakcją nie wystarcza.

Idempotencja przeżywa restart. Dla tego projektu nie usuwaj automatycznie wyników poleceń aukcyjnych. Dokładną implementację konfliktów i ponowień sprawdź testami z realnym PostgreSQL.

## Logi, kopie i utrzymanie

Audyt stanowi część operacji biznesowej. Logi diagnostyczne są oddzielne i mogą być zapisywane paczkami. Eksport raportu opiera się na ofertach i audycie aukcji, a nie na zawodnych opisach z logu klienta.

Baza developerska i testowa są oddzielne, np. `bidroom_dev` i `bidroom_test`. Zautomatyzowane czyszczenie testów musi sprawdzać nazwę testowej bazy i nie dotykać developerskiej. Nie czyść DB przy normalnym starcie.

Dane kontenera przechowuj w nazwanym wolumenie Dockera. `docker compose down` bez `-v` zachowuje wolumen; usuwanie wolumenu jest osobną destrukcyjną operacją. Aktywne pliki PostgreSQL nie mogą leżeć w synchronizowanym OneDrive. Wolumen nie zastępuje kopii zapasowej.

Przygotuj skrypty kopii `pg_dump` i odtworzenia do oddzielnej bazy, z próbą odczytu danych po odtworzeniu. Kopia zawiera konta i logi, więc nie trafia do publicznego repozytorium ani instalatora. Przy migracji hosta przenoś dump, konfigurację i wymagane sekrety; nie kopiuj na żywo katalogu danych między różnymi środowiskami.

