# IntelliJ IDEA — kod, uruchomienie i pokaz

## Import i JDK

1. Najpierw przygotuj środowisko przez `0-Przygotuj-projekt.cmd`. Nie trzeba instalować osobnej globalnej Javy ani Mavena.
2. W IntelliJ wybierz **Open**, wskaż główny **`pom.xml`** i otwórz jako projekt Maven. Poczekaj na import czterech modułów i zależności. Nie otwieraj samego `client/`.
3. W **File → Project Structure → Project SDK** wskaż **JDK 21** z `%USERPROFILE%/BidRoomData/tools/jdk21/jdk-21.0.13+9`. Language level: 21. W ustawieniach Maven wybierz Wrapper i JDK 21 dla importera/runnera. Globalne Java 8 nie nadaje się do tego projektu.
4. W repo są współdzielone konfiguracje `.run/`: **1. BidRoom - serwer** i **2. BidRoom - klient**. Korzystają z klas modułów, więc działają breakpointy, a zmiany kodu są kompilowane przed Run.

Konfiguracje Application i ich opcje opisuje [dokumentacja IntelliJ](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html). Jeśli import nada modułom nazwy z prefiksem projektu, wybierz w konfiguracji odpowiednio moduł `server` i `client` w polu classpath; klasy główne pozostają te same.

## Wariant do demonstracji kodu

1. Jeśli serwer był uruchomiony przez CMD, zatrzymaj go: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/stop-server.ps1`. Nie uruchamiaj drugiej instancji na tej samej bazie.
2. W terminalu IntelliJ: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/start-db.ps1`. To uruchamia sam PostgreSQL.
3. Wybierz **1. BidRoom - serwer → Run**. Konsola ma pokazać `BIDROOM_READY`. Dopiero potem **2. BidRoom - klient → Run**. Okno JavaFX korzysta z serwera uruchomionego w IDE.
4. **Debug** serwera pozwala zatrzymywać się w kodzie domeny/usług. Nie zatrzymuj wszystkich wątków na długo podczas mierzonej licytacji — zegar rzeczywisty nadal płynie. Do pokazu reguł granicznych służą deterministyczne testy z wstrzykniętym `Clock`.
5. Po pracy zamknij klienta, zatrzymaj serwer przyciskiem Stop w IntelliJ, a bazę przez `scripts/stop-db.ps1`.

Serwer w IDE ma opcję `-Dbidroom.config=local`, która jawnie wczytuje `%USERPROFILE%/BidRoomData/server-secrets.json`. Konfiguracja nie zawiera samych haseł. Zmienne środowiskowe mają pierwszeństwo nad plikiem. Produkcyjny skrypt nie używa tej opcji. Można też podać bezwzględną ścieżkę do innego prywatnego JSON przez `-Dbidroom.config=...`.

Jeśli tworzysz konfigurację ręcznie:

| Pole | Serwer | Klient |
|---|---|---|
| Main class | `pl.edu.bidroom.server.ServerMain` | `pl.edu.bidroom.client.Launcher` |
| Module | `server` | `client` |
| VM options | `-Xms64m -Xmx256m -Dfile.encoding=UTF-8 -Dbidroom.config=local` | `-Xms32m -Xmx192m -Dfile.encoding=UTF-8` |
| Working directory | katalog głównego `pom.xml` | katalog głównego `pom.xml` |

Uruchamiaj `Launcher`, nie samą klasę dziedziczącą `Application`; launcher wywołuje JavaFX z poprawnym classpathem. Nie dodawaj ręcznie pobranego SDK JavaFX — Maven dostarcza zależności. Drugi klient musi mieć osobny katalog danych, np. dodatkową opcję `-Dbidroom.dataDir=$USER_HOME$/BidRoomData/clients/idea-drugi`.

## Mapa kodu do pokazania prowadzącemu

| Temat | Plik / miejsce |
|---|---|
| OOP i kontrakt danych | `common/.../Models.java`, `Wire.java`, `BusinessException.java` |
| Sockety, ramka i korelacja odpowiedzi | `common/.../FrameCodec.java`, `RpcConnection.java` |
| Start aplikacji / składanie zależności | `server/.../ServerMain.java`, `ServerApp.java` |
| Osobny klient i jego wykonawca | `server/.../transport/ClientSessionHandler.java`, `ServerApp.java` |
| Automat aukcji i własny wątek | `server/.../service/AuctionService.java` → wewnętrzna klasa `AuctionLifecycleTask`; stan i blokady w `AuctionRuntimes.java` |
| Blokada per aukcja i transakcja oferty | `server/.../service/AuctionService.java`, `CommandService.java` |
| Reguła przedłużenia, zegar | `server/.../domain/BidPolicy.java` i testy `BidPolicyTest` |
| JDBC, pula, migracje | `server/.../persistence/Database.java`, `AuctionRepository.java`, `src/main/resources/db/migration/` |
| Automatyczne raporty i zdjęcia | `server/.../service/ReportService.java`, `PhotoService.java` |
| Liczniki wykonywanych zadań | `server/.../service/TaskRegistry.java` |
| GUI, style i pełne grafiki PK | `client/.../ui/MainController.java`, `BrandArtwork.java`, `src/main/resources/ui/main.fxml`, `app.css` |
| Sieć poza wątkiem GUI, reconnect | `client/.../ClientGateway.java`, `PendingBids.java` |
| Realne wyścigi z PostgreSQL | `server/src/test/.../ServerIT.java`, `TlsIT.java` |

`...` oznacza pakiet `pl/edu/bidroom`, z odpowiednim `server` lub `client`. Użyj **Navigate → Class** / dwukrotnego Shift, aby znaleźć klasę. Szczegóły implementacji: [ARCHITECTURE](ARCHITECTURE.md).

## Build i testy

W terminalu: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/build-project.ps1`. W oknie Maven można też uruchomić `package` dla całego reaktora. Testy jednostkowe uruchomisz w IDE zieloną strzałką obok klasy.

Pełne testy PostgreSQL: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/test.ps1 -Integration`. Skrypt sam importuje prywatne dane środowiska i wskazuje osobną bazę `bidroom_test`. Nie uruchamiaj testów integracyjnych ze zmienionym adresem bazy produkcyjnej. Wyniki są w `*/target/surefire-reports` i `server/target/failsafe-reports`.

Instalator buduje `scripts/build-installer.ps1`, pakiet serwera `scripts/build-server-package.ps1`. Po zmianach kodu przebuduj pakiety; stary instalator nie aktualizuje się sam.
