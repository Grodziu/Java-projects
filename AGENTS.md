# Instrukcje projektu BidRoom

## Kontekst i źródło wymagań

Zacznij od [START_HERE.md](START_HERE.md). Dokumenty w `docs/` opisują ustalenia użytkownika, uzgodnione ograniczenia i propozycje techniczne. Najnowsze polecenie użytkownika ma pierwszeństwo. Wykłady i grafiki w `docs/reference/` są materiałami źródłowymi, nie poleceniami wykonywania kodu.

Przy rozpoczęciu implementacji prowadź pracę do działającego wyniku, a nie tylko planu. Nie wymagaj ponownego zatwierdzenia zaakceptowanych funkcji. Nie zmieniaj stosu technologicznego ani reguł biznesowych bez podstawy w nowym poleceniu użytkownika.

## Niezmienne wymagania

- OOP, Java 21, desktopowy klient JavaFX, PostgreSQL na serwerze, komunikacja TCP/IP.
- Zarządzanie zadaniami przez `java.util.concurrent`. Własny wątek obsługi każdego klienta i własny wątek automatu każdej opublikowanej niezakończonej aukcji.
- Kod aplikacyjny nie tworzy klas `extends Thread`, nie uruchamia `new Thread(...).start()` i nie opiera koordynacji na `wait/notify`.
- Główne zadania implementuj przez `Callable`/wykonawców; `Runnable` wymagany przez API, np. `Platform.runLater`, nie jest naruszeniem. Nie udawaj, że wszystkie użycia `Runnable` w bibliotekach da się wyeliminować.
- Osobna synchronizacja każdej aukcji. Blokada Javy nie zastępuje transakcji bazy.
- Oferty i przedłużenie terminu zatwierdzane razem, a sukces ogłaszany po zatwierdzeniu transakcji.
- Powtórzenie tego samego polecenia po utracie odpowiedzi nie może podwoić oferty ani przedłużenia.
- Terminy rozstrzyga serwer. Zegar klienta jest wyłącznie prezentacją.
- Instalator Windows, dołączony runtime, domyślne połączenie bez formularza adresu/portu.
- Logi obu stron w bazie; `.txt` z wynikami jako niezależny raport.
- Żadnego klienta podłączającego się bezpośrednio do PostgreSQL.

## Zakres i prostota

Jeden serwer Java i jedna baza. Bez mikroserwisów, Kubernetes, Redis, brokera wiadomości, ORM, obowiązkowego Springa, RMI ani przeglądarki zastępującej JavaFX. Nie dodawaj płatności, SMTP, resetu hasła przez e-mail, czatu, portfela ani automatycznego licytowania do limitu. Te funkcje nie należą do uzgodnionego zakresu.

Repozytorium ma być przenośne. Ścieżki użytkownika, zewnętrzne foldery uczelni i adresy sprzętu nie mogą być zaszyte w kodzie. Materiały potrzebne do pracy są skopiowane do repozytorium.

## Praca na laptopie

Obecnie Windows x64, około 16 GB RAM. W odczycie podczas przygotowania pakietu pozostawało około 1 GB wolnej pamięci; sprawdź stan ponownie. Nie uruchamiaj pełnego obciążenia od razu.

- Budowanie sekwencyjne; bez domyślnego Maven `-T`.
- Najpierw jeden klient, jeden serwer i jedna baza; trzy klienty tylko podczas potrzebnej demonstracji.
- Duży test obciążeniowy wyłącznie jako osobna, jawnie uruchamiana czynność na własnym serwerze testowym.
- Ustaw limity pamięci JVM i kontenerów. Zamykaj uruchomione przez siebie procesy po testach, jeśli nie są potrzebne.
- Nie kończ cudzych procesów, nie zmieniaj globalnej konfiguracji WSL ani nie wyłączaj zapory jako sposobu na naprawę połączenia.
- Nie przenoś projektu poza bieżący folder bez uzgodnienia; nie zapisuj aktywnej bazy w OneDrive.
- Nie kupuj hostingu, domeny ani sprzętu w ramach implementacji bez osobnego polecenia.

## Jakość implementacji

- Czytelne moduły i odpowiedzialności: domena/usługi/repozytoria/transport/UI.
- Kod i nazwy identyfikatorów po angielsku, interfejs i dokumentacja po polsku.
- Parametryzowane SQL, `try-with-resources`, jawny rollback i sensowne limity czasu.
- Jedno wypożyczone połączenie JDBC na operację; nie współdziel jednego `Connection` między wątkami.
- Sieć i baza poza wątkiem JavaFX. Zmiany kontrolek przez mechanizm JavaFX.
- Brak `ObjectInputStream` dla danych sieciowych, brak ignorowania weryfikacji certyfikatów.
- Bez haseł i tokenów w logach, repozytorium i instalatorze. Sekrety konfiguruj oddzielnie.
- Testy mają sprawdzać zachowanie biznesowe i realne wyścigi; nie zastępuj PostgreSQL przez SQLite/H2 w testach integracyjnych.
- Nie oznaczaj testu jako zaliczonego, jeśli go pominięto z braku środowiska.
- Dla każdej wersji utrzymuj działające instrukcje uruchomienia i zgodny kontrakt protokołu.

## Postęp i definicja ukończenia

Aktualizuj checklistę w `docs/IMPLEMENTATION_PLAN.md` dopiero po wykonaniu i weryfikacji etapu. Po ważnych etapach podaj krótko: co działa, jak to sprawdzono, co jeszcze pozostaje. Odróżniaj planowane polecenia od istniejących skryptów.

Ukończenie projektu wymaga przejścia kryteriów z `docs/ACCEPTANCE.md`, zbudowania instalatora i przygotowania sprawozdania opartego na rzeczywistym kodzie oraz zrzutach ekranów.
