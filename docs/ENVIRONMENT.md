# Środowisko na laptopie i późniejsze przeniesienie

## 1. Stan rozpoznany 9 listopada 2026

| Element | Odczyt / ograniczenie wiedzy |
|---|---|
| System | Windows x64, wersja raportowana przez system `10.0.26300`. |
| Procesor | Intel Core Ultra 5 125H, 18 procesorów logicznych. |
| Pamięć | Około 16 GB zainstalowanej/15 GB widocznej; w pojedynczej próbce około 1 GB wolnej. To chwilowy odczyt, nie pomiar obciążenia projektu. |
| `java` na PATH | Ścieżka Oracle `java8path`; nie jest to potwierdzenie gotowego JDK 21. Próba odczytu wersji nie zakończyła się i została przerwana. |
| `javac`, `mvn`, `docker` | Nie znaleziono na PATH. Nie przesądza to o braku wszystkich instalacji poza PATH. |
| Git | Znaleziony plik wykonywalny; wersji nie potwierdzono. |
| WSL | Jest plik `wsl.exe`; nie potwierdzono działającej dystrybucji, WSL2 ani wirtualizacji. |
| Projekt | Bieżący folder w OneDrive; początkowo bez kodu i repozytorium Git. |

To historyczny odczyt sprzed implementacji. Narzędzia i bazę przygotowano 10–11 listopada 2026 według następnej sekcji. Przy kolejnej sesji nadal należy sprawdzić aktualną wolną pamięć i ograniczać czas poleceń diagnostycznych.

## 2. Wdrożony układ lokalny

Aktualizacja implementacyjna: przygotowano prywatny JDK 21, Maven Wrapper i natywny PostgreSQL 17.11. Wybrano dopuszczony wariant bez Dockera, aby ograniczyć zużycie pamięci. Odczyt przed pracą ponownie wykazał około 1,2 GB wolnego RAM. Dane PostgreSQL są poza OneDrive, w `%USERPROFILE%/BidRoomData/postgres17-data`; `shared_buffers=32MB`. Rzeczywiste polecenia: [RUNNING.md](RUNNING.md).

```text
Laptop Windows
  ├─ źródła + Maven Wrapper
  ├─ serwer Java uruchomiony natywnie: 127.0.0.1:5001
  ├─ 1–3 klientów JavaFX z lokalnym profilem
  └─ PostgreSQL 17.11 uruchomiony natywnie: 127.0.0.1:55432
       ├─ bidroom_dev
       └─ bidroom_test
```

Nie jest potrzebny drugi komputer do pisania kodu i większości testów. Trzy osobne procesy klienta na laptopie pokażą współbieżność. Trzy laptopy i różne sieci wykorzystamy później do testu wdrożenia publicznego. Sama instalacja Dockera nie udostępnia aplikacji przez Internet.

Serwer Java i lokalny PostgreSQL działają natywnie. Alternatywny Compose jest przygotowany, ale nie był uruchamiany na tym laptopie. Docker pozostaje narzędziem autora/administratora; użytkownik końcowy dostaje instalator klienta.

## 3. Przygotowanie narzędzi

1. Sprawdź wolną pamięć, miejsce na dysku, dostępny JDK i Docker z timeoutami. Niczego nie zamykaj za użytkownika poza własnymi procesami testowymi.
2. Przygotuj JDK 21 x64. Preferuj jawne wskazanie JDK w skryptach projektu; nie zmieniaj bez potrzeby globalnego PATH i nie usuwaj starej Javy używanej przez inne programy.
3. Dodaj Maven Wrapper z przypiętą stabilną wersją Maven i kontrolą integralności. Nie wymagaj globalnego Maven. `mvnw.cmd` musi działać w ścieżce ze spacjami i polskimi znakami.
4. Sprawdź Docker Desktop/WSL2. Jeżeli instalacja potrzebuje restartu lub interakcji użytkownika, opisz konkretny krok i kontynuuj niezależny kod. Nie oznaczaj testów PostgreSQL jako wykonanych.
5. Uruchom jedną instancję PostgreSQL, z healthcheckiem, nazwanym wolumenem i portem związanym wyłącznie z `127.0.0.1`.
6. Jeżeli Docker okaże się niewykonalny lub nadmiernie obciąża laptop, dopuszczalny jest lokalny PostgreSQL bez Dockera, z tym samym schematem i protokołem. Nie zastępuj go SQLite/H2. Odnotuj wybór i instrukcję.
7. Narzędzie do budowy instalatora Windows dobierz dopiero na etapie pakowania: `jpackage` z JDK 21 oraz wersja WiX zgodna z wybranym buildem JDK. JavaFX i potrzebne moduły JDK muszą znaleźć się w runtime aplikacji.

Zależności pobieraj z oficjalnych repozytoriów, przypinaj konkretne poprawki. JavaFX 21 musi odpowiadać platformie Windows x64. Nie zakładaj, że systemowa Java 8 uruchomi klienta.

## 4. Oszczędny profil zasobów

Poniżej ustawienia użyte w wersji lokalnej 1.0.0. Heap JVM nie obejmuje całego procesu; grafika ma własny narzut. Zmierzone wartości znajdują się w `VALIDATION_RESULTS.md`.

| Element | Profil lokalny |
|---|---|
| Budowanie Maven | Sekwencyjnie, bez `-T`; maks. heap 384 MiB. |
| Serwer | `-Xms64m -Xmx256m`; zwiększ dopiero przy uzasadnionym pomiarze. |
| Klient | `-Xms32m -Xmx192m`; do 256 MiB, jeśli JavaFX wymaga tego w rzeczywistym teście. |
| PostgreSQL | Proces natywny, `shared_buffers=32MB`, `max_connections=20`. Alternatywny Compose ma limit kontenera 512 MiB; nie był uruchamiany. |
| Pula aplikacji | Maks. 4 połączenia, minimum bezczynnych 0–1; dodatkowo 1 dedykowane połączenie blokady instancji. |
| Klienci / aukcje | Limit 32 połączenia i 16 opublikowanych niezakończonych aukcji. |
| Zwykła praca | 1 klient; dodatkowe procesy tylko na czas sprawdzenia. |
| Test obciążenia | Domyślnie osobno 10 klientów bez GUI, 3 aukcje, 30 s; większy profil na żądanie. |

Przy około 1 GB wolnego RAM nie zakładaj, że uruchomienie wszystkiego będzie płynne. Sprawdź pamięć przed startem, unikaj równoległego builda i pokazu trzech GUI. Nie zmieniaj globalnego pliku `.wslconfig` automatycznie; jego limit wpływa też na inne projekty. Ewentualny limit całego WSL rzędu 3 GB wymaga uwzględnienia pozostałych usług użytkownika.

Ogranicz kolejki, rozmiary ramek, liczbę połączeń i równoległe haszowanie. Wątki wirtualne zmniejszają koszt oczekiwania, ale nie tworzą dodatkowej mocy CPU ani nieskończonej pamięci. Po testach zatrzymaj własne zbędne procesy; zachowaj dane bazy.

## 5. Konfiguracja

`ServerConfig` odczytuje i waliduje konfigurację przy starcie; sekrety znajdują się osobno od repozytorium. Zmienne wersji 1.0.0:

| Zmienna | Profil lokalny | Docelowe wdrożenie |
|---|---|---|
| `BIDROOM_PROFILE` | `local` | `production` |
| `BIDROOM_BIND_HOST` | `127.0.0.1` | Interfejs serwera/kontenera zgodny z wdrożeniem. |
| `BIDROOM_PORT` | `5001` | Np. `5001` wewnątrz, port publiczny konfigurowalny. |
| `BIDROOM_DB_URL` | `jdbc:postgresql://127.0.0.1:55432/bidroom_dev` | Np. `jdbc:postgresql://postgres:5432/bidroom` w prywatnej sieci Compose. |
| `BIDROOM_DB_USER` | `bidroom` | Dedykowany użytkownik DB. |
| `BIDROOM_DB_PASSWORD` | Lokalny sekret poza repozytorium. | Sekret administratora wdrożenia. |
| `BIDROOM_DB_POOL_SIZE` | `4` | Według pomiaru. |
| `BIDROOM_MAX_CLIENTS` | `32` | Według pamięci, CPU i puli. |
| `BIDROOM_MAX_LIVE_AUCTIONS` | `16` | Według pomiaru; obejmuje SCHEDULED i ACTIVE. |
| `BIDROOM_TLS_REQUIRED` | `false` tylko przy loopback | `true`, wymuszone przez profil. |
| `BIDROOM_TLS_KEYSTORE` / `BIDROOM_TLS_KEYSTORE_PASSWORD` | Nieużywane bez TLS. | Certyfikat/klucz i sekret po stronie serwera. |
| `BIDROOM_BOOTSTRAP_ADMIN_LOGIN` / `BIDROOM_BOOTSTRAP_ADMIN_PASSWORD` | Jednorazowe utworzenie, jeśli admin nie istnieje. | Sekret wdrożenia, bez nadpisywania hasła przy każdym starcie. |
| `BIDROOM_COMMAND_HMAC_SECRET` | Trwały lokalny sekret dla bezpiecznego porównania poleceń zawierających hasło. | Zachowywany przy migracji serwera. |
| `BIDROOM_DATA_DIR` | Skrypty ustawiają `%USERPROFILE%/BidRoomData/server`. | Prywatny, zapisywalny katalog serwera poza katalogiem programu. |

Nie wystawiaj PostgreSQL w publicznym Internecie. Dla Dockera lokalnego port mapuj jako `127.0.0.1:55432:5432`, nie na wszystkie interfejsy. Baza w profilu produkcyjnym nie potrzebuje publikowania portu na hoście.

Klient ma osobną niepoufną konfigurację `serverHost`, `serverPort`, `tls`, `protocolVersion` w profilu pakowania. Profil developerski wskazuje localhost automatycznie. Instalator publiczny ma wskazywać prawdziwą domenę i TLS; bez wybranego hosta wolno zbudować i przetestować instalator lokalny, nie wolno twierdzić, że jest gotowym publicznym wdrożeniem. Nie ukrywaj brakującego serwera przez wymyślony adres.

Nie ma pól host/port na ekranie użytkownika. Ekspercki override do testów może istnieć w pliku lub parametrze startowym. Żadne ustawienie klienta nie może nadać roli ADMIN ani wyłączyć wymaganej weryfikacji TLS w wydaniu publicznym.

## 6. Istniejące skrypty

Skrypty poniżej są częścią repozytorium. Sprawdzone polecenia i ich warunki opisuje [RUNNING.md](RUNNING.md). `BidRoom.cmd` uruchamia lokalną bazę, serwer i klienta, a `Stop-BidRoom.cmd` zatrzymuje własne usługi.

| Plik | Działanie |
|---|---|
| `scripts/check-env.ps1` | Krótka diagnoza JDK, pamięci, Dockera i portów, bez modyfikowania systemu. |
| `scripts/setup-tools.ps1` | Prywatny JDK 21 i Maven poza OneDrive, bez zmiany globalnego PATH. |
| `scripts/start-db.ps1` | Uruchomienie wyłącznie projektowej bazy, oczekiwanie na healthcheck. |
| `scripts/start-server.ps1` | Jawny lokalny profil i limity JVM; informacja o gotowości. |
| `scripts/start-client.ps1` | Jeden klient z lokalną konfiguracją i osobnym katalogiem sesji testowej. |
| `scripts/test.ps1` | Rozdzielenie testów jednostkowych i integracyjnych; czytelne niezaliczone/brak warunków. |
| `scripts/test-gui.ps1` / `test-layout.ps1` | Rzeczywiste JavaFX, przepływ aukcji i układ przy skalowaniu 100/125/150%. |
| `scripts/test-recovery.ps1` | Kontrolowane zatrzymanie własnej bazy i sprawdzenie powrotu serwera. |
| `scripts/demo.ps1` / `load-test.ps1` | Trzy procesy GUI oraz osobny, niewielki pomiar bez GUI. |
| `scripts/build-installer.ps1` | Powtarzalne pakowanie Windows, weryfikacja profilu i dołączonego runtime. |
| `scripts/backup-db.ps1` / `restore-test-db.ps1` | Kopia i odtworzenie do jawnie testowej bazy, bez nadpisywania developerskiej. |
| `compose.yaml` | Alternatywny lokalny PostgreSQL; konfiguracja nieuruchomiona na tym laptopie. |

Sprawdzono skrypty w PowerShell 7 oraz zwykły start i zatrzymanie w Windows PowerShell 5.1. Pliki PS1 mają UTF-8 z BOM. Launchery ustawiają `RemoteSigned` wyłącznie w swoim procesie; nie zmieniają globalnej konfiguracji. Nie kończą obcego procesu nasłuchującego na porcie.

## 7. OneDrive, konfiguracja i Git

Źródła pozostają w obecnym folderze. `.gitignore` może wykluczyć `target`, logi, hasła i pliki lokalne z Gita, ale **nie wyłącza synchronizacji OneDrive**. Dlatego aktywne pliki DB, runtime logów klienta i sekrety trzymaj poza synchronizowanym katalogiem, np. w właściwych katalogach aplikacji profilu użytkownika. Nie przenoś projektu samodzielnie. Oryginalne materiały prowadzącego nie są modyfikowane.

Dodanie kopii PDF/grafik do Git nie jest automatycznym upoważnieniem do ich publicznego publikowania; to materiały lokalne projektu. Repozytorium można przygotować prywatnie. Nie publikuj ani nie wysyłaj niczego bez polecenia.

## 8. Instalator i przejście do Internetu

Instalator Windows `.exe` zawiera aplikację, JavaFX i runtime Javy. Użytkownik: pobiera → instaluje → uruchamia → rejestruje/loguje się → wybiera aukcję. Preferuj instalację dla bieżącego użytkownika, skrót i deinstalację; dane robocze w `%LOCALAPPDATA%/BidRoom`, nie w katalogu programu. Podpisanie instalatora jest oddzielnym, niewybranym krokiem; nie instruuj automatycznego wyłączania zabezpieczeń Windows.

Po ukończeniu wersji lokalnej można przenieść serwer do Linux/VPS lub własnego urządzenia. Wymagane będą: publiczny dostęp TCP, DNS, certyfikat TLS, dostęp administracyjny, backup i reguła zapory dla portu aplikacji. Zwykły hosting wyłącznie stron WWW może nie obsłużyć naszego surowego TCP; port 443 nie zamienia go w HTTP.

Przy hostowaniu z domu trzeba ustalić publiczny adres i przekierowanie portu; CGNAT może je uniemożliwić. Nie narzucaj uczestnikom VPN ani hotspotu. Laptop za domowym routerem nie staje się publicznym serwerem przez uruchomienie Compose. Konkretną drogę udostępnienia wybierz dopiero przy wdrożeniu, bez kupowania czegokolwiek teraz.

Migracja: build serwera → migracje/restore DB → konfiguracja sekretów/TLS → sprawdzenie gotowości → klient z docelowym DNS → testy z co najmniej dwóch niezależnych sieci. Zmiana miejsca hostowania nie powinna wymagać zmiany protokołu ani modelu aukcji.


