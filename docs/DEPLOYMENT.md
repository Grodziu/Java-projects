# Internet i demonstracja BidRoom 1.1

Wybór: VPS Linux x86-64, 2 vCPU, 4 GB RAM, około 40 GB dysku. Laptop pozostaje środowiskiem programowania. VPS utrzymuje aukcje po zamknięciu laptopa, nie wymaga wspólnej sieci, przekierowania portu domowego routera ani VPN. Jedna Java, jedna baza, jeden publiczny port TCP z TLS.

Przykład do zakupu przez właściciela: OVHcloud VPS-1, miesięczny okres rozliczenia, Ubuntu 24.04, lokalizacja w UE. Oficjalna [oferta VPS](https://www.ovhcloud.com/pl/vps/) obejmuje wariant 2 vCores / 4 GB / 40 GB; cenę i warunki promocji należy sprawdzić w podsumowaniu zamówienia. Nie zamówiono żadnej usługi. Nazwa DNS przydzielona przez dostawcę może wystarczyć, jeżeli wskazuje publiczny adres VPS i można uzyskać dla niej certyfikat. Własna domena nie jest warunkiem zakupu serwera.

## Rzeczywisty stan

Kod TCP/TLS i certyfikaty są testowane lokalnie. Nie ma jeszcze kupionego VPS, jego adresu ani dostępu administracyjnego. Nie wykonano wdrożenia publicznego ani próby z dwóch niezależnych sieci. Skrypty Linux wymagają odbioru na wybranym VPS; istnienie plików nie jest potwierdzeniem wdrożenia.

## Pakiet serwera

1. Na laptopie uruchom `scripts/build-server-package.ps1`. Powstaje `dist/BidRoom-serwer-1.1.1.zip`, bez haseł, lokalnej bazy i kont klientów.
2. Rozpakuj pakiet na VPS do `/opt/bidroom`. Zainstaluj Docker Engine z wtyczką Compose według [oficjalnej instrukcji](https://docs.docker.com/engine/install/ubuntu/) oraz `certbot` i `openssl` z repozytorium systemu.
3. Skonfiguruj zaporę dostawcy: SSH 22 dla administratora, TCP 80 do uzyskiwania/odnawiania certyfikatu i TCP 5001 dla aplikacji. PostgreSQL nie publikuje portu. Nie wyłączaj zapory.
4. Uruchom `sudo bash /opt/bidroom/deploy/prepare-vps.sh NAZWA_DNS_VPS`. Certbot pyta o warunki i kontakt. Skrypt tworzy losowe sekrety tylko na VPS, certyfikat PKCS12 i uruchamia kontenery. Hasło administratora znajduje się w `/etc/bidroom/server.env`; pliku nie wysyłaj uczestnikom.
5. Gotowość: `sudo docker compose --env-file /etc/bidroom/server.env -f /opt/bidroom/deploy/compose.production.yaml logs --tail 60 server`. Oczekiwany wpis: `BIDROOM_READY`. Limit JVM 256 MB, kontenera serwera 384 MB, bazy 512 MB; pierwszy profil 32 klientów / 16 aukcji. To limity konfiguracyjne, nie pomiar wydajności VPS.
6. Skopiuj `deploy/renew-tls.sh` jako wykonywalny hook `/etc/letsencrypt/renewal-hooks/deploy/bidroom`. Certbot przekazuje nazwę odnowionej domeny. `certbot renew --dry-run` sprawdza odnowienie. Restart serwera po odnowieniu chwilowo rozłącza klientów; po reconnect pobierają bieżący stan.
7. `sudo bash /opt/bidroom/deploy/backup.sh` zapisuje dump w `/var/backups/bidroom`. Zaplanuj go raz dziennie w systemowym timerze/cron. Kopię przenoś poza VPS. Zachowaj także prywatny plik konfiguracji, szczególnie trwały sekret HMAC. Obrazy przedmiotów są w bazie; brakujące raporty są odtwarzane po restarcie. Katalog raportów kontenera: `/var/lib/bidroom/reports` w wolumenie stanu.

## Instalator dla innych komputerów

Na laptopie: `powershell -NoProfile -ExecutionPolicy RemoteSigned -File scripts/build-installer.ps1 -Production -ServerHost NAZWA_DNS_VPS`. Wydanie zawiera docelowy adres oraz obowiązkowy TLS; użytkownik nie wpisuje adresu ani portu. Wynik zapisuje się w **`dist/internet/BidRoom-1.1.1.exe`**, osobno od lokalnego instalatora w `dist/`. Plik `client-profile.json` obok instalatora opisuje host, profil i SHA256. Wysłanie lokalnego instalatora z adresem `127.0.0.1` nie pozwala połączyć się z Twoim laptopem.

Przekaż wyłącznie internetowy `BidRoom-1.1.1.exe` z `dist/internet`. To instalator wraz z Javą/JavaFX. Sam `BidRoom.exe` wyjęty z katalogu zainstalowanego programu nie jest samodzielną aplikacją. Uczestnik instaluje klienta, rejestruje własne konto i uruchamia skrót BidRoom. Nie instaluje PostgreSQL i nie uruchamia skryptu serwera.

Odbiór: pierwszy klient na domowym Wi-Fi, drugi przez Internet mobilny lub w innym domu; wspólna aukcja, różne konta, jednoczesne oferty, aktualizacja ceny i czasu, reconnect, zakończenie oraz automatyczny raport. Dopiero ten test zalicza wymaganie różnych sieci.

## Przeniesienie istniejących danych

Zatrzymaj lokalny serwer po zakończeniu aukcji, wykonaj nowy `scripts/backup-db.ps1` i zachowaj obecny sekret HMAC. Odtwórz dump do pustej bazy VPS przed pierwszym normalnym uruchomieniem aplikacji; nie łącz automatycznie dwóch różnych baz. Przenieś bezpiecznie sekrety i kopię, nie publikuj ich w repozytorium. Zachowanie HMAC pozwala uzgodnić powtórzenia dawnych poleceń. Nie kopiuj aktywnego katalogu PostgreSQL ani lokalnych sesji klientów. Po migracji sprawdź liczby użytkowników, aukcji i ofert oraz login administratora.

Przy pracy na VPS nie uruchamiasz `BidRoom.cmd` ani `Stop-BidRoom.cmd` na laptopie. Uruchamiasz tylko klienta. Serwer i baza pracują niezależnie na VPS.
