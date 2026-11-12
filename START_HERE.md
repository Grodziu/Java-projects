# BidRoom — pakiet startowy dla Codexa

Stan przekazania: **12 listopada 2026, wersja 1.1.1**. Marcin przejmuje wdrożenie i dalsze modyfikacje; Oliwia przygotowuje sprawozdanie końcowe. Zacznij od [README](README.md) i [HANDOFF](docs/HANDOFF.md). Autorzy: Piotr Grodny, Marcin Gurdziel, Oliwia Jarosz, rok 2026/2027. Implementacja rozpoczęta **10 listopada 2026**: kod, środowisko, testy, klient JavaFX i lokalny instalator już istnieją. Aktualne polecenia są w [RUNNING.md](docs/RUNNING.md), a wykonane sprawdzenia w [VALIDATION_RESULTS.md](docs/VALIDATION_RESULTS.md). Dalsza część zachowuje kontekst początkowej specyfikacji z 9 listopada; historyczne sformułowania „do wykonania” nie zastępują aktualnej checklisty.

## Zadanie dla następnego Codexa

Zbuduj kompletny projekt zaliczeniowy BidRoom zgodnie z dokumentami poniżej. Najpierw przygotuj możliwie lekkie środowisko na obecnym laptopie Windows, następnie implementuj kolejne etapy, sprawdzając działanie. Doprowadź projekt do działającej aplikacji klient–serwer, trwałego zapisu w PostgreSQL, testów współbieżności, klienta JavaFX oraz instalatora Windows. Pisz i wyjaśniaj po polsku. Bieżące decyzje implementacyjne podejmuj samodzielnie w granicach specyfikacji. Nie pytaj ponownie o rozstrzygnięcia już zapisane.

Pakiet pierwotnie powstał jako przygotowanie informacji. Następne polecenie użytkownika uruchomiło implementację; obecnie należy korzystać z istniejącego kodu i instrukcji uruchomienia.

## Przeczytaj w tej kolejności

1. [AGENTS.md](AGENTS.md) — zasady pracy i ograniczenia projektu.
2. [Wymagania i reguły biznesowe](docs/REQUIREMENTS.md).
3. [Rejestr ustaleń i domyślnych decyzji](docs/DECISIONS.md).
4. [Środowisko na laptopie i późniejsze wdrożenie](docs/ENVIRONMENT.md).
5. [Architektura, wątki i synchronizacja](docs/ARCHITECTURE.md).
6. [Kontrakt komunikacji](docs/PROTOCOL.md) oraz [model danych](docs/DATABASE.md).
7. [Interfejs i identyfikacja wizualna](docs/UI_DESIGN.md).
8. [Etapy implementacji](docs/IMPLEMENTATION_PLAN.md) oraz [kryteria odbioru](docs/ACCEPTANCE.md).
9. [Materiały prowadzącego i źródła](docs/REFERENCES.md).

## Najważniejsze ustalenia

- Projekt trzyosobowy na zajęcia dr. inż. Sławomira Bąka.
- Java 21, programowanie obiektowe, JavaFX 21, TCP/IP, PostgreSQL po stronie serwera.
- Wielowątkowość przez `java.util.concurrent`: `ExecutorService`, `Callable`, `Future`, blokady i warunki. Zadanie każdego klienta oraz automat każdej opublikowanej, niezakończonej aukcji otrzymują osobne wątki. Proponowany wykonawca: `Executors.newVirtualThreadPerTaskExecutor()`.
- Wiele aukcji i klientów równocześnie; limity konfigurowalne, dostosowane do sprzętu.
- Samodzielna rejestracja. Użytkownik wystawia i licytuje cudze aukcje. Administrator ma dodatkowe uprawnienia.
- Wirtualne punkty, bez płatności, portfela i dostawy towarów.
- Gdy poprawna oferta zostaje przyjęta przy `0 < czasPozostały < 60 s`, nowy pozostały czas wynosi `min(czasPozostały + 30 s, 60 s)`.
- Logi serwera i klienta trafiają do bazy przez serwer. Raport `.txt` jest osobnym wymaganiem.
- Klient Windows pobierany z instalatora z dołączoną Javą/JavaFX. Brak wpisywania adresu i portu w normalnym interfejsie.
- Docelowo jeden serwer dostępny przez Internet, połączenie TCP z TLS, stała domena w konfiguracji. Nie wymagać od uczestników wspólnego hotspotu, Dockera ani VPN.
- Rozwój i odbiór lokalny odbywają się na laptopie. Po poleceniu z 11.11.2026 wybrano VPS Linux 2 vCPU / 4 GB RAM dla demonstracji internetowej. Pakiet i instrukcje są przygotowane; usługa nie została kupiona ani wdrożona publicznie. Szczegóły: [DEPLOYMENT.md](docs/DEPLOYMENT.md).
- Docker przede wszystkim dla PostgreSQL i późniejszego wdrożenia. W codziennej pracy uruchamiaj serwer Java natywnie na Windows, żeby ograniczyć narzut.
- Motyw wizualny inspirowany dostarczonymi grafikami Politechniki Krakowskiej; interfejs po polsku.

## Pierwsza sesja implementacyjna

1. Wykonaj ponowny krótki przegląd narzędzi. Zapoznaj się z ograniczeniami pamięci w `docs/ENVIRONMENT.md`.
2. Przygotuj JDK 21 i Maven Wrapper. Nie opieraj projektu na obecnym skrócie do Java 8.
3. Skonfiguruj PostgreSQL w Dockerze, jeśli środowisko go obsługuje. Jeśli Docker wymaga interakcji/restartu, wykonaj niezależne prace nad kodem i opisz dokładnie pozostały krok.
4. Utwórz moduły `common`, `server`, `client`, `loadtest`.
5. Najpierw uruchom przepływ TCP klient → serwer → odpowiedź, a następnie rejestrację i logowanie z bazą. Nie zaczynaj od rozbudowanej makiety bez działającego serwera.
6. Realizuj [plan](docs/IMPLEMENTATION_PLAN.md) dalej, bez traktowania samego szkieletu jako ukończonego projektu.

## Co jest, a czego jeszcze nie ma

Gotowe: specyfikacja i materiały źródłowe, moduły aplikacji, migracja SQL, testy automatyczne, skrypty lokalnego środowiska, pomiar obciążenia, pokaz JavaFX oraz budowanie instalatora. Szczegółowy aktualny stan i warunki testów znajdują się w rejestrze weryfikacji.

Osobnym przyszłym etapem pozostaje rzeczywiste wdrożenie publiczne przez Marcina. Ograniczeń odbioru na drugim/czystym Windows nie wolno zastępować fikcyjnymi potwierdzeniami.

Hosting i domena nie blokują lokalnego kodowania. Nazwiska autorów są podane powyżej. Nie przedstawiaj lokalnego testu jako publicznego wdrożenia. Zgodnie z poleceniem 12.11.2026 nie kontynuuj pisania sprawozdania — to zadanie Oliwii.
