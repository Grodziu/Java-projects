# Rejestr decyzji

## Ustalone z użytkownikiem — nie pytaj ponownie

| Temat | Decyzja |
|---|---|
| Temat projektu | Aplikacja aukcyjna zaakceptowana przez prowadzącego; BidRoom to robocza nazwa nadana w tym pakiecie. |
| Współbieżność | `java.util.concurrent`; osobni klienci i automaty aukcji; wiele aukcji. |
| Znaczenie automatu | Sterownik cyklu życia pojedynczej aukcji, nie bot przebijający do limitu. |
| GUI | JavaFX na Windows, motyw PK. |
| Rejestracja | Samodzielna oraz możliwość utworzenia konta przez administratora. |
| Role | Administrator, wystawiający/opiekun i licytujący. Sposób odwzorowania na konta doprecyzowano poniżej. |
| Termin | Ustalany przez wystawiającego, rozstrzygany przez zegar serwera. |
| Przedłużenie | Poniżej minuty +30 s, najwyżej do minuty pozostałego czasu, przy każdej poprawnej ofercie. |
| Trwałość | Odtwarzanie zapisanych aukcji, ofert i przedłużonego terminu po restarcie. |
| Zmiany trwającej aukcji | Bez zmiany ceny początkowej i terminu przez wystawiającego. Anulowanie z uprawnieniami i historią. |
| Sieć docelowa | Użytkownicy w różnych sieciach, centralny serwer, automatyczne połączenie. |
| Dystrybucja | Instalator Windows z runtime; użytkownik nie instaluje Javy, bazy ani Dockera. |
| System serwera | Linux dopuszczony; kod serwera ma działać także na Windows w trakcie rozwoju. |
| Sprzęt obecnie | Wszystko rozwijamy na obecnym laptopie. Zakup mini PC/VPS odłożony. |
| Termin zaliczenia | Użytkownik zarządza terminem sam; nie pytaj ponownie o datę. |

## Decyzje techniczne przyjęte w tym pakiecie

To uzasadnione domyślne rozwiązania, a nie dodatkowe wypowiedzi prowadzącego. Codex może dopracować szczegóły bez zmiany efektu użytkowego; istotną zmianę odnotuj tutaj.

- Java/JDK 21 oraz JavaFX 21. Najnowsze stabilne poprawki kompatybilnych linii przypnij podczas pierwszego przygotowania builda.
- PostgreSQL 17, JDBC, HikariCP, migracje Flyway. Dokładne zgodne wersje bibliotek przypnij w nadrzędnym POM; bez `LATEST`, zakresów i SNAPSHOT.
- Maven Wrapper, moduły common/server/client/loadtest. Bez Springa i ORM na start.
- TCP + ramkowany JSON UTF-8. TLS obowiązkowe w profilu publicznym, jawny lokalny tryb bez TLS wyłącznie na loopback.
- Jedna instancja serwera aukcyjnego. Wątki wirtualne uruchamiane przez `ExecutorService`, główne zadania `Callable`.
- Brak e-maila i SMTP. Login, nazwa wyświetlana, hasło. Sesje w pamięci serwera; restart może wymagać ponownego logowania.
- W wersji 1.0 wystarczały grafiki kategorii. Polecenie z 11.11.2026 rozszerza zakres: własne zdjęcie wymagane przed publikacją, przechowywane na serwerze w bazie, dostępne wszystkim uprawnionym klientom przez TCP/TLS.
- Całkowite punkty; pierwsza oferta równa cenie startowej jest dopuszczalna. Brak salda.
- Tylko szkic jest edytowalny. Właściciel może anulować własną zaplanowaną aukcję, administrator również aktywną.
- Terminy pokazywane w Europe/Warsaw, zapis w UTC.
- Rejestracja ma domyślnie rolę USER; pierwszego administratora inicjalizuje konfiguracja serwera.
- Model ról: USER może wystawiać i licytować cudze aukcje; ADMIN ma dodatkowe uprawnienia. Wystawiający jest relacją do aukcji, nie osobnym rodzajem konta.
- Roboczy profil laptopowy: 32 połączenia, 16 aukcji opublikowanych niezakończonych, pula DB maks. 4. To limity konfiguracyjne, nie ograniczenie modelu.

## Nadal otwarte, ale nie blokujące kodowania

### Uściślenia implementacji z 10 listopada 2026

- Wybrano natywny PostgreSQL 17.11, zgodnie z dopuszczonym wariantem oszczędzającym RAM. Nie zmieniano istniejącej instalacji PostgreSQL 18 ani globalnego PATH. Dane i sekrety są poza OneDrive.
- Lokalny port TCP aplikacji to **5001**: Windows odmawiał bindowania portu 5000 błędem uprawnień socketu. Sprawdzono dostępność 5001. Port PostgreSQL pozostał 55432.
- JDK/JavaFX 21.0.13, Maven 3.9.16, Wrapper 3.3.4; dokładne wersje zależności w POM. Runtime instalatora powstaje przez jlink/jpackage; WiX 3.14.1.
- Katalog protokołu używa `view` i `offset`/`nextOffset`, aby porcjowanie według rozmiaru ramki nie gubiło rekordów. Czas trwania szkicu jest `durationSeconds`, a `startAt:null` oznacza start przy publikacji.
- Diagnostyka jest ograniczona do 5000 rekordów i 10 MiB bufora. Równoległe haszowanie: 2, logowanie/rejestracja: 60 prób/minutę/adres. Limity nie są deklaracją zmierzonej maksymalnej wydajności.
- Zwykły klient blokuje własny katalog sesji; trzy procesy pokazu używają oddzielnych katalogów. Zapobiega to nadpisywaniu bufora przez dwa procesy.
- Sprawozdanie nie otrzymuje wymyślonych nazwisk. Test czystego Windows, rzeczywistego hostingu i różnych sieci pozostaje osobno oznaczony.

1. Autorzy podani 12.11.2026: Piotr Grodny, Marcin Gurdziel, Oliwia Jarosz. Kurs „Programowanie w języku Java”, Informatyka w inżynierii komputerowej, semestr 5, P1, 2026/2027. Oliwia przygotowuje sprawozdanie, Marcin przejmuje wdrożenie; instrukcja i streszczenie kontekstu w HANDOFF.md. Publikacja kodu w publicznym Grodziu/Java-projects zatwierdzona przez użytkownika, bez danych kont i prywatnej konfiguracji.
2. Konkretny hosting, domena, certyfikat i dostęp administracyjny — potrzebne przed publicznym wdrożeniem. Nie wymyślaj adresu działającej usługi.
3. Ewentualne płatne podpisanie instalatora — nie jest konieczne do kompilacji, nie deklaruj posiadania certyfikatu. Podpisywanie nie oznacza obchodzenia zabezpieczeń Windows.
4. Ostateczne limity i deklarowana wydajność — wynik pomiarów, nie obietnica marketingowa.

## Rozwiązania poza zakresem aktualnej specyfikacji

### Rozszerzenie zaakceptowane 11 listopada 2026

- Wersja 1.1: bogatsza paleta inspirowana wydziałami PK, dostarczony wzór techniczny, kolorowe awatary, oficjalna ikona PK, usunięcie szarego wypełnienia nagłówków tabel.
- Raport każdej terminalnej aukcji powstaje automatycznie, ma czytelną nazwę i wpis w `raport-ogolny.txt` oraz `diagnostic_log`. Przycisk pobiera gotowy raport.
- Administrator ogląda aktualne zadania i ID wątków, pamięć/CPU, liczbę klientów, automatów i połączeń z bazą.
- `app_user.id` jest liczbą BIGINT; dotychczasowe UUID zachowane jako `legacy_uuid` dla migracji oczekujących ofert. Aukcje mają dodatkowe czytelne numery. Protokół v2 wymaga aktualizacji obu stron.
- Użytkownik nie ma hostingu i polecił wybrać wariant. Do testów internetowych i demonstracji wybieramy VPS Linux x86-64 2 vCPU / 4 GB RAM (np. OVHcloud VPS-1); laptop służy do rozwoju. Nie kupiono usługi. Rzeczywiste wdrożenie wymaga zakupu/dostępu przez właściciela, publicznego hosta, certyfikatu i odbioru dwóch sieci. Przygotowano `docs/DEPLOYMENT.md` i pakowanie serwera.

Jedna aukcja jako limit modelu, SQLite, sam nick bez konta, ręczne wpisywanie IP w normalnym GUI, obowiązek wspólnego hotspotu, obligatoryjny zakup komputera i baza u klienta nie realizują obecnych ustaleń. Nie wprowadzaj tych ograniczeń w celu szybszego ukończenia projektu.
