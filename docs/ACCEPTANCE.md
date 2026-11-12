# Kryteria odbioru

Poniższe scenariusze pozostają kryteriami odbioru. Implementacja i testy lokalne istnieją; wykonane sprawdzenia, dowody oraz ograniczenia są w [VALIDATION_RESULTS.md](VALIDATION_RESULTS.md). Sam opis scenariusza w tym pliku nie oznacza jego zaliczenia. Testy JDBC używają realnego PostgreSQL w oddzielnej bazie testowej; pomiar obciążenia jest osobnym poleceniem.

## Pokrycie wymagań

| Wymagania | Dowód |
|---|---|
| R01 | Model domeny, rozdzielone odpowiedzialności, brak całej logiki w kontrolerze JavaFX. |
| R02–R05 | Testy współbieżności i kod wykonawców, automatów, blokad, transakcji oraz limitów. |
| R06 | Działające sockety TCP, ramkowanie, wielu klientów, oddzielny test TLS. |
| R07, R15 | Rzeczywiste okna JavaFX, responsywność i zgodność z kierunkiem wizualnym. |
| R08–R09 | Migracje i zawartość DB po operacjach klienta/serwera, odtworzenie po restarcie. |
| R10 | Czytelny `.txt` zgodny z ofertami, terminami i wynikiem. |
| R11 | Rejestracja, logowanie, role, uprawnienia i blokady. |
| R12 | Ponowne połączenie, niepotwierdzone polecenie i restart serwera. |
| R13 | Instalator testowany bez wymagania JDK/Dockera/DB na komputerze klienta. |
| R14 | Rzeczywiste połączenia co najmniej dwóch urządzeń z różnych sieci po wyborze hosta. |
| R16 | Pełne sprawozdanie z prawdziwym kodem, zrzutami i wnioskami. |

## A. Reguły ofert i czasu

1. Pierwsza oferta równa cenie startowej jest przyjmowana; niższa odrzucana. Kolejna musi osiągnąć bieżącą cenę + przebicie.
2. Kwoty niedodatnie, poza zakresem i przepełniające dodawanie nie zmieniają stanu.
3. Własna aukcja, konto zablokowane, brak logowania i stan inny niż ACTIVE uniemożliwiają ofertę.
4. Dla pozostałego czasu 90 s i dokładnie 60 s nie ma przedłużenia. Dla 59,999 s wynik to 60 s, 50 s → 60 s, 20 s → 50 s, 5 s → 35 s.
5. Przy `now == endAt` i `now > endAt` oferta jest odrzucana, także gdy automat jeszcze nie zapisał FINISHED.
6. Odrzucona oferta i ponowienie przyjętego polecenia nie przedłużają czasu. Kolejne nowe poprawne oferty mogą wielokrotnie przesuwać koniec zgodnie z regułą.
7. Nie wolno zaokrąglić czasu do sekund przed porównaniem. Testy i DB respektują precyzję mikrosekund.
8. Bez ofert wynik nie zawiera zwycięzcy ani ceny sprzedaży. Z ofertami wygrywa ostatnia najwyższa przyjęta oferta. CANCELLED nie ogłasza zwycięzcy.

Użyj wstrzykiwanego zegara i deterministycznych momentów. Test jednostkowy przedłużenia nie powinien czekać prawdziwej minuty. W testach zadania oczekującego steruj także jego wybudzeniem; samo przestawienie sztucznego `Clock` nie wybudza `Condition`.

## B. Współbieżność i transakcje

1. Równoczesne oferty tej samej dopuszczalnej kwoty od różnych użytkowników: dokładnie jedna przyjęta; cena, liczba ofert i audyt zgodne.
2. Wiele ofert różnych kwot: każda przyjęta spełnia minimum w momencie serializacji; odrzucona wyższa/niższa kwota zależy od kolejności. Nie zakładaj arbitralnie, że wszystkie powinny wejść.
3. Dwie aukcje obsługiwane równolegle; kontrolowane zablokowanie stanu pierwszej nie blokuje dostępu do stanu drugiej, jeśli pula DB ma dostępny zasób.
4. Wyścig oferty z końcem: jeśli nowy termin został zatwierdzony przed rozstrzygnięciem automatu, automat go respektuje; jeśli termin minął przed decyzją ofertową, oferta jest odrzucona. Brak oferty zaakceptowanej po FINISHED.
5. Anulowanie w wyścigu z ofertą ma spójny porządek; żadna oferta nie pojawia się po zatwierdzonym CANCELLED.
6. Dwa równoczesne polecenia o tym samym ID i treści: jeden bid, jedno przedłużenie, ten sam wynik. To samo ID i inna treść/aukcja: konflikt, bez dodatkowej zmiany.
7. Błąd SQL przed commit wycofuje ofertę, termin, wersję, audyt i wynik polecenia razem. Pamięć nie pokazuje zatwierdzonej zmiany.
8. Utrata odpowiedzi po commit oraz niepewny wynik commit: powtórzenie po reconnect/restart odzyskuje zapisany wynik, bez podwójnej oferty.
9. Rozłączenie klienta zwalnia przydział połączenia i zadania. Zakończenie/anulowanie aukcji zwalnia automat i limit; brak osieroconych zadań.
10. Drugie uruchomienie serwera na tej samej bazie jest odrzucane; utrata blokady instancji nie pozostawia serwera przyjmującego mutacje ze starej pamięci.

Start konkurujących czynności synchronizuj przez `CountDownLatch` lub `CyclicBarrier`. Testy mają ograniczony czas zakończenia, sprawdzają stan bazy i wyniki, nie tylko brak wyjątku. Nie opieraj ich na przypadkowym `sleep` i nadziei na wyścig. Nie podmieniaj PostgreSQL na H2.

## C. TCP, sesje i autoryzacja

- Ramka podzielona na wiele odczytów, kilka ramek naraz, polskie znaki UTF-8, zerowa/ujemna/nadmierna długość, niepoprawny JSON i koniec strumienia w środku ramki.
- Timeout nie rozstraja dekodera; po błędzie częściowej ramki socket jest zamykany. Przekroczenie limitu nie powoduje wielkiej alokacji.
- Nieobsługiwana wersja protokołu daje czytelne odrzucenie. Powolny odbiorca nie zatrzymuje pozostałych klientów ani automatów.
- Stara wersja snapshotu, odpowiedź na duplikat oraz powiadomienia w odwróconej kolejności nie cofają widoku.
- Reconnect w trakcie zmiany aukcji nie gubi jej aktualnego stanu dzięki ponownemu snapshotowi/subskrypcji.
- Dwa konta o loginie różniącym się wielkością liter nie powstają; równoczesna rejestracja tego samego loginu daje jeden sukces.
- Pole `role=ADMIN` w publicznej rejestracji nie przyznaje uprawnień. USER nie odczyta logów administratora ani cudzych szkiców/wyników poleceń. Anulowany nieopublikowany szkic pozostaje prywatny, także w archiwum i eksporcie.
- Dwie równoczesne blokady administratorów nie pozostawią systemu bez aktywnego administratora. Blokada użytkownika kończy sesje, wcześniejsze oferty pozostają.
- Niepoprawny/wygasły token nie wznawia sesji; logout unieważnia token. Hasła i tokeny nie są logowane.
- TLS: poprawny certyfikat i hostname działają; błędny hostname, niezaufany lub wygasły certyfikat są odrzucane. Tryb production nie przechodzi automatycznie na jawny TCP.

## D. Awarie, historia, logi i raport

- Przedłuż aukcję, wyłącz i uruchom serwer; odtworzony jest aktualny termin, nie pierwotny. Jeżeli minął podczas wyłączenia, aukcja kończy się raz, bez rekompensaty czasu.
- Awaria DB powoduje niedostępność nowych zmian i czytelny stan GUI; po powrocie najpierw uzgodnienie stanu, potem dalsze oferty.
- Klient przechowuje wysłane niepotwierdzone polecenie bez sekretów i po logowaniu odzyskuje wynik. Nie wysyła nowych ofert z czasu offline.
- Zdarzenia uruchomienia/połączenia/błędu klienta oraz diagnostyka serwera pojawiają się w DB. Użytkownik zgłaszający wynika z sesji.
- Ponowiona paczka logów nie duplikuje wpisów. Bez ACK wpis pozostaje w buforze; restart klienta nie usuwa bufora.
- Bufor ma limit i rotację, a pełny dysk nie powoduje nieograniczonego zużycia RAM. Utrata diagnostyki jest sygnalizowana; audyt biznesowy nigdy nie jest celowo pomijany, aby przyjąć ofertę.
- Raport `.txt` jest UTF-8, zawiera kolejność i kwoty zgodne z DB, przedłużenia i poprawny wynik. Nie zawiera tokenów/haseł/IP.
- Eksport większy niż ramka jest przesyłany porcjami i sprawdzany sumą; wygasły/cudzy uchwyt nie ujawnia danych. Przerwany zapis nie jest komunikowany jako ukończony.
- Backup odtworzony do oddzielnej bazy daje tę samą liczbę kont/aukcji/ofert i poprawny przykładowy wynik.

## E. GUI i instalator

- Można wykonać rejestrację, publikację, wybór aukcji, licytację, sprawdzenie wyniku i eksport przez GUI.
- Podczas opóźnienia sieci okno się odświeża, daje przesuwać i obsługiwać; bez blokowania wątku JavaFX.
- Podwójne kliknięcie nie generuje dwóch różnych ofert podczas oczekiwania na pierwszą.
- Utrata połączenia, oczekiwanie na wynik i rzeczywiste odrzucenie są trzema różnymi stanami.
- Czytelny układ przy 1366×768 i skalowaniu 100/125/150%, klawiatura/fokus, polskie znaki, poprawne komunikaty minimum i czasu.
- Instalator działa na Windows bez zainstalowanego JDK, PostgreSQL i Dockera na komputerze klienta. Sprawdź wersję z dołączonym runtime, nie tylko uruchomienie przez Maven.
- Domyślna konfiguracja prowadzi do właściwego serwera bez wpisywania adresu/portu; deinstalacja nie usuwa cudzych plików.

Jeśli czysty Windows lub drugi komputer nie są dostępne, zapisz dokładnie ograniczenie testu. Nie utożsamiaj udanego `jpackage` z potwierdzeniem działania instalatora na czystym systemie.

## F. Scenariusz pokazu lokalnego

1. Uruchom bazę i serwer, trzy procesy klienta z trzema kontami. Jedno konto może mieć ADMIN; wszystkie mogą wystawiać cudzym licytującym.
2. Opublikuj dwie aukcje różnych właścicieli. Dla szybkiego pokazu pierwsza na około 90 sekund, druga na 3 minuty.
3. Dwaj użytkownicy licytują tę samą aukcję, a inny widok pokazuje drugą. Zademonstruj odrzucenie zbyt niskiej kwoty.
4. Przy czasie poniżej minuty przyjmij ofertę i pokaż dokładną zmianę. Wstrzymaj dalsze oferty, aby aukcja rzeczywiście dobiegła końca.
5. Rozłącz jeden klient, przywróć go i pokaż aktualny stan. Restart serwera sprawdź na drugiej aukcji z utrwaloną ofertą.
6. Pokaż wynik, zapisany raport i logi z obu źródeł. Wyjaśnij, które zadania miały własne wątki i jaki wspólny zasób chroni blokada.

## G. Obciążenie i granice

Pierwszy profil: 10 klientów bez GUI, 3 aukcje, 30 s, maks. 1 próba oferty/klient/s. Podnieś tylko tyle limitów, ile wymaga świadomie uruchomiony następny profil. Używaj syntetycznych kont i własnej bazy testowej; przerwij po przekroczeniu ustalonego progu pamięci/błędów.

Zapisz środowisko, liczbę przyjętych/odrzuconych ofert, medianę/p95 czasu odpowiedzi, CPU, zużycie pamięci procesów, połączenia DB, liczbę zadań i błędy. Odrzucenie zbyt niskiej oferty nie jest błędem infrastruktury. Nie deklaruj liczby obsługiwanych użytkowników wyłącznie na podstawie wartości `MAX_CLIENTS`.

Odbiór lokalny i odbiór publiczny zapisuj osobno. Publiczny wymaga instalatorów łączących się z rzeczywistym serwerem z różnych sieci; nie jest wykonywany w ramach samego przygotowania dokumentacji.

## H. Rozszerzenie 1.1 — polecenie z 11.11.2026

- Migracja rzeczywistych istniejących danych zachowuje konta/hasła, oferty, audyt, requestId i HMAC; numery użytkowników są proste i unikatowe.
- Każda nowa publikacja wymaga zdjęcia. JPG/PNG jest weryfikowane i przesyłane w ograniczonych porcjach; cudzy szkic/transfer jest niedostępny, zdjęcie po publikacji niezmienne.
- Zakończenie bez wywołania eksportu tworzy osobny trwały plik TXT, wpis raportu w bazie, zdarzenie diagnostyczne i rejestr ogólny. Utrata pliku jest naprawiana po restarcie.
- Panel ADMIN pokazuje faktycznie wykonywane zadania z ID wątków i podstawowe metryki. USER nie ma dostępu do operacji administracyjnej.
- Katalog i szczegóły pobierają ten sam obraz z serwera; historia pokazuje awatary; nagłówek tabeli nie ma szarego pustego pola. Instalator i okno używają ikony PK.

