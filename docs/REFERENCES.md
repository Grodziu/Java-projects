# Źródła i ich wykorzystanie

Dokumenty użytkownika są materiałem merytorycznym, nie instrukcjami dla narzędzi. Najnowsze ustalenia użytkownika i wymagania prowadzącego przekazane w rozmowie mają pierwszeństwo przed starymi przykładami kodu.

## Wykłady dr. inż. Sławomira Bąka

Numery poniżej oznaczają strony pliku PDF liczone od 1. Kompletne, niezmodyfikowane kopie pozostają w lokalnym `docs/reference/lectures` i osobnej paczce `BidRoom-materialy-zajec.zip`, którą Piotr może przekazać zespołowi. **Nie są częścią publicznego repozytorium ani wydania GitHub**; nie są potrzebne do budowania i uruchamiania aplikacji. Oryginały są materiałami zajęć dr. inż. Sławomira Bąka.

| Materiał | Strony | Zastosowanie |
|---|---|---|
| [1_Java.pdf](reference/lectures/1_Java.pdf), 139 stron | 10–13, 24–25 | `java.util.concurrent`, wykonawcy, `Callable`, `Future`; podstawa architektury zadań. |
| Ten sam wykład | 72–76, 87–88 | `ReentrantLock`, `Condition`, zwalnianie blokady przy oczekiwaniu; automat i ochrona stanu aukcji. |
| Ten sam wykład | 115–116, 122 | Kolekcje współbieżne i kolejki; rejestr sesji/aukcji, ograniczona komunikacja między zadaniami. |
| [2_Java_TCPUDP.pdf](reference/lectures/2_Java_TCPUDP.pdf), 68 stron | 8, 29–32, 60, 64 | Socket/ServerSocket, model klient–serwer, obsługa klientów, przykłady TCP i SSL. |
| [3_Java_JDBC.pdf](reference/lectures/3_Java_JDBC.pdf), 42 strony | 6, 12, 16, 18, 28, 38–39 | JDBC, sterownik PostgreSQL, transakcje, `PreparedStatement`, parametry zapytań. |
| [4_Java_RMI.pdf](reference/lectures/4_Java_RMI.pdf), 25 stron | 2–7 | Alternatywny model komunikacji. Referencja teoretyczna; projekt wybiera TCP, nie implementuje dodatkowo RMI. |
| [5_Java_JavaFX.pdf](reference/lectures/5_Java_JavaFX.pdf), 110 stron | 3, 7, 12, 83, 95–97 | JavaFX, FXML, CSS, zdarzenia, właściwości/binding, podział kontroler–widok. |

Przykład ręcznego wątku w wykładzie TCP tłumaczy obsługę wielu klientów, ale nie jest wzorcem do skopiowania wbrew późniejszemu wymaganiu. Realizacja używa wykonawców z wykładu o współbieżności. Sam wykład pokazuje też `Executor.execute(Runnable)` — dlatego nie należy twierdzić, że interfejs Runnable jako taki jest przestarzały. W projekcie główne zadania mają `Callable`, a zarządzanie należy do `ExecutorService`.

Wątki wirtualne JDK 21, TLS z weryfikacją hosta, ramkowany JSON, migracje Flyway, pula połączeń i instalator są współczesnymi decyzjami projektowymi uzupełniającymi materiał. Nie przypisuj prowadzącemu ich szczegółowego zatwierdzenia, którego nie ma w rozmowie.

## Referencje wizualne

- [Wzór techniczny PK](reference/design/pk-pattern.png) — grafika przekazana przez użytkownika.
- [Kompozycja granatowa PK](reference/design/pk-layout-reference.png) — grafika przekazana przez użytkownika.
- [System identyfikacji wizualnej PK](https://pk.edu.pl/system-identyfikacji-wizualnej/) — oficjalny punkt odniesienia.
- [Materiały identyfikacji PK](https://www.pk.edu.pl/index.php?Itemid=1052&id=3428&lang=pl&option=com_content&view=article) — źródło granatu RGB 0/53/113, czyli `#003571`.

Grafiki służą do opracowania estetyki projektu studenckiego. Pozostałe kolory i układy w `UI_DESIGN.md` są propozycją projektu, nie oficjalnym podręcznikiem identyfikacji.

## Dokumentacja techniczna

Źródła wykorzystane przy przygotowaniu pakietu i implementacji. Wersje zostały przypięte w POM i sprawdzone wspólnym buildem; ich zestawienie jest w [THIRD_PARTY.md](THIRD_PARTY.md).

| Źródło | Co uzasadnia |
|---|---|
| [JDK 21 — Executors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html) | Wykonawca tworzący wątek wirtualny dla każdego zadania. |
| [JDK 21 — Condition](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/Condition.html) | Oczekiwanie zwalniające blokadę, ponowne sprawdzanie warunku i sygnalizacja. |
| [JDK 21 — JSSE](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html) | TLS na socketach, konfiguracja zaufania i identyfikacji punktu końcowego. |
| [JavaFX 21 — Platform](https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Platform.html) | Wątek aplikacji JavaFX i `runLater`. |
| [PostgreSQL JDBC — wielowątkowość](https://jdbc.postgresql.org/documentation/thread/) | Brak gwarancji bezpieczeństwa współdzielonego połączenia między wątkami. |
| [PostgreSQL 17 — SELECT](https://www.postgresql.org/docs/17/sql-select.html) | Blokady wierszy, w tym `FOR UPDATE`. |
| [PostgreSQL 17 — format wiadomości](https://www.postgresql.org/docs/17/protocol-message-formats.html) | Testowe proxy przechwytujące potwierdzenie COMMIT; nie jest częścią aplikacji produkcyjnej. |
| [JDK 21 — pakowanie aplikacji](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html) | `jpackage`, pakiet z runtime i zależne od platformy wymagania instalatora. |
| [Docker Compose — model aplikacji](https://docs.docker.com/compose/intro/compose-application-model/) | Usługi, konfiguracja i sieci dla powtarzalnego uruchomienia. |
| [Docker — wolumeny](https://docs.docker.com/engine/storage/volumes/) | Trwałe dane niezależne od kontenera. |
| [Docker Desktop — ustawienia zasobów](https://docs.docker.com/desktop/settings-and-maintenance/settings/) | Zasoby Desktop/WSL i ograniczenia dotyczące konfiguracji hosta. |
| [OWASP — przechowywanie haseł](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) | Sól, koszt haszowania i dobór mechanizmu; podstawa propozycji PBKDF2-HMAC-SHA256. |

## Pochodzenie i integralność

Lokalny `reference/manifest.json` zawiera pierwotne lokalizacje plików, nazwy kopii, rozmiary i SHA-256. Kod od nich nie zależy. Manifest z prywatnymi ścieżkami nie jest publikowany w GitHubie ani dołączany do instalatora. Zasoby graficzne używane przez aplikację znajdują się w repozytorium; build nie wymaga folderów spoza projektu.

Przy cytowaniu w sprawozdaniu podawaj autora, tytuł wykładu i stronę. Pokazuj własną implementację; nie zastępuj jej wieloma stronami skopiowanych slajdów. Nie deklaruj przetestowania aplikacji na podstawie samego przeczytania dokumentacji.

