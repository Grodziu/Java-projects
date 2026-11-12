# Wymagania i reguły biznesowe

## 1. Cel

BidRoom to symulator licytacji w wirtualnych punktach, realizowany przez trzyosobowy zespół na zajęcia z Javy. Ma pozwalać wielu użytkownikom licytować wiele przedmiotów jednocześnie i demonstrować rzeczywistą synchronizację wspólnego stanu. Autor materiałów: dr inż. Sławomir Bąk.

Podstawą są wymagania przekazane w rozmowie i [wykłady](REFERENCES.md). Specyfikacja wymaga wielu aukcji, PostgreSQL i samodzielnej rejestracji. Szczegóły niewskazane wprost przez użytkownika przyjęto jako domyślne decyzje projektowe, opisane w [rejestrze decyzji](DECISIONS.md).

## 2. Wymagania obowiązkowe

| ID | Wymaganie |
|---|---|
| R01 | Implementacja obiektowa w Javie, z sensownym modelem domenowym. |
| R02 | `java.util.concurrent` jako podstawa współbieżności. |
| R03 | Każdy klient obsługiwany w osobnym wątku; osobny wątek automatu każdej opublikowanej niezakończonej aukcji. |
| R04 | Wiele jednoczesnych aukcji i użytkowników; konfigurowalne limity sprzętowe. |
| R05 | Poprawna synchronizacja ofert, zamknięcia i przedłużania aukcji. |
| R06 | Architektura klient–serwer i komunikacja TCP/IP. |
| R07 | Graficzny klient JavaFX na Windows. |
| R08 | Odczyt/zapis PostgreSQL wyłącznie przez serwer. |
| R09 | Zdarzenia serwera i klienta zapisywane w bazie. |
| R10 | Eksport wyników działania do pliku tekstowego `.txt`. |
| R11 | Samodzielna rejestracja, logowanie oraz dodatkowe uprawnienia administratora. |
| R12 | Przywracanie stanu po ponownym połączeniu i restarcie serwera. |
| R13 | Instalator Windows z runtime i domyślnym adresem usługi. |
| R14 | Docelowy dostęp z różnych sieci przez Internet, bez wspólnego hotspotu i dodatkowej konfiguracji uczestnika. |
| R15 | Wygląd inspirowany dostarczoną identyfikacją Politechniki Krakowskiej. |
| R16 | Sprawozdanie: temat, autorzy, teoria, implementacja z kodem, przykłady ze zrzutami, wnioski. |

GUI, baza i raport są obowiązkowe mimo wcześniejszej transkrypcji opisującej dodatkowe punkty. Nie obiecuj konkretnej oceny.

## 3. Użytkownicy i uprawnienia

Role systemowe: `USER` i `ADMIN`. Wystawiający/opiekun jest relacją użytkownika do konkretnej aukcji, a nie drugim kontem. Każdy `USER` może tworzyć aukcje i licytować cudze. `ADMIN` ma również uprawnienia zwykłego użytkownika; zakaz licytowania własnej aukcji dotyczy także administratora.

| Czynność | Użytkownik | Wystawiający daną aukcję | Administrator |
|---|---|---|---|
| Rejestracja własnego konta | Tak | Tak | Tak |
| Przegląd opublikowanych aukcji/historii | Tak | Tak | Tak |
| Utworzenie szkicu | Tak | Tak | Tak |
| Edycja/publikacja własnego szkicu | — | Tak | Tak |
| Edycja opublikowanych warunków | Nie | Nie | Nie; używa anulowania z powodem |
| Licytowanie | Cudze aukcje | Nie własnej | Cudze aukcje |
| Anulowanie szkicu/zaplanowanej własnej aukcji | — | Tak | Tak |
| Anulowanie aktywnej aukcji | Nie | Nie | Tak, wymagany powód |
| Eksport publicznego wyniku zakończonej aukcji | Tak | Tak | Tak |
| Tworzenie innych kont, blokowanie użytkownika, logi | Nie | Nie | Tak |

Administrator nie może zablokować/usunąć ostatniego aktywnego administratora. Blokada konta kończy jego sesje i uniemożliwia nowe działania; wcześniej poprawnie zapisanych ofert nie usuwa. To projektowe decyzje domyślne, opisane w `DECISIONS.md`.

## 4. Konta

- Samodzielna rejestracja: login, nazwa wyświetlana, hasło i powtórzenie hasła.
- Login 3–32 znaki ASCII `[A-Za-z0-9._-]`, unikatowy bez względu na wielkość liter; nazwa wyświetlana 2–50 znaków, może zawierać polskie znaki.
- Hasło 12–128 znaków, dozwolone spacje; brak obowiązku kategorii znaków. Hasła przechowywane wyłącznie jako bezpieczny hash z losową solą.
- E-mail, weryfikacja e-mail, odzyskiwanie hasła i zewnętrzne logowanie nie są wymagane w pierwszej wersji.
- Rejestracja nigdy nie nadaje roli administratora na podstawie pola wysłanego przez klienta.
- Pierwszy administrator inicjalizowany przez konfigurację serwera poza instalatorem klienta. Brak fabrycznego hasła produkcyjnego.

## 5. Aukcja

Pola: identyfikator, tytuł, opis, kategoria, właściciel, cena początkowa, minimalne przebicie, początek, pierwotny termin końca, aktualny termin końca, stan, lider, najwyższa oferta, liczba ofert, wersja stanu. Kategorie startowe: Elektronika, Nauka i książki, Akcesoria, Inne. Od wersji 1.1 zdjęcie jest wymagane przed nową publikacją; grafiki kategorii pozostają wyłącznie oznaczonym zastępstwem dla dawnych aukcji bez fotografii.

Stan: `DRAFT → SCHEDULED → ACTIVE → FINISHED`; ze szkicu można publikować od razu do `ACTIVE`. Anulowanie prowadzi do `CANCELLED`. `FINISHED` i `CANCELLED` są końcowe. Nie otwieraj ich ponownie.

- Szkic widzą właściciel i administrator. Katalog pokazuje aukcje zaplanowane i aktywne; archiwum osobno. Anulowanie nigdy nieopublikowanego szkicu nie czyni go publicznym — zapamiętaj, czy aukcja była opublikowana.
- Początek może być natychmiastowy albo przyszły. Początkowy czas trwania: domyślnie 5 minut, zakres 1 minuta–7 dni.
- Wszystkie ceny to całkowite punkty (`long` / SQL `BIGINT`), zakres 1–1 000 000 000 000. Nie używaj `float`/`double` dla kwot.
- Nie ma salda użytkownika, blokowania środków, płatności ani wysyłki towarów.
- Pierwsza oferta musi być co najmniej równa cenie początkowej. Kolejna musi osiągnąć najwyższą ofertę + minimalne przebicie; może być wyższa. Sprawdzaj przepełnienie dodawania.
- Użytkownik może podwyższyć swoją prowadzącą ofertę; obowiązują te same warunki. Nie może licytować własnej aukcji.
- Dwie równoczesne oferty tej samej kwoty mogą skutkować tylko jednym przyjęciem. Kolejność rozstrzyga serwer przy obsłudze operacji, nie czas kliknięcia u klienta.
- Wygrywa właściciel najwyższej zaakceptowanej oferty. Bez ofert aukcja kończy się bez zwycięzcy i bez ceny sprzedaży.
- Wyjście opiekuna lub licytującego z aplikacji nie zatrzymuje aukcji.
- Tytuł, opis, terminy, cena początkowa i przebicie po publikacji są niezmienne. Wyjątkiem jest automatyczne przedłużenie końca.

## 6. Przedłużenie aukcji — dokładna definicja

`now` to czas decyzji odczytany na serwerze po uzyskaniu ochrony aktualnego stanu aukcji. `endAt` to aktualny, także wcześniej przedłużony termin. Poniższe reguły dotyczą nowego polecenia: zgodne powtórzenie wcześniej rozstrzygniętego polecenia zwraca jego zapisany wynik przed ponowną oceną ceny i terminu, także po zakończeniu aukcji.

1. Jeśli `now >= endAt`, odrzuć ofertę, nawet jeżeli automat jeszcze nie zapisał stanu `FINISHED`.
2. Zweryfikuj cenę, tożsamość, właściciela, stan i idempotencję żądania.
3. Jeśli poprawna oferta jest przyjmowana i `0 < endAt - now < 60 s`, oblicz:

   `newEndAt = min(endAt + 30 s, now + 60 s)`

4. W innych przypadkach pozostaw `endAt`.
5. Zapisz ofertę, nowy termin, wersję, audyt i wynik polecenia w jednej transakcji.
6. Po commit uaktualnij stan w pamięci, obudź automat i powiadom klientów.

| Pozostało | Po poprawnej ofercie |
|---|---|
| 90 s | 90 s |
| dokładnie 60 s | 60 s |
| 50 s | 60 s |
| 20 s | 50 s |
| 5 s | 35 s |
| 0 s lub mniej | Odrzucenie |

Nie zaokrąglaj czasu do sekund przed sprawdzeniem reguły. 59,999 s mieści się w przedziale. Limit 60 s dotyczy pozostałego czasu po każdym podbiciu, **nie sumy przedłużeń**. Ciągłe oferty mogą podtrzymywać aukcję. Odrzucone i zdublowane żądania nie przedłużają czasu.

Oferta uznana za dopuszczalną przed terminem może zakończyć commit po nim; zamknięcie czeka na tę samą blokadę. Samo wcześniejsze dotarcie pakietu do systemu nie gwarantuje przyjęcia.

## 7. Ponowne połączenie i awarie

- Po utracie połączenia GUI pozostaje responsywne, pokazuje stan offline i nie przyjmuje nowych ofert do późniejszej wysyłki.
- Dla już wysłanej, niepotwierdzonej oferty zachowaj jej `requestId`. Po ponownym uwierzytelnieniu odpytaj wynik albo ponów tę samą operację z tym samym ID i treścią.
- Po połączeniu pobierz świeży stan katalogu/otwartej aukcji i wznow subskrypcję. Nie polegaj na odebraniu wszystkich starych powiadomień.
- Po restarcie serwera odtwórz dane z PostgreSQL. Przeterminowane aukcje zakończ na podstawie ofert zapisanych przed awarią. Nie przyznawaj automatycznej rekompensaty czasowej.
- Przy awarii bazy nie ogłaszaj nieutrwalonych ofert jako przyjętych. Wyświetl chwilową niedostępność; po odzyskaniu bazy uzgodnij stan.

## 8. Raport i logi

Raport UTF-8 `.txt`: nazwa i ID aukcji, wystawiający, stan, terminy pierwotny i końcowy, historia zaakceptowanych ofert z kolejnością i czasem, przedłużenia, wynik, zwycięzca lub brak, powód anulowania, data wygenerowania. Bez haseł, tokenów, IP uczestników i prywatnych logów.

Serwer generuje treść z utrwalonych danych; klient wybiera lokalne miejsce zapisu. Eksport zakończonych/anulowanych aukcji. Logi obejmują również zdarzenia klienta, ale nie rejestrują każdego znaku hasła ani każdego odświeżenia zegara.

## 9. Sprawozdanie i pokaz

Sprawozdanie ma zawierać wszystkie elementy z R16. Nazwiska pozostają do uzupełnienia przez zespół; nie wpisuj zmyślonych. Fragmenty kodu i zrzuty mają pochodzić z rzeczywistej wersji projektu. Wnioski obejmują pomiary obciążenia, granice rozwiązania i odróżnienie współbieżności od równoległości.

Pokaz obejmuje co najmniej dwie równoległe aukcje i trzech klientów, wyścig ofert, przedłużenie, zakończenie, raport, widok logów klienta i serwera, ponowne połączenie oraz trwałość po restarcie.
