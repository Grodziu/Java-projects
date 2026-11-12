# Interfejs Windows / JavaFX

## Kierunek wizualny

Materiały wejściowe: [kompozycja z granatowym tłem](reference/design/pk-layout-reference.png) oraz [kolorowy wzór techniczny](reference/design/pk-pattern.png). Oryginały zachowano; to referencje stylistyczne, a nie gotowy projekt każdego ekranu.

Interfejs ma przypominać identyfikację Politechniki Krakowskiej: mocny granat, biel, prosta typografia, spokojne powierzchnie i kolorowe akcenty inspirowane dziedzinami techniki. Nie pokrywaj całego ekranu aukcji szczegółowym wzorem — cyfry, czas i oferta muszą być czytelne. Nazwa robocza **BidRoom**, podpis „Projekt studencki • Politechnika Krakowska”. Nie przedstawiaj aplikacji jako oficjalnego systemu uczelni.

| Rola koloru | Propozycja |
|---|---|
| Główny granat | `#003571`, nawiązanie do oficjalnego SIW PK. |
| Tło aplikacji | `#F3F6FA`. |
| Powierzchnie kart | `#FFFFFF`. |
| Główny tekst | `#14243A`. |
| Tekst pomocniczy | `#516176`. |
| Akcent niebieski | `#007FAE`; jasny cyjan głównie dekoracyjny. |
| Sukces / prowadzenie | Ciemna zieleń, np. `#187447`. |
| Krótki czas | Ciemny pomarańcz; przy końcu czerwień oraz komunikat, nie sam kolor. |

Poza granatem są to propozycje projektu, nie deklaracja oficjalnej palety uczelni. Sprawdź kontrast konkretnych par. Kolorowe pasy kategorii i delikatne linie techniczne mogą nawiązywać do grafik; tło logowania lub wąski nagłówek może wykorzystać wybrany motyw. Używaj wyraźnej typografii dostępnej na Windows, np. Segoe UI, a nie pobierania fontu podczas startu.

JavaFX 21: FXML dla widoków, CSS dla tokenów kolorów/odstępów, kontrolery lub modele widoków oddzielone od transportu. Standardowe `ListView`, `TableView`, `Pagination`, `DatePicker`, właściwości i binding. Nie dodawaj dużej biblioteki UI tylko dla jednego przycisku. Ikony wektorowe lub proste zasoby lokalne; licencje zależności/assets odnotuj.

## Ekrany i przepływy

### 1. Logowanie / rejestracja

Panel marki i motyw PK, formularz login/hasło, przejście do rejestracji. Rejestracja: login, nazwa wyświetlana, hasło, powtórzenie. Jasna walidacja obok pola. Przy logowaniu komunikat bez ujawniania, czy istnieje login.

Połączenie automatyczne. Stan „Łączenie z serwerem…”, „Serwer chwilowo niedostępny” i przycisk ponowienia. Bez formularza adresu, portu, bazy, Dockera i certyfikatu. Błąd konfiguracji wydania ma komunikat użytkowy, a szczegóły techniczne trafiają do diagnostyki.

### 2. Główne okno

Lewy panel: Aukcje, Moje aukcje, Historia; dla ADMIN również Administracja. Nagłówek: nazwa ekranu, użytkownik, stan połączenia, wylogowanie. Widok startowy to katalog trwających aukcji, z zakładką zaplanowanych. Cena w punktach, bez symbolu PLN.

Karta aukcji: kategoria/grafika, tytuł, aktualna cena lub cena początkowa, czas, liczba ofert, przycisk „Otwórz aukcję”. Wyszukiwarka, filtr kategorii i paginacja. Puste stany mają czytelny komunikat. Zmiana ceny nie powinna przestawiać kart podczas czytania, chyba że użytkownik wybrał sortowanie po cenie.

### 3. Szczegóły aukcji

```text
Tytuł, kategoria                           [stan aukcji]
Opis i wystawiający              Aktualna oferta: 150 pkt
                                Pozostało: 00:42
Historia ofert                  Minimalna oferta: 160 pkt
  kolejność / osoba / kwota      [ kwota ] [Licytuj]
                                Prowadzisz / Prowadzi: …
```

Priorytet: cena, pozostały czas, własna możliwość licytowania. Kwota domyślnie minimalna, ręczna zmiana dostępna. Nie zmieniaj wpisywanej kwoty bez wiedzy użytkownika; po zmianie minimum pokaż nowe minimum i pozwól poprawić. Przy oczekiwaniu na wynik zablokuj ponowne kliknięcie tego polecenia.

Historia uporządkowana numerem oferty, stronicowana. Potwierdzenie pojawia się dopiero po odpowiedzi serwera. Informacja „Czas przedłużony” nie blokuje obsługi modalnym oknem. Zakończenie pokazuje zwycięzcę i cenę albo brak ofert; anulowanie pokazuje powód. Właściciel widzi informację, dlaczego nie może licytować.

### 4. Wystawianie

„Nowa aukcja”: tytuł, opis, kategoria, cena początkowa, przebicie, start teraz lub data/godzina, termin końca. Czas prezentowany z jawną strefą Europe/Warsaw; niejednoznaczną/nieistniejącą godzinę przy zmianie czasu obsłuż walidacją, nie cichym przesunięciem. W wersji 1.1 własne zdjęcie JPG/PNG jest wymagane przed publikacją. Starsze aukcje bez zdjęć mają jawnie oznaczoną ilustrację kategorii; nie udajemy, że to fotografia przedmiotu.

Szkic można zapisać, edytować i opublikować. Przed publikacją krótkie podsumowanie warunków. Po publikacji pola nie są edytowalne. Zajęty limit aukcji daje zrozumiały błąd bez utraty szkicu. Działania anulowania wymagają podania powodu i potwierdzenia konkretnej aukcji.

### 5. Historia i raport

Raport powstaje automatycznie na serwerze po każdej zakończonej/anulowanej aukcji. Archiwum, szczegóły wyniku, „Pobierz raport .txt”. Standardowe okno wyboru miejsca zapisu. Eksport działa w tle i sygnalizuje błąd/ukończenie. Nie proponuj jako raportu technicznego pliku logów.

### 6. Administracja

Domyślny panel stanu serwera odświeżany co 2 s: faktyczne zadania/wątki aplikacji, klienci, automaty, pamięć, CPU, połączenia JDBC, liczby rekordów i lokalizacja raportów. Lista kont, utworzenie konta, blokowanie/odblokowanie z powodem; lista aukcji z anulowaniem zgodnym z regułami; logi z filtrami po czasie, poziomie, źródle CLIENT/SERVER, użytkowniku i aukcji. Brak podglądu haseł i tokenów. Rozróżnij czas zdarzenia klienta od czasu otrzymania na serwerze. Audyt biznesowy i diagnostyka mogą mieć oddzielne zakładki.

## Zachowanie dynamiczne

- Wszystkie operacje sieciowe i plikowe poza wątkiem JavaFX. Na wątku UI wyłącznie aktualizacja modeli/kontrolek.
- Łącz częste aktualizacje w paczki; nie dodawaj nieograniczonej liczby `runLater` przy lawinie zdarzeń. Stosuj wersje snapshotów.
- Odliczanie obliczaj lokalnie z czasu serwera i upływu monotonicznego; koryguj po odpowiedziach/zdarzeniach. To orientacyjna prezentacja. Wysłanie w ostatniej widocznej sekundzie nie gwarantuje przyjęcia.
- Po rozłączeniu banner „Połączenie przerwane — przywracanie…”, brak nowych ofert. Niepotwierdzone wysłane polecenie ma osobny stan, nie znika jako „porażka”.
- Reconnect z rosnącym odstępem np. 1, 2, 4, 8 s, następnie maks. 15 s z małym losowym rozrzutem; ręczne ponowienie dostępne. Po odzyskaniu sesji pobierz świeży snapshot i historię.
- Dla nieaktualnych danych pokaż oznaczenie; lokalne dojście licznika do zera nie ustala zwycięzcy. Widok wyniku pochodzi z serwera.

## Minimalna jakość wizualna

Sprawdź ekran 1366×768 i skalowanie Windows 100%, 125%, 150%; okno skalowalne, zawartość przewijana zamiast ucinania przycisków. Obsługa klawiatury, widoczny fokus, podpisy pól, komunikaty niezależne od koloru. Rozmiar tekstu podstawowego około 14–16 px, regularne odstępy 8/16/24 px, spójne przyciski i promienie narożników. Bez długich animacji utrudniających licytowanie.

Zrzuty do sprawozdania wykonaj dopiero z prawdziwego programu: katalog, aukcja z historią i przedłużeniem, dwa równoczesne widoki, wynik, logi i raport. Makieta nie jest dowodem implementacji.

