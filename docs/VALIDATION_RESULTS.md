# BidRoom — wykonana weryfikacja

## Przekazanie 1.1.1 — 12.11.2026

- Pełne `scripts/test.ps1 -Integration`: **36 testów, 0 błędów, 0 niepowodzeń, 0 pominiętych** na PostgreSQL 17.11. Doszły dwa testy jawnego wczytywania prywatnej konfiguracji do IntelliJ, pierwszeństwa zmiennych środowiska i nieujawniania treści błędu parsera. [Wyniki](validation/tests-summary-1.1.1.json).
- Rzeczywisty scenariusz JavaFX: **GUI_SMOKE_PASSED** — logowanie, zdjęcie z serwera, licytacja i dwuklik, reconnect, zakończenie, raport, tworzenie/publikacja, ADMIN i logi. [Aktualne zrzuty](screenshots-1.1.1/01-logowanie.png) oraz [panel serwera](screenshots-1.1.1/09-panel-serwera.png). Konta testowe są syntetyczne.
- `scripts/test-layout.ps1`: **3 × GUI_SMOKE_PASSED**, skale 100/125/150% przy docelowym ekranie 1366×768. Asercje sprawdzają brak wycinania i zmian proporcji grafiki, dopasowanie szerokości treści, jednakowe szerokości menu oraz wyrównanie statusu i Ponów. Przy małej wysokości okna przewijanie pionowe jest celowe. Testy GUI mają teraz własną, jednorazową bazę, automatycznie usuwaną po zakończeniu własnego serwera.
- `launch.ps1 -Action Server` uruchamia bazę i serwer **bez okna klienta**. Powtórzenie zachowuje ten sam proces. `assert-server.ps1` sprawdza odpowiedź HELLO protokołu v2. Próba na nieuruchomionym porcie kończy się instrukcją uruchomienia serwera, zamiast otwierać klienta.
- Po zmianie samych metadanych numeru wersji HELLO wykonano ponowny czysty build i testy jednostkowe, następnie zbudowano końcowe pakiety. Czysty build usuwa stare wersje JAR z `target/lib`. Poprawiono też `.dockerignore`, aby aktualny JAR serwera trafiał do kontekstu wdrożenia.
- Instalator 1.1.1 zawiera runtime i pełne grafiki PK. Test końcowej lokalnej instalacji oraz SHA256: [installer-1.1.1.json](validation/installer-1.1.1.json). Windows odrzucił próbę nadpisania roboczego buildu identycznym numerem (1638); własną pośrednią instalację zastąpiono końcową. Nie zmieniano danych PostgreSQL ani kont. To nadal nie jest odbiór na drugim/czystym Windows.
- Składnia wszystkich skryptów PowerShell i czterech `deploy/*.sh` sprawdzona. Konfiguracje `.run` służą do importu w IntelliJ; w tej sesji nie wykonano interaktywnego pokazu w samym IntelliJ ani wdrożenia Docker/Linux.
- Przed publikacją przeprowadzono porównanie plików z lokalnymi sekretami oraz kontrolę wykluczeń: bez bazy, dumpów, haseł, sesji i surowej rozmowy. Historyczne wykłady, pierwotny manifest z prywatnymi ścieżkami oraz szkic sprawozdania pozostają poza publicznym repozytorium. Bieżące grafiki i wszystkie zasoby potrzebne do buildu są dołączone.
- Zbudowano komplet przekazania. Archiwum źródeł z commita rozpakowano do nowego podkatalogu bez `target/` i wykonano `mvnw.cmd package -DskipTests`: **BUILD SUCCESS**. To dodatkowy sprawdzian samodzielności źródeł, nie kolejny przebieg testów. [Dowód](validation/handoff-1.1.1.json). Późniejszy commit dokumentuje ten wynik i uprawnienia wykonywania plików Linux, bez zmian kodu aplikacji.

**Dalszy odbiór Marcina:** rzeczywisty VPS, publiczny host/certyfikat, docelowy instalator i licytacja z różnych sieci. **Sprawozdanie końcowe: Oliwia.** Historyczne wyniki poniżej opisują wcześniejsze wersje; wcześniejsze zrzuty pozostają lokalnie, nie wszystkie są publikowane.

Publikacja zakończona: [repozytorium](https://github.com/Grodziu/Java-projects) i [wydanie v1.1.1](https://github.com/Grodziu/Java-projects/releases/tag/v1.1.1). Pięć zasobów wydania (instalator, serwer, źródła, komplet przekazania i sumy) sprawdzono także przez publiczny odczyt API. SHA256 odpowiadają lokalnym plikom. [Zapis publikacji](validation/github-release-1.1.1.json). Tag i ZIP-y zawierają zweryfikowany commit kodu; późniejszy commit na main jedynie dokumentuje publikację i doprecyzowuje rozpakowanie paczki.

## Archiwum weryfikacji 1.0 i 1.1


Stan opracowania: **11 listopada 2026**. Źródła powstały w bieżącym folderze projektu. Testy wykonano na Windows x64 z rzeczywistym PostgreSQL 17.11. Ten dokument opisuje odbiór lokalny; nie deklaruje wdrożenia publicznego ani pełnego zamknięcia wszystkich punktów ACCEPTANCE.md.

## Środowisko

- Intel Core Ultra 5 125H, 18 procesorów logicznych, 15,4 GiB widocznego RAM. Przed pracą około 1,2 GiB wolnego RAM; sprawdzano stan ponownie po wznowieniu.
- Prywatny Temurin JDK 21.0.13, JavaFX 21.0.13, Maven 3.9.16, Wrapper 3.3.4. Wersje bibliotek przypięte w POM.
- PostgreSQL 17.11 natywnie, port 127.0.0.1:55432, osobne dev/test, aplikacyjna rola bez superuser. Katalog danych i sekrety w `%USERPROFILE%/BidRoomData`, poza OneDrive. Początkowe testy działały w prywatnym AppData; później przeniesiono narzędzia i zatrzymany klaster do zwykłego profilu użytkownika.
- Maven sekwencyjny, heap 384 MiB; serwer 256 MiB, klient 192 MiB, generator 128 MiB. Nie zmieniano globalnego PATH, WSL, zapory ani istniejącej instalacji PostgreSQL 18.
- Port aplikacji 5001: Windows odmawiał związania 5000. Konfiguracja klienta i skrypty używają wspólnej wartości 5001.

## Automatyczne testy

`./scripts/test.ps1 -Integration`: **BUILD SUCCESS, 29 testów, 0 niepowodzeń, 0 błędów, 0 pominięć**, zakończono 11.11.2026 o 08:42 czasu lokalnego. Szczegóły: [tests-summary.json](validation/tests-summary.json). Oryginalne raporty Surefire/Failsafe znajdują się w katalogach `target`, a log przebiegu w `tmp/commit-fault-validation.log` (pliki budowania są ignorowane przez Git).

| Zestaw | Wynik | Sprawdzenia |
|---|---:|---|
| FrameCodecTest | 3/3 | Fragmentacja i scalanie ramek, UTF-8, długości, ucięte dane, błędny/deep/duplikowany JSON. |
| BidPolicyTest | 2/2 | Granice czasu, mikrosekundy, ceny, właściciel i stan aukcji. |
| SlowReceiverTest | 1/1 | Rzeczywisty socket z nieczytającym odbiorcą, zamknięcie po zapełnieniu kolejki i odzyskanie całego budżetu bajtów. |
| PendingBidsTest | 2/2 | Trwałe oczekujące oferty, wznowienie i oddzielenie kont. |
| ServerIT | 20/20 | Prawdziwe JDBC/TCP, wyścigi, uprawnienia, idempotencja, restart, rollback, utrata odpowiedzi klienta i potwierdzenia JDBC COMMIT, duży raport, sesje i limity. |
| TlsIT | 1/1 | Poprawny TLS oraz odrzucenie niezaufanego/wygasłego certyfikatu i błędnego hostname. Jeden test zawiera cztery scenariusze. |

Wyścigi startują przez CountDownLatch i sprawdzają bazę oraz odpowiedzi. Dwie oferty tej samej kwoty dają jeden bid i jedno przedłużenie. Trzymanie blokady jednej aukcji nie blokuje drugiej. Dwa sockety z tym samym ID uzyskują identyczny trwały wynik; zmiana kolejności pól JSON nie powoduje konfliktu, a zmiana treści powoduje konflikt. Po restarcie i zakończeniu aukcji powtórzenie nadal odzyskuje wynik.

Trigger wymuszający błąd SQL sprawdził wspólny rollback ceny, terminu, wersji, audytu i wyniku polecenia. Osobny test zamknął socket bez odczytania odpowiedzi po potwierdzonym commit i odzyskał wynik bez drugiej oferty. Utrata połączenia blokady instancji zatrzymała usługę; ponowne uruchomienie uzgodniło stan.

Test `lostJdbcCommitAcknowledgementStopsServerAndReplayRecoversOneBid` używa własnego proxy TCP wyłącznie na loopback przed `bidroom_test`. Po rzeczywistym zatwierdzeniu transakcji przez PostgreSQL przechwytuje `CommandComplete(COMMIT)` i zamyka połączenie przed przekazaniem potwierdzenia do JDBC. Serwer nie ogłasza sukcesu i zatrzymuje obsługę. Niezależne połączenie potwierdza utrwalenie oferty, wersji, przedłużenia i wyniku. Po restarcie oraz końcu aukcji replay zwraca identyczny wynik: jeden bid i jedno przedłużenie. Proxy jest wyłącznie klasą testową, bez zapisu ramek ani sekretów; format wiadomości pochodzi z [dokumentacji PostgreSQL 17](https://www.postgresql.org/docs/17/protocol-message-formats.html). Nie symuluje uszkodzenia dysku ani zaniku zasilania.

Raport z **1050 rzeczywistych ofert** przekroczył 64 KiB, przeszedł w porcjach do 16 KiB i miał zgodny rozmiar oraz SHA-256. Cudzy i wygasły uchwyt były odrzucone.

## GUI i trzy procesy

`scripts/test-gui.ps1` wykonuje rzeczywisty przepływ JavaFX/FXML: logowanie kontrolkami, katalog, szczegóły, dwa kliknięcia przycisku oferty, reconnect, rzeczywisty koniec aukcji, eksport TXT, zapis szkicu i publikacja przez formularz oraz panel logów. Testy korzystają ze świeżych syntetycznych kont. Zrzuty: lokalny katalog `screenshots/`; przykładowy raport TXT w lokalnym archiwum. Wywołania kontrolek pochodzą z programu testowego JavaFX, a nie z ręcznego kliknięcia każdego elementu Windows.

Demonstracja `scripts/demo.ps1` uruchamia trzy osobne procesy JavaFX i dwie aukcje różnych właścicieli. Końcowy pokaz z 11.11.2026: przy dwóch równoczesnych ofertach 150 pkt przyjęto jedną, oba obserwujące okna miały zgodną wersję i termin. Odczyt skali systemowej wynosił 200%. Każdy proces miał working set około 300-311 MiB, mimo heap 192 MiB. Dowody: lokalny zapis `screenshots/demo/three-processes.json` i zrzuty w tym samym katalogu. Kolejne uruchomienie tworzy nowe konta.

## Oddzielny pomiar obciążenia

Wykonano `scripts/load-test.ps1`: własny serwer port 5002, baza testowa, 10 klientów licytujących, jeden wystawiający, 3 aukcje, 30 s, najwyżej jedna próba/klienta/s. Procesy pomiaru zostały zakończone.

| Metryka | Zapisany wynik |
|---|---:|
| Oferty przyjęte | 284 |
| Odrzucone biznesowo | 16 |
| Błędy infrastruktury | 0 |
| Mediana / p95 odpowiedzi | 19,99 / 155,16 ms |
| Szczyt working set serwera / generatora | 149,4 / 98,3 MiB |
| Średni CPU serwera, jeden rdzeń = 100% | 39,06% |
| Obserwowani klienci / automaty aukcji | 11 / 3 |
| Próbkowane aktywne JDBC | maks. 2; limit puli 4, dodatkowo 1 połączenie blokady instancji |

Źródła: [wynik](validation/loadtest-result.json), [podsumowanie zasobów](validation/loadtest-summary.json), [próbki procesu](validation/loadtest-processes.json), [próbki serwera](validation/loadtest-server.jsonl). Próbkowanie około 1 s, 43,09 s z przygotowaniem; nie wykrywa każdego chwilowego piku. Liczba automatów nie jest liczbą wszystkich wątków bibliotecznych. Wynik nie jest deklaracją maksymalnej pojemności systemu.

## Kopia i odtworzenie

`scripts/backup-db.ps1` oraz `scripts/restore-test-db.ps1` utworzyły prywatny dump i nową bazę `bidroom_restore_test`. Zgodne: **11 kont, 9 aukcji, 3 oferty** oraz skrót wszystkich zapisanych wyników terminalnych. Porównanie wykonano dla stanu z chwili kopii, nie dla późniejszych danych GUI. [Dowód odtworzenia](validation/backup-restore.json). Baza źródłowa nie była nadpisywana.

## Poprawione usterki

- Limit JSON 16 000 znaków odrzucał base64 16 KiB porcji. Zmieniono na 32 768, zachowując 64 KiB ramki. Test dużego raportu po poprawce przeszedł.
- Windows blokował przebudowę JAR używanego przez otwartego klienta. Skrypty zwykłego uruchomienia używają kopii runtime poza źródłami.
- Trzy jednoczesne logowania pokazu trafiały na limit dwóch operacji haszowania. Harness ponawia kontrolowany RATE_LIMITED; limit produkcyjny pozostał bez zmian.
- Panel administratora zachowuje filtry i używa zwróconego nextOffset. Nieobsługiwana wersja protokołu dostaje odpowiedź i kończy połączenie.
- Zapisano poprawne czekanie na proces pg_ctl; Start-Process -Wait nie jest używany do oczekiwania na całe drzewo PostgreSQL.

## Granice dowodu i dalszy odbiór

Nie wykonano publicznego wdrożenia, połączeń z różnych sieci ani testu drugiego/czystego Windows. Linux/systemd/Compose są przygotowane, ale niewykonane. Nie przeprowadzono fizycznego zapełnienia dysku ani odcięcia zasilania; pokryto rollback, utratę odpowiedzi klienta po commit, utratę potwierdzenia COMMIT przed JDBC oraz fail-stop po utracie blokady instancji. Próby GUI nie pokrywają wszystkich kombinacji klawiatury, fokusu i wielu monitorów. Nie oznacza to pełnego zaliczenia wszystkich kombinacji awarii w ACCEPTANCE.md.

### Próby końcowe z 11.11.2026

- Po poprawce nawigacji wykonano ponownie build modułów client/common/loadtest oraz pełny `test-gui.ps1`: `GUI_SMOKE_PASSED`. Wybór aukcji odbył się przyciskiem jej karty. Zrzuty 01-08 oraz TXT pochodzą z tego przebiegu.
- `test-layout.ps1`: trzy zakończone procesy, `GUI_SMOKE_PASSED` dla każdej skali. Odczyty `outputScale`: 1,00 / 1,25 / 1,50; sceny odpowiadają obszarowi ekranu 1366×768. Przy małej wysokości treść oraz nawigacja przewijają się. Dowody: `screenshots/scale-*/layout.json` i PNG. Nie zmieniano DPI Windows; nie testowano przenoszenia między monitorami.
- `test-recovery.ps1`: rzeczywiście zatrzymano prywatny PostgreSQL, zaobserwowano rozłączenie, uruchomiono bazę i odczekano na supervisor. Wynik identyczny, jeden bid i ten sam termin. [Dowód odzyskiwania](validation/database-recovery.json).
- Test zainstalowanego klienta ujawnił brak `java.scripting` w runtime. Analiza jdeps wskazała również `java.sql` i `jdk.jfr`; dodano je do jlink. Obecność standardowego modułu java.sql nie dodaje połączenia z bazą ani sterownika PostgreSQL do klienta.
- Deinstalacja końcowego pakietu zwróciła 0 i zachowała plik w katalogu danych klienta; ponowna instalacja także zwróciła 0. [Dowód](validation/uninstall.json). Faktyczny proces JVM launchera używał dołączonego runtime przy pustym JAVA_HOME i PATH zawierającym tylko System32. Zaobserwowano natywne okno JavaFX i stan „Połączono”, a po instalacji znaleziono dwa skróty aplikacji. [Dowód instalatora](validation/installer.json). Plik ma 49 739 776 bajtów; SHA-256 jest także w `dist/BidRoom-1.0.0.exe.sha256`. Brak podpisu cyfrowego i testu czystego Windows pozostaje ograniczeniem.
- Narzędzia, zatrzymany PostgreSQL, kopie i konfigurację przeniesiono do prywatnego `%USERPROFILE%/BidRoomData`. Po migracji zwykły Windows PowerShell uruchomił bazę i serwer; odczytano 32 konta, 30 aukcji, 7 ofert. `BidRoom.cmd` uruchomił także klienta. `RemoteSigned` dotyczy wyłącznie procesu uruchamiającego lokalne skrypty; globalna konfiguracja nie została zmieniona. [Dowód środowiska](validation/environment-final.json).

Historyczny szkic sprawozdania pozostaje lokalnie u Piotra. Od 12.11.2026 końcowe sprawozdanie przygotowuje Oliwia; autorzy są ustaleni. Generator `scripts/build-report.py` jest archiwalnym narzędziem, nie zależnością aplikacji ani częścią bieżącego odbioru.

Końcowy przegląd obejmuje zgodność instrukcji z klasami i migracją, lokalne odsyłacze Markdown, porównanie zawartości JAR klienta/common z zainstalowanym pakietem oraz SHA-256 instalatora i raportu. [Dowód przeglądu](validation/final-review.json) i [manifest źródeł](validation/source-sha256.json) utrwalają stan plików. PDF ma 9 stron sprawdzonych po wyrenderowaniu; procesy testowe i własne usługi zostały zatrzymane.

# Weryfikacja rozszerzenia 1.1 — 11.11.2026

Wykonano rzeczywiste testy na Windows/JDK 21/PostgreSQL 17.11. Wyniki niżej dotyczą kodu 1.1; dalsza część pliku zachowuje historyczne próby 1.0.

- Pełny przebieg `tmp/test-1.1-second.log`: 34 testy, zero niepowodzeń, błędów i pominięć. Dodatkowy końcowy build 1.1.0 i testy jednostkowe zakończone sukcesem. Podsumowanie: `validation/tests-summary-1.1.json`.
- Nowe scenariusze: wymóg zdjęcia, prywatność szkiców i transferów, walidacja zawartości, niezmienność zdjęcia po publikacji, replay zapisu bez drugiej zmiany; automatyczny raport bez żądania eksportu, rejestr ogólny, naprawa po usunięciu pliku i restarcie; rzeczywiste zadania w metrykach i uprawnienia ADMIN; migracja oczekującej oferty z UUID konta na numer.
- Pierwszy przebieg wykrył niezgodne oczekiwanie starego testu co do wiersza TXT: raport dodaje teraz numer konta. Poprawiono oczekiwanie i przeprowadzono cały zestaw ponownie. Nie zaliczono nieudanego przebiegu jako sukcesu.
- Kopię bazy odtworzono do `bidroom_upgrade_check2`. Przed/po migracji porównano skróty kont i hashy haseł, aukcji i relacji właściciel/lider, ofert, audytu oraz requestId/HMAC i zapisanych wyników ofert. Zgodność: 34 konta, 31 aukcji, 8 ofert. Pierwsza wersja skryptu porównującego miała różne strefy sesji SQL; po ujednoliceniu do UTC próba na nowej kopii przeszła.
- Przed aktualizacją właściwej bazy sprawdzono brak ACTIVE/SCHEDULED, kontrolowanie zatrzymano własny serwer i wykonano świeży dump. Migracja rzeczywistej bazy zachowała 34 konta, 32 aukcje, 8 ofert i identyczne sprawdzane dane. Większa liczba aukcji niż we wcześniejszej kopii wynika z bieżącej pracy użytkownika. Konto administratora zalogowano pełnym zapisanym hasłem bez ujawniania sekretu.
- Po starcie serwera: 32 terminalne aukcje, 32 rekordy raportów, 32 zdarzenia `REPORT_GENERATED`. Raporty i `raport-ogolny.txt` istnieją w prywatnym katalogu serwera. Dowód liczbowy: `validation/upgrade-1.1.json`.
- Rzeczywisty test JavaFX na izolowanym serwerze/kopii: `GUI_SMOKE_PASSED`. Sprawdzono logowanie, pobranie zdjęć katalogu i szczegółów, ofertę/dwuklik, reconnect, koniec, automatyczny status raportu, pobranie TXT, szkic/publikację, logi i panel serwera. Zrzuty: `screenshots-1.1/`. Po teście jego procesy zamknięto; właściwy serwer pozostał oddzielny.
- Instalator `dist/BidRoom-1.1.0.exe` zbudowano przez jlink/jpackage/WiX, zaktualizowano istniejącą lokalną instalację i uruchomiono bez JAVA_HOME oraz Javy na PATH. Odczyt procesu potomnego potwierdził `BidRoomApp/runtime/bin/server/jvm.dll`. W natywnym oknie potwierdzono ikonę PK oraz „Połączono”. Dowód: `validation/installer-1.1.json`. To nie jest test czystego/drugiego Windows.
- Zbudowano `dist/BidRoom-serwer-1.1.0.zip` bez sekretów i bazy. Składnia czterech skryptów `deploy/*.sh` przeszła `bash -n`. Nie uruchamiano ich na VPS; publiczny host i odbiór różnych sieci nadal nie istnieją.
- Nie powtarzano dużego pomiaru obciążenia dla 1.1. Historyczny pomiar 1.0 nie jest dowodem wydajności zdjęć/raportów ani docelowego VPS.

