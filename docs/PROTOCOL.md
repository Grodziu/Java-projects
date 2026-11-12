# Kontrakt TCP/JSON v2

Kontrakt implementacyjny. Zmianę nazw/pól należy wprowadzić jednocześnie w `common`, obu stronach, testach i tym pliku. Wszystkie daty to UTC ISO-8601; ID użytkowników jako dodatni `long`, pozostałe identyfikatory UUID; kwoty całkowite `long`. Klient nie przekazuje autorytatywnego czasu przyjęcia oferty.

## Transport

- Jedno trwałe połączenie TCP na uruchomionego klienta; publicznie TLS przez JSSE. Lokalny profil działa wyłącznie na loopback.
- Ramka: 4 bajty długości w porządku big-endian, następnie dokładnie tyle bajtów JSON UTF-8. Długość oznacza bajty, nie znaki; dozwolone 1–65 536.
- Odrzuć ujemną/zerową/zbyt dużą długość przed alokacją. Obsłuż fragmentację ramki i wiele ramek w jednym odczycie. Timeout w połowie ramki kończy połączenie; nie interpretuj reszty jako nowego nagłówka.
- Parser ogranicza zagnieżdżenie JSON do 20 poziomów, pojedynczy tekst do 32 768 znaków i liczbę do 20 znaków. Dzięki temu mieści się 21 848 znaków base64 jednej porcji raportu. Krótsze limity pól biznesowych obowiązują niezależnie. Bez deserializacji dowolnych klas i `ObjectInputStream`.
- Jedna pętla odczytu; odpowiedzi kojarzone po `requestId`, zdarzenia po typie. Wszystkie zapisy serializowane przez kolejkę nadawcy. Maks. 16 niezakończonych żądań klienta, obsługa poleceń jednej sesji kolejno.
- Timeout połączenia/HELLO np. 5 s; odczyt pojedynczej ramki maks. 10 s od pierwszego bajtu, heartbeat co 15 s, rozłączenie po 45 s bez oznak aktywności. Długie operacje raportowe są porcjowane.

## Koperty

```json
{
  "protocolVersion": 2,
  "kind": "REQUEST",
  "requestId": "a0c7f3a5-5c2d-4314-97e3-6490f56bfcdf",
  "operation": "PLACE_BID",
  "payload": {"auctionId": "eb595ffb-6475-43bc-aa46-8dc1c98cff164", "amount": 150}
}
```

```json
{
  "protocolVersion": 2,
  "kind": "RESPONSE",
  "requestId": "a0c7f3a5-5c2d-4314-97e3-6490f56bfcdf",
  "ok": true,
  "serverTime": "2026-11-09T12:00:00Z",
  "payload": {"bidId": "1357dd47-5148-4ef1-8f47-8e143f9683e5", "amount": 150, "acceptedAt": "2026-11-09T11:59:59.990Z", "auctionVersion": 7, "endAt": "2026-11-09T12:00:49.990Z"}
}
```

Błąd: `ok:false`, `error:{code, message}`, bez stosu wyjątków, SQL i sekretów. Minimum jest podane w komunikacie `BID_TOO_LOW`; aktualny snapshot można pobrać przez `GET_AUCTION`. Komunikat UI jest polski. `serverTime` służy synchronizacji prezentacji, a nie gwarancji zerowego opóźnienia. Klient nie synchronizuje zegara odpowiedzią na mutację, ponieważ odtworzony wynik może zawierać dawny czas.

Zdarzenie: `kind:"EVENT"`, `eventType`, `serverTime`, `payload`, bez `requestId`. `AUCTION_SNAPSHOT` zawiera pełny publiczny stan z `auctionId`, `version`, `status`, tytułem, właścicielem, `startAt`, `initialEndAt`, `endAt`, ceną startową, minimalnym przebiciem, najwyższą ofertą/liderem albo `null`, liczbą ofert i wynikiem. `CATALOG_INVALIDATED` jest sygnałem do odświeżenia listy z krótkim scalaniem kolejnych sygnałów. `SESSION_REVOKED` kończy zalogowaną sesję. Szczegóły szkiców nie są rozsyłane publicznie.

## Operacje

| Operacja | Wejście / odpowiedź | Uprawnienie |
|---|---|---|
| `HELLO` | Wersja klienta/protokołu i losowy `installationId` / wersja serwera, czas, limity, dostępność | Przed logowaniem; pierwsze żądanie. |
| `REGISTER` | login, displayName, password / ID nowego USER | Publiczne, ograniczenie częstotliwości. |
| `LOGIN` | login, password / token, user, uprawnienia, czas wygaśnięcia | Publiczne. |
| `SESSION_RESUME` | token / użytkownik lub `SESSION_EXPIRED` | Przed logowaniem nowego socketu. |
| `LOGOUT` | Bez danych / potwierdzenie unieważnienia | Zalogowany. |
| `PING` | Bez danych / czas serwera | Każdy aktywny socket. |
| `LIST_AUCTIONS` | `view` (active/scheduled/mine/history), `category`, `query`, `offset`, `pageSize` / `items`, `nextOffset`, `hasMore` | Zalogowany; `mine` udostępnia własne szkice. |
| `GET_AUCTION` | auctionId / snapshot i opis | Zgodnie z widocznością. |
| `GET_BIDS` | auctionId, afterSequence, limit / `items`, `nextSequence`, `hasMore` | Zgodnie z widocznością. |
| `SUBSCRIBE_AUCTION` | auctionId / aktualny snapshot po rejestracji obserwatora | Maks. 4 subskrypcje/socket. |
| `UNSUBSCRIBE_AUCTION` | auctionId / potwierdzenie | Własna subskrypcja. |
| `CREATE_DRAFT` | Pola szkicu / ID i snapshot | Zalogowany. |
| `UPDATE_DRAFT` | auctionId, expectedVersion, pola / snapshot | Właściciel/admin; tylko DRAFT. |
| `PUBLISH_AUCTION` | auctionId, expectedVersion / snapshot | Właściciel/admin; poprawne warunki, zdjęcie i limit automatów. |
| `CANCEL_AUCTION` | auctionId, reason / snapshot | Właściciel przed aktywacją; admin także podczas trwania. |
| `PLACE_BID` | auctionId, amount / trwały wynik przyjęcia | Zalogowany, cudza ACTIVE. |
| `GET_COMMAND_RESULT` | commandRequestId / FOUND z wynikiem albo UNKNOWN | Wyłącznie wyniki tego użytkownika. |
| `EXPORT_REPORT_START` | auctionId / reportId, liczba bajtów, SHA-256 | Zakończona/anulowana widoczna aukcja. |
| `EXPORT_REPORT_CHUNK` | reportId, offset / base64, nextOffset, eof | Właściciel uchwytu eksportu. |
| `LOG_BATCH` | Lista logów / lista trwale zapisanych lub już istniejących eventId | Uwierzytelniona sesja. |
| `ADMIN_LIST_USERS` | query, offset, pageSize / items, nextOffset, hasMore; użytkownicy bez hashy | ADMIN. |
| `ADMIN_CREATE_USER` | login, displayName, password, role / użytkownik | ADMIN. |
| `ADMIN_SET_USER_STATUS` | userId, enabled, reason / potwierdzenie | ADMIN, ochrona ostatniego administratora. |
| `ADMIN_GET_LOGS` | origin, userId, auctionId, from, to, level, offset, pageSize / items, nextOffset, hasMore | ADMIN. |

Domyślna strona 20, maksymalna 100. Długie opisy wyłącznie w szczegółach, nie na liście. Rozmiar strony/ramki ogranicza też rzeczywista liczba bajtów; przycięcie strony musi zwrócić poprawny wskaźnik dalszego pobierania, nie uszkodzony JSON. Tekst tytułu do 120 znaków, opis do 4000, powód do 500. Dla wyszukiwania i sortowania tylko jawna lista dozwolonych pól.

W implementacji 1.1.0 `GET_BIDS` domyślnie zwraca 50 ofert. Kursor `offset` ma zakres 0–1 000 000. Strony list są przycinane do 60 000 bajtów koperty. Kategorie to `ELECTRONICS`, `BOOKS`, `ACCESSORIES`, `OTHER`; pusty tekst oznacza brak filtra.

Pola szkicu: `auctionId` (null przy tworzeniu), `expectedVersion`, `title`, `description`, `category`, `startingPrice`, `minIncrement`, `startAt` (null = start przy publikacji), `durationSeconds` (60–604800). Kwoty mają zakres 1–10^12.

`PLACE_BID` zwraca `bidId`, `amount`, `acceptedAt`, `auctionVersion`, `endAt` oraz pełny snapshot w `auction`. Snapshot odpowiada rekordowi `Models.Auction`: `auctionId`, `ownerId`, `ownerName`, `title`, `description`, `category`, `status`, `startingPrice`, `minIncrement`, `startAt`, `initialEndAt`, `endAt`, `durationSeconds`, `currentPrice`, `leaderId`, `leaderName`, `bidCount`, `version`, `publishedAt`, `finishedAt`, `cancelReason`, `auctionNumber`, `photoVersion`. Wynik wyprowadza się z `status`: zwycięzca i cena istnieją wyłącznie dla FINISHED z co najmniej jedną ofertą; lider w CANCELLED nie jest zwycięzcą.

`LOG_BATCH` przekazuje obiekt `{entries:[...]}`, odpowiedź to `{eventIds:[...]}`. `GET_COMMAND_RESULT` zwraca `{status:"FOUND",result:...}` albo `{status:"UNKNOWN"}`. Raport startuje odpowiedzią `{reportId,bytes,sha256,filename}`. Sesja loginu to `{token,user,expiresAt}`; `SESSION_RESUME` zwraca `{user}`. Rejestracja zwraca bezpieczny rekord użytkownika `{id,login,displayName,role,enabled,legacyId}`.

## Idempotencja i utrata odpowiedzi

Mutacje aukcji oraz administracyjne tworzenie/zmiana kont używają trwałego klucza `(authenticatedUserId, requestId)` i skrótu kanonicznego wejścia zawierającego nazwę operacji. Nie haszuj przypadkowego porządku pól JSON; normalizuj z typowanego DTO. Nie zapisuj hasła ani pełnego payloadu z hasłem w wynikach. Dla operacji z hasłem porównanie wejść opieraj na HMAC z sekretem serwera lub dedykowanej bezpiecznej reprezentacji, nie szybkim publicznym hashu hasła.

`REGISTER` nie ma jeszcze użytkownika ani kontekstu idempotencji: unikatowy login zapobiega podwójnemu kontu. Przy nieznanym wyniku GUI proponuje zalogowanie się tym samym loginem. `LOGIN`, odczyty, subskrypcje i uchwyty raportu nie wymagają trwałego zapisu wyniku. `LOG_BATCH` ma własną deduplikację po identyfikatorach zdarzeń.

Klient dla wysłanej oferty zapisuje lokalnie niezbędny rekord oczekiwania: ID użytkownika, requestId, auctionId, amount. Bez hasła/tokena. Po odpowiedzi końcowej usuwa rekord; po ponownym logowaniu tym samym kontem odpytuje wynik. `UNKNOWN` nie dowodzi odrzucenia, bo stara operacja może jeszcze trwać — bezpiecznie ponów wyłącznie ten sam requestId i payload. Nie generuj automatycznie nowego ID. Nigdy nie wysyłaj później nowych ofert klikniętych offline.

Podwójne kliknięcie przycisku w trakcie oczekiwania nie wysyła nowego polecenia. Trwały wynik biznesowy tego samego polecenia pozostaje taki sam nawet po zmianie ceny; nowa świadoma próba użytkownika otrzymuje nowe ID.

## Raport i logi

Raport generowany z jednego spójnego obrazu DB (transakcja odczytowa REPEATABLE READ), do pliku tymczasowego serwera poza blokadą aukcji. Porcja maks. 16 KiB danych przed base64. Uchwyt związany z użytkownikiem, ważny 5 minut, maks. 2 otwarte raporty/użytkownik, 64 łącznie; generowanie jest serializowane jednym wykonawcą i blokadą. Raport powstaje automatycznie po FINISHED/CANCELLED i pozostaje w katalogu `reports/`; pobranie usuwa tylko uchwyt, nigdy plik raportu. Restart uzupełnia brakujące raporty. Plik `raport-ogolny.txt` zawiera rejestr wygenerowanych raportów, a zapis raportu i zdarzenie `REPORT_GENERATED` są rejestrowane wspólnie w transakcji DB. Użytkownik ponawia pobranie, jeśli uchwyt wygasł. Klient weryfikuje rozmiar i SHA-256 przed uznaniem zapisu za ukończony.

Log klienta: `eventId`, `installationId`, `clientOccurredAt`, `level`, `eventType`, opcjonalne `auctionId`, `commandRequestId`, ograniczony `message`. Serwer dodaje `receivedAt`, identyfikator sesji i zgłaszającego. Maks. 50 wpisów/paczkę, message do 1000 znaków oraz limit ramki. Zdarzenia nie zawierają haseł, tokenów ani pełnego pakietu LOGIN. ACK oznacza zapis do DB, nie tylko do kolejki. Brak ACK pozostawia wpis w lokalnym buforze. Ponowienia nie mnożą wpisów.

## Błędy i zgodność

Minimalne kody: `BAD_REQUEST`, `PROTOCOL_UNSUPPORTED`, `AUTH_REQUIRED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `ACCOUNT_DISABLED`, `FORBIDDEN`, `NOT_FOUND`, `LOGIN_TAKEN`, `VALIDATION_ERROR`, `VERSION_CONFLICT`, `AUCTION_NOT_ACTIVE`, `AUCTION_ENDED`, `BID_TOO_LOW`, `OWN_AUCTION`, `CAPACITY_REACHED`, `RATE_LIMITED`, `REQUEST_ID_CONFLICT`, `TEMPORARILY_UNAVAILABLE`, `RESULT_UNKNOWN`, `REPORT_EXPIRED`, `PHOTO_REQUIRED`, `PHOTO_EXPIRED`, `INTERNAL_ERROR`.

Brak odpowiedzi/timeout transportu pokazuje „Nie otrzymano potwierdzenia — sprawdzamy wynik”, a nie „Oferta odrzucona”. Nieznana główna wersja protokołu kończy negocjację czytelnym komunikatem. Nie zezwalaj na automatyczne zejście z TLS do połączenia jawnego po błędzie certyfikatu.



## Zdjęcia i diagnostyka w v2

| Operacja | Wejście / odpowiedź | Uprawnienie |
|---|---|---|
| `PHOTO_BEGIN` | auctionId, expectedVersion, bytes, sha256 / uploadId, chunkBytes | Właściciel lub ADMIN, wyłącznie DRAFT. |
| `PHOTO_CHUNK` | uploadId, offset, base64 / nextOffset | Właściciel transferu; dokładnie następna pozycja. |
| `PHOTO_FINISH` | uploadId / snapshot aukcji | Transakcyjny zapis zdjęcia i wersji; trwała idempotencja po requestId i skrócie zdjęcia. |
| `PHOTO_ABORT` | uploadId / potwierdzenie | Usunięcie bufora transferu tego użytkownika. |
| `GET_PHOTO` | auctionId / available oraz version, bytes, sha256, width, height | Widoczna aukcja. |
| `GET_PHOTO_CHUNK` | auctionId, version, offset / base64, nextOffset, eof | Widoczna aukcja; wersja zdjęcia musi się zgadzać. |
| `GET_REPORT_STATUS` | auctionId / ready, filename, generatedAt (gdy gotowy) | Widoczna aukcja. |
| `ADMIN_SERVER_STATS` | pusty obiekt / uptimeSeconds, clients/maxClients, liveAuctions/maxAuctions, tasks, heapUsedBytes/heapMaxBytes, cpuPercent, platformThreads, databaseActive/databasePoolMax, counts, reportsPath, tls | ADMIN. |

JPG/PNG do 2 MiB, maksymalnie 16 megapikseli wejścia. Serwer odczytuje rzeczywisty format, ogranicza wymiary przed dekodowaniem, skaluje do 1280 px i zapisuje JPEG bez metadanych. Do 8 transferów po 2 MiB, najwyżej 2 dekodowania równolegle, transfer wygasa po 5 minutach. Porcje maks. 16 KiB. Zapis binarny `auction_photo.content` i aktualizacja `auction.photo_version` oraz `version` są jedną transakcją pod blokadą aukcji. Po publikacji zdjęcia są niezmienne.

`tasks` zawiera liczbę, podział według roli i do 100 faktycznie wykonywanych zadań z ID wątku, nazwą, czasem startu i flagą `virtual`. JVM MXBean liczy osobno wątki platformowe; nie przedstawiamy go jako liczby wszystkich wątków wirtualnych.

Wersja 1 protokołu nie jest zgodna z v2: wymagana jest aktualizacja klienta razem z serwerem. Migracje V002/V003 przeliczają zapisane wyniki dawnych poleceń i zachowują requestId oraz skróty HMAC. `legacyId` w DTO konta pozwala przenieść lokalne oczekujące oferty z identyfikatora UUID na numer konta.
