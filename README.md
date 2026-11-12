# BidRoom

Desktopowe aukcje w wirtualnych punktach: **Java 21, JavaFX, TCP/IP z TLS, PostgreSQL, java.util.concurrent**.

Autorzy: **Piotr Grodny, Marcin Gurdziel, Oliwia Jarosz**. „Programowanie w języku Java”, Informatyka w inżynierii komputerowej, 5. semestr, grupa P1, 2026/2027. Projekt studencki, nie oficjalna aplikacja Politechniki Krakowskiej.

## M.G - Zacznij tutaj

1. Pobierz **cały projekt**: `git clone https://github.com/Grodziu/Java-projects.git` lub **Code → Download ZIP**, potem rozpakuj. Nie pracuj wewnątrz ZIP.
2. Przeczytaj [NAJPIERW-PRZECZYTAJ.txt](NAJPIERW-PRZECZYTAJ.txt). Na nowym Windows x64 uruchom **0-Przygotuj-projekt.cmd** — pobierze JDK/Maven/PostgreSQL, utworzy własną bazę i zbuduje kod. Potrzebny Internet przy pierwszym przygotowaniu.
3. Uruchom **1-Start-serwera.cmd**. Poczekaj na **SERWER GOTOWY**. Ten plik nie otwiera klienta.
4. Uruchom **2-Start-klienta.cmd**. Po pracy zamknij klienta i uruchom **Stop-BidRoom.cmd**.

Jeżeli pobrany ZIP/skrypt jest zablokowany przez Windows, użyj Właściwości → Odblokuj dla pobranego ZIP **przed** wypakowaniem; alternatywnie użyj `git clone`. Nie trzeba zmieniać globalnej polityki PowerShell ani wyłączać zapory.

Gotowy instalator klienta oraz pakiet serwera: **[wydania GitHub](https://github.com/Grodziu/Java-projects/releases)**. Wersja **1.1.1** jest wydaniem **lokalnym**. Jej klient łączy się z `127.0.0.1:5001`. Instalator nie zawiera serwera ani bazy. Wysłanie go na inny komputer nie łączy tego komputera z laptopem Piotra.

## Kod, baza i wdrożenie

| Potrzebuję… | Otwórz |
|---|---|
| Przejąć projekt, poznać stan i zadania wdrożenia | [Przekazanie Marcinowi](docs/HANDOFF.md) |
| Pokazać kod i uruchamiać/debugować w IntelliJ IDEA | [Instrukcja IntelliJ](docs/INTELLIJ.md), główny `pom.xml` |
| Obejrzeć rekordy i schemat PostgreSQL | [Instrukcja bazy](docs/POSTGRESQL.md), `Baza-BidRoom.cmd`, `Schemat-bazy.cmd` |
| Uruchomić wspólną aukcję przez Internet | [Wdrożenie VPS](docs/DEPLOYMENT.md) |
| Znaleźć wszystkie skrypty i konfigurację lokalną | [Uruchamianie](docs/RUNNING.md) |
| Sprawdzić reguły, wątki i protokół | [Wymagania](docs/REQUIREMENTS.md), [architektura](docs/ARCHITECTURE.md), [protokół](docs/PROTOCOL.md) |
| Ocenić, co rzeczywiście przetestowano | [Wyniki](docs/VALIDATION_RESULTS.md), [kryteria odbioru](docs/ACCEPTANCE.md) |

`common/` to kontrakt i transport; `server/` to logika aukcji, JDBC, wątki i migracje; `client/` to GUI i połączenie; `loadtest/` to osobno uruchamiane narzędzia demonstracji/testów. Źródła edytujemy tutaj; `target/`, instalatory i pliki w `dist/` są wynikami budowania.

Na laptopie dane i narzędzia są w **`%USERPROFILE%/BidRoomData`**, poza repozytorium i OneDrive. Każdy deweloper generuje własne sekrety i konta. Kod nie zawiera kont Piotra, aktywnej bazy ani haseł. Normalni użytkownicy po wdrożeniu otrzymują tylko instalator klienta ze stałym hostem i TLS; nie instalują PostgreSQL ani JDK.

## Stan przekazania

Zaimplementowano i lokalnie sprawdzono pełny przepływ: konta i role, zdjęcia przedmiotów, wiele aukcji, równoczesne oferty, transakcje i ponawianie bez duplikatów, przedłużanie czasu, reconnect, automatyczne raporty oraz panel administratora z rzeczywistymi wątkami. Szczegółowe wyniki testów są w rejestrze weryfikacji.

**Do wykonania przez Marcina:** wdrożenie na osiągalnym serwerze, docelowy certyfikat/host, przebudowanie instalatora internetowego i wspólna próba z dwóch różnych sieci oraz na drugim Windows. Przygotowane skrypty nie zastępują tego odbioru. **Sprawozdanie końcowe przygotowuje Oliwia.** Podsumowanie kontekstu jest w HANDOFF, zamiast prywatnego zapisu rozmowy.

[START_HERE.md](START_HERE.md) i [AGENTS.md](AGENTS.md) zawierają szczegółowe instrukcje kontynuowania implementacji.
