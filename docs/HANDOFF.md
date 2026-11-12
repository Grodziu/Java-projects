# Przekazanie BidRoom Marcinowi — 12.11.2026

To skrót ustaleń i punkt startu do dalszej pracy. **Nie potrzeba całej rozmowy z asystentem**: zawierała prywatne hasła i ścieżki. Wymagania, decyzje, protokół i wyniki są zapisane w repozytorium. Najnowsze polecenia zespołu mają pierwszeństwo przed historycznymi planami.

## Co przejmujesz

Wersja 1.1.1: Java 21, JavaFX, TCP z ramkowanym JSON (publicznie obowiązkowy TLS), PostgreSQL 17, JDBC i Flyway. Jeden serwer i jedna baza. Bez klienta WWW, RMI, ORM, mikroserwisów ani połączeń klientów do bazy.

Każdy klient ma własne zadanie `Callable` i wątek wirtualny; każda opublikowana niezakończona aukcja ma własny automat i wątek. Koordynacja przez `ExecutorService`, `ReentrantLock`, `Condition`, semafory i kolejki. Wątki wirtualne wymagają Java 21 i spełniają założenie współczesnego `java.util.concurrent`, rozwijanego od Java 1.5. Nie zastępuj tego ręcznym `new Thread().start()`.

Najważniejsze reguły: tylko cudze aukcje; punkty całkowite; pierwsza oferta może być równa cenie startowej; kolejne co najmniej cena + przebicie. Jeśli pozostało mniej niż 60 s, poprawna oferta dodaje 30 s, maksymalnie do 60 s pozostałego czasu. Decyduje zegar serwera. Oferta, termin, audyt i wynik requestId zatwierdzane są razem w PostgreSQL. Ponowienie tego samego polecenia nie tworzy drugiej oferty.

Zdjęcie JPG/PNG jest wymagane przed publikacją nowej aukcji; trafia porcjami TCP do `auction_photo` w PostgreSQL. Raport TXT generuje się sam po zakończeniu/anulowaniu, osobno dla każdej aukcji; jest też rejestr zbiorczy i zdarzenie w logach. ADMIN ma logi, konta i panel wątków/CPU/pamięci/JDBC. Dawne aukcje Piotra bez zdjęć mają jawnie opisaną ilustrację kategorii.

## Pliki i paczki

| Element | Do czego służy |
|---|---|
| Repozytorium / źródłowy ZIP | Dalsze programowanie, IntelliJ, testy, zasoby i instrukcje |
| `BidRoom-1.1.1.exe` z lokalnego wydania | Instalator klienta z Javą/JavaFX, adres `127.0.0.1:5001`; test na komputerze z uruchomionym lokalnym serwerem |
| `BidRoom-serwer-1.1.1.zip` | Skompilowany serwer, pliki Docker/VPS, instrukcja i schemat; potrzebuje PostgreSQL i konfiguracji wygenerowanej na serwerze |
| `BidRoom-przekazanie-1.1.1.zip` | Alternatywny komplet do przekazania: źródła, lokalny instalator i pakiet serwera; bez istniejących kont i sekretów |
| `docs/INTELLIJ.md` | Import Maven, uruchamianie i miejsca w kodzie do pokazania prowadzącemu |
| `docs/POSTGRESQL.md` | Gdzie są dane, pgAdmin/psql, diagram, przykładowe zapytania, backup |
| `docs/VALIDATION_RESULTS.md` | Wyniki rzeczywiście wykonanych prób i ich ograniczenia |

Nie wysyłamy samego `BidRoom.exe` wyjętego z zainstalowanego katalogu — potrzebuje katalogów runtime/app. Wysyłamy instalator. Nie kopiujemy aktywnego katalogu PostgreSQL, sesji klientów ani pliku sekretów do GitHuba.

## Pierwszy odbiór na Twoim komputerze

1. Wykonaj kolejność z [NAJPIERW-PRZECZYTAJ](../NAJPIERW-PRZECZYTAJ.txt). Nie musisz instalować Dockera na Windows. Narzędzia i dane powstaną w Twoim `%USERPROFILE%/BidRoomData`.
2. Po starcie serwera zarejestruj swoje konto i osobne konto testowego wystawiającego. Nie istnieją wspólne fabryczne hasła Piotra/Marcina/Oliwii. Losowy login/sekret administratora są w prywatnej konfiguracji wygenerowanej **u Ciebie**.
3. Wystaw aukcję ze zdjęciem. Inny użytkownik ma ją zobaczyć i licytować. Rzeczywistą wspólną licytację trzech klientów można uruchomić jawnie przez `scripts/demo.ps1`; wymaga więcej pamięci.
4. Obejrzyj panel ADMIN, bazę oraz automatyczne raporty. Testy integracyjne: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/test.ps1 -Integration`. Używają osobnej, czyszczonej bazy `bidroom_test`, nigdy `bidroom_dev`.
5. Uruchom projekt w IntelliJ według osobnej instrukcji. Deweloperski klient i instalator korzystają z tego samego protokołu v2; wersje obu stron muszą być zgodne.

## Wdrożenie — Twoje zadanie

**Nie ma wymogu wspólnego hotspotu/LAN.** TCP/IP działa przez Internet, o ile klient może osiągnąć serwer, a certyfikat TLS pasuje do jego hosta. Wspólny hotspot może pomóc przy lokalnej demonstracji, ale sam nie zapewnia dostępu z różnych sieci. Profil `local` celowo nasłuchuje tylko na loopback; zmiana samego IP bez TLS nie wystarczy.

Wybrany kierunek: **VPS Linux x86-64, około 2 vCPU / 4 GB RAM**, laptop do rozwijania kodu. To założenie startowe do sprawdzenia pomiarem, nie gwarancja pojemności. VPS daje stałą dostępność po wyłączeniu laptopa i upraszcza sieć. Zakup oraz dane dostępu ustala zespół — żadna usługa nie została kupiona.

Kolejność:

1. Zapewnij VPS i publiczną nazwę DNS wskazującą serwer. Otwórz tylko niezbędne porty opisane w [DEPLOYMENT](DEPLOYMENT.md). PostgreSQL zostaje prywatny.
2. Wgraj pakiet serwera, zainstaluj narzędzia Linux, uruchom `prepare-vps.sh`. Uzyskaj certyfikat i poczekaj na `BIDROOM_READY`.
3. Rozpocznij od nowej bazy z własnymi kontami. Jeśli chcecie zachować dane Piotra, uzgodnijcie osobny prywatny transfer dumpa i trwałego HMAC; procedura migracji jest w DEPLOYMENT. GitHub nie przenosi istniejących rekordów.
4. Zbuduj klienta poleceniem `scripts/build-installer.ps1 -Production -ServerHost RZECZYWISTY_HOST`. Powstanie instalator w `dist/internet/`. Zapisz host, wersję, datę i SHA256. Przetestuj go przed przekazaniem zespołowi.
5. Piotr i drugi uczestnik łączą się **z różnych sieci**, np. Wi-Fi i Internet mobilny. Używają różnych kont i tej samej aukcji. Sprawdź aktualizację oferty/czasu, jednoczesne podbicie, odrzucenie za małej oferty, utratę sieci, reconnect, koniec i automatyczny raport. Sprawdź też zdjęcie na drugim komputerze.
6. Zrestartuj serwer podczas osobnej testowej aukcji i sprawdź odtworzenie terminu/ofert. Odbierz instalator na drugim/czystym Windows bez JDK/PostgreSQL. Zapisz wyniki, wersje i zrzuty w dokumentacji.
7. Sprawdź backup i odtworzenie do osobnej bazy oraz odnowienie certyfikatu. Dopiero po tych próbach oznacz odpowiednie punkty w `IMPLEMENTATION_PLAN.md`.

Kod lokalny i testy TCP/TLS już istnieją. **Nie wykonano jeszcze rzeczywistego wdrożenia Linux/VPS, publicznego certyfikatu ani próby między dwiema sieciami.** Tych punktów nie należy przedstawiać prowadzącemu jako zaliczonych na podstawie samych plików wdrożenia.

## Dalsze modyfikacje i pokaz

Pracuj na gałęzi od `main`, po `git pull`. Nie edytuj istniejących migracji V001–V003 w bazie, która je wykonała; kolejne zmiany schematu dostają nowy plik V004 itd. Po zmianie protokołu aktualizuj obie strony i `PROTOCOL.md`. Buduj sekwencyjnie bez Maven `-T`. Test obciążenia jest osobną świadomą czynnością na własnej bazie testowej.

Publiczny link pozwala pobrać kod, ale nie przyznaje zapisu. Marcin może zrobić **Fork → własna gałąź → Pull Request**. Jeśli chcecie pracować bezpośrednio w jednym repozytorium, Piotr dodaje konto GitHub Marcina w **Settings → Collaborators** z uprawnieniem zapisu. W tej sesji nie wysyłano zaproszeń ani wiadomości do członków zespołu.

Nowe wydanie instalatora musi mieć wyższy numer wersji. Windows może odmówić nadpisania innym buildem o identycznej wersji (kod 1638). Nie zmieniaj numeru już opublikowanego wydania bez podniesienia wersji w POM, metadanych HELLO oraz skryptach pakowania/uruchamiania. Lokalne robocze wyniki `target/` czyści `build-project.ps1`, aby nie pozostawały stare JAR-y w `lib/`.

Oliwia przygotowuje końcowe sprawozdanie po odbiorze. Otrzymuje kod, aktualne zrzuty oraz faktyczne wyniki testów. Historyczny roboczy PDF pozostaje u Piotra; nie jest publikowany jako ukończone sprawozdanie. Autorzy i rok 2026/2027 są już ustaleni, nie trzeba pytać o nie ponownie.
