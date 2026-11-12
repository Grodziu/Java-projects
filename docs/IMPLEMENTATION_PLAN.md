# Plan realizacji i stan prac

Stan 11.11.2026: **działająca implementacja lokalna**. Zaliczone punkty mają dowody w [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md). Otwarte punkty oznaczają brak pełnej weryfikacji, nawet jeżeli kod lub konfiguracja już istnieją. Nie deklarujemy ukończenia publicznego wdrożenia ani całego odbioru.

## Przekazanie 1.1.1 — 12.11.2026

- [x] Pełne proporcje grafik PK, autorzy/kurs/rok, wyrównanie nagłówka i szerokości menu; sprawdzenie JavaFX i skal 100/125/150%.
- [x] Oddzielny start serwera i klienta z kontrolą gotowości TCP oraz czytelnym błędem braku serwera.
- [x] Instrukcja kolejności plików, HANDOFF, mapa kodu i konfiguracje IntelliJ, pgAdmin/psql/diagram, gotowa wiadomość do Marcina.
- [x] Testy 36/36 na PostgreSQL; czysty build; instalator 1.1.1 i paczka serwera, kontrola sekretów.
- [x] Publikacja kodu i pięciu plików wydania w publicznym [Grodziu/Java-projects, v1.1.1](https://github.com/Grodziu/Java-projects/releases/tag/v1.1.1). Potwierdzono zgodność SHA256 wszystkich plików.
- [ ] Odbiór Marcina na drugim Windows oraz wdrożenie i test różnych sieci.

Sprawozdanie końcowe przejęła Oliwia; nie jest wykonywane w ramach tego przekazania. Dane autorów są już podane. Przygotowanie konfiguracji IntelliJ nie jest równoznaczne z wykonaniem pokazu w IDE.

## Rozszerzenie 1.1 — wykonane i sprawdzone 11.11.2026

- [x] Kolory nawigacji/kategorii, wzór techniczny PK, kolorowe awatary i poprawione nagłówki tabel; rzeczywiste zrzuty JavaFX.
- [x] Zdjęcia JPG/PNG w PostgreSQL, porcjowany TCP/TLS, kontrola dostępu/rozmiaru/formatu, wymóg zdjęcia przed nową publikacją.
- [x] Automatyczne trwałe raporty każdej terminalnej aukcji, czytelne nazwy, rejestr ogólny i wpisy w bazie; sprawdzone odzyskanie brakującego pliku.
- [x] ADMIN: rzeczywiste wątki zadań, klienci, automaty, CPU, pamięć, JDBC i liczniki rekordów; USER bez dostępu.
- [x] Migracja numerów kont oraz oczekujących ofert; porównanie danych na odtworzonej kopii, następnie zachowanie danych rzeczywistej bazy.
- [x] Własny instalator 1.1.0 z ikoną PK; aktualizacja lokalnej instalacji i uruchomienie dołączonej JVM bez globalnej Javy.
- [x] Schemat bazy SVG/Mermaid, skrypt lokalnego podglądu `psql`, instrukcje i pakiet serwera VPS bez sekretów.
- [ ] Uruchomienie skryptów wdrożenia na rzeczywistym VPS/Linux, certyfikat publiczny i instalator ze stałym hostem.
- [ ] Wspólna licytacja z dwóch komputerów w różnych sieciach oraz odbiór na drugim/czystym Windows.

Zestaw automatyczny: 34 testy, 0 błędów, 0 pominiętych. W lokalnej bazie zachowano 34 konta, 32 aukcje i 8 ofert; automat utworzył 32 raporty. Dawne aukcje bez fotografii mają oznaczoną ilustrację kategorii. Nie generowano fikcyjnych zdjęć rzeczywistych przedmiotów.

## Etap 0 — powtarzalne środowisko

- [x] Ponowna diagnoza laptopa, JDK 21, pamięci i Dockera; bez zawieszających się bezterminowo sprawdzeń.
- [x] JDK 21 i Maven Wrapper, konkretne wersje zależności, build z bieżącej ścieżki z polskimi znakami.
- [x] Moduły `common`, `server`, `client`, `loadtest`; `.gitignore`, konfiguracja przykładowa bez sekretów.
- [x] PostgreSQL, trwały katalog poza OneDrive (wariant natywny), oddzielne bazy dev/test, healthcheck i migracje Flyway.
- [x] Skrypty sprawdzenia środowiska, startu i zatrzymania własnych usług; jawne limity pamięci.
- [x] Instrukcja rzeczywistych poleceń uruchomienia oraz zapis brakujących warunków, jeśli któreś wymagają interakcji.

Kryterium: build minimalnego projektu działa na JDK 21, a serwer potrafi wykonać migrację i odczyt bazy. Jeśli DB jest chwilowo zablokowana środowiskiem, można niezależnie implementować kod, ale etap DB pozostaje niezaliczony.

## Etap 1 — najkrótszy działający przepływ

- [x] Kodowanie ramek TCP, granice i limity; DTO oraz kontrakt wersji.
- [x] Serwer przyjmuje wielu klientów przez wykonawcę i osobne zadania; proste HELLO/PING.
- [x] Minimalne okno JavaFX łączy się w tle, pokazuje wynik i błąd bez zamrażania.
- [x] Kontrolowane zamykanie socketów i zadań, zwalnianie limitu.
- [x] Pierwsze testy fragmentacji ramek, korelacji żądań i równoczesnych połączeń.

Kryterium: dwa klienty komunikują się z jednym procesem serwera. Nie jest to jeszcze ukończona funkcjonalność aukcji.

## Etap 2 — konta i uprawnienia

- [x] Rejestracja, normalizacja loginów, bezpieczne hasła i konkurencja o ten sam login.
- [x] Logowanie, sesja, wylogowanie, wznowienie połączenia i wygasanie.
- [x] Jednorazowe inicjowanie administratora z konfiguracji, bez fabrycznego sekretu.
- [x] Uprawnienia USER/ADMIN, utworzenie konta przez admina, blokowanie i ochrona ostatniego admina.
- [x] Transakcyjny audyt kont, limity kosztownych operacji i prób logowania.

Kryterium: konta przeżywają restart, klient nie może nadać sobie uprawnień polem JSON, hasła/tokeny nie trafiają do logów ani odpowiedzi listujących konta.

## Etap 3 — aukcje i współbieżność

- [x] Model domenowy i reguły testowane z wstrzykiwanym zegarem.
- [x] Szkice, publikacja, lista, szczegóły, anulowanie i limity automatów.
- [x] Własny `Callable`/wątek wirtualny każdej nieterminalnej opublikowanej aukcji; `ReentrantLock`/`Condition` per aukcja.
- [x] Przyjmowanie ofert w transakcji, historia, wersje snapshotów, idempotencja.
- [x] Precyzyjna reguła +30 s do najwyżej minuty pozostałego czasu.
- [x] Rozstrzygnięcie końca, braku ofert i wyścigu oferta–zakończenie.
- [x] Powiadomienia wielu obserwatorów, kolejki o ograniczonym rozmiarze i wolny klient.
- [x] Testy dwóch aukcji, równoczesnych ofert i restartu z realnym PostgreSQL.

Kryterium: w dwóch aukcjach jednocześnie działa poprawna licytacja, jedna oferta ma jeden skutek, wynik i termin są spójne w pamięci i DB. To zasadniczy etap spełnienia wymagań prowadzącego.

## Etap 4 — pełne GUI

- [x] Logowanie/rejestracja, katalog startowy, szczegóły aukcji, historia ofert, stany połączenia.
- [x] Formularz szkicu/publikacji, moje aukcje, historia zakończonych.
- [x] Panel administratora, filtry i paginacja.
- [ ] Motyw PK według `UI_DESIGN.md`, polskie komunikaty, poprawne fokusy i skalowanie.
- [x] Zapis oczekującego polecenia i odzyskanie wyniku po reconnect; brak nowych ofert offline.
- [x] Brak operacji blokujących na wątku JavaFX i brak cofania widoku przez starą wersję.

Kryterium: kompletny przepływ możliwy przez GUI bez konsoli i bez ręcznej edycji DB.

## Etap 5 — diagnostyka, raport i odzyskiwanie

- [x] Logi klienta i serwera w DB, bezpieczne pola, paczki, deduplikacja i trwały ograniczony bufor.
- [x] Widok logów administratora; rozróżnienie deklaracji klienta i audytu serwera.
- [x] Raport `.txt` z utrwalonej historii, porcjowany transfer i zapis przez klienta.
- [x] Odtwarzanie automatów, rozstrzygnięcie przeterminowanych aukcji, pojedyncza instancja dla bazy.
- [x] Zachowanie przy awarii DB, nieznanym wyniku commit i kontrolowanym wyłączeniu.
- [x] Backup i sprawdzone odtworzenie do oddzielnej bazy testowej.

Kryterium: po awarii nie pojawia się przyjęta oferta, która zniknęła z DB, a brak odpowiedzi jest poprawnie uzgadniany. Raport zgadza się z rzeczywistym wynikiem.

## Etap 6 — odbiór i pomiary

- [ ] Wszystkie odpowiednie testy z `ACCEPTANCE.md`, z zapisanym wynikiem i warunkami.
- [x] Pokaz 3 klientów i 2 aukcji na laptopie.
- [x] Oddzielny mały test obciążenia; pomiar CPU, pamięci, czasu odpowiedzi, błędów, liczby zadań i połączeń DB.
- [ ] Przekroczenie limitów daje kontrolowany błąd; nie ma utraty spójności, wycieków i nieograniczonych kolejek.
- [x] Powtarzalne uruchomienie z czystej testowej bazy i z istniejącymi danymi.

Kryterium: opisz faktyczną pojemność przetestowanego profilu, nie „nieograniczoną liczbę użytkowników”. Większy limit jest ustawieniem, jego wydajność wymaga pomiaru.

## Etap 7 — dystrybucja i gotowość przeniesienia

- [x] Windows `.exe` z runtime i JavaFX, skrót, deinstalacja, katalog danych użytkownika.
- [ ] Uruchomienie pakietu bez zależności od globalnej Javy; weryfikacja na czystym środowisku lub drugim Windows.
- [x] Domyślny lokalny profil testowego instalatora, bez pól adres/port w GUI.
- [x] Publiczny profil TLS; test prawidłowego i nieprawidłowego certyfikatu z kontrolowanym testowym zaufaniem.
- [ ] Konfiguracja wdrożenia Linux/Compose, prywatna baza, przekazywanie sekretów, backup, opis migracji.
- [ ] Po wyborze hosta: rzeczywista domena/certyfikat w konfiguracji wydania i test z różnych sieci.

Ostatni punkt zależy od przyszłego wyboru wdrożenia. Nie blokuje implementacji lokalnej, ale bez niego R14 nie jest w pełni zweryfikowane. Nie oznaczaj publicznego wdrożenia jako zakończonego na podstawie localhost.

## Etap 8 — sprawozdanie i demonstracja

- [ ] Temat, prawdziwe nazwiska, zwięzła teoria powiązana z wykładami.
- [x] Opis rzeczywistej architektury i fragmentów kodu: executor, automat, synchronizacja, JDBC, TCP, JavaFX.
- [x] Rzeczywiste zrzuty, raport przykładowej aukcji, wyniki testów i pomiarów.
- [x] Wnioski, ograniczenia, instrukcja startu oraz scenariusz pokazu bez przypadkowej zależności od danych z poprzedniego uruchomienia.
- [x] Sprawdzenie zgodności finalnych dokumentów z kodem.

## Proponowany podział pracy zespołu

| Osoba | Obszar do zrozumienia, sprawdzenia i przedstawienia |
|---|---|
| A | Domena, automat aukcji, współbieżność, testy wyścigów. |
| B | TCP/protokół, PostgreSQL, konta, logi i odtwarzanie. |
| C | JavaFX, identyfikacja wizualna, instalator, pokaz i sprawozdanie. |

To podział odpowiedzialności studentów; nie polecenie uruchamiania subagentów. Codex może tworzyć kod, ale każdy autor powinien umieć wyjaśnić swoją część i główną regułę synchronizacji.

## Rejestr weryfikacji

Wykonane próby zapisano w [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md). Otwarte punkty dotyczą m.in. pełnej kombinacji awarii, wszystkich prób klawiatury/fokusu, drugiego Windows, Linux/publicznego wdrożenia oraz nazwisk autorów. Niepewny wynik commit JDBC sprawdzono przez zatrzymanie potwierdzenia z rzeczywistego PostgreSQL. Kod i podstawowe scenariusze lokalne działają; sam fakt istnienia skryptu nie zalicza niewykonanej próby.

