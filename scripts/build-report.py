"""Build the Polish implementation report from source, screenshots and recorded results.
Requires reportlab; does not participate in the Java application build.
"""
from pathlib import Path
import hashlib
import json
import os
import textwrap
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Image, Preformatted

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output/pdf/BidRoom-sprawozdanie.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)
font_root = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts"
if font_root.exists():
    fonts = [font_root / "segoeui.ttf", font_root / "segoeuib.ttf", font_root / "consola.ttf"]
else:
    fonts = [Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf")]
for name, file in zip(["Body", "Bold", "Code"], fonts):
    pdfmetrics.registerFont(TTFont(name, str(file)))
NAVY = colors.HexColor("#003571")
styles = {
    "title": ParagraphStyle("title", fontName="Bold", fontSize=36, leading=43, textColor=NAVY, spaceAfter=15),
    "heading": ParagraphStyle("heading", fontName="Bold", fontSize=19, leading=25, textColor=NAVY, spaceAfter=16),
    "body": ParagraphStyle("body", fontName="Body", fontSize=10.5, leading=15, spaceAfter=10),
    "caption": ParagraphStyle("caption", fontName="Body", fontSize=8.5, leading=12, textColor=colors.HexColor("#516176"), spaceAfter=12),
    "cell": ParagraphStyle("cell", fontName="Body", fontSize=9, leading=12),
    "code": ParagraphStyle("code", fontName="Code", fontSize=7.3, leading=10, leftIndent=8, borderPadding=8, backColor=colors.HexColor("#F0F4F8"), spaceAfter=12),
}
story, md = [], ["# BidRoom - sprawozdanie z implementacji\n"]

def text(value, style="body"):
    value = value.replace("—", "-").replace("–", "-")
    story.append(Paragraph(escape(value), styles[style]))
    md.append(value + "\n")

def page(title):
    if story:
        story.append(PageBreak())
    story.append(Paragraph(escape(title), styles["heading"]))
    md.append("## " + title + "\n")

def table(rows, widths=None):
    formatted = [[Paragraph(escape(str(c)), styles["cell"]) for c in row] for row in rows]
    element = Table(formatted, colWidths=widths or [165, 346], hAlign="LEFT", repeatRows=1)
    element.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#E6EEF7")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 9), ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (-1, -1), 7), ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("LINEBELOW", (0, 0), (-1, -1), 0.4, colors.HexColor("#DCE5EF")),
    ]))
    story.extend([element, Spacer(1, 14)])
    md.append("| " + " | ".join(map(str, rows[0])) + " |")
    md.append("|" + "---|" * len(rows[0]))
    md.extend("| " + " | ".join(map(str, row)) + " |" for row in rows[1:])
    md.append("")

def screenshot(name, caption, max_height=335):
    path = ROOT / "docs/screenshots-1.1" / name
    pic = Image(str(path))
    scale = min(511 / pic.imageWidth, max_height / pic.imageHeight)
    pic.drawWidth, pic.drawHeight = pic.imageWidth * scale, pic.imageHeight * scale
    story.extend([pic, Spacer(1, 8)])
    text(caption, "caption")
    md.append(f"![{caption}](screenshots/{name})\n")

def code(path, begin, end):
    lines = (ROOT / path).read_text(encoding="utf-8").splitlines()
    first = next(i for i, line in enumerate(lines) if begin in line)
    last = next(i for i in range(first + 1, len(lines)) if end in lines[i])
    raw = textwrap.dedent("\n".join(lines[first:last]))
    wrapped = "\n".join(part for line in raw.splitlines() for part in (textwrap.wrap(line, 93, subsequent_indent="    ", replace_whitespace=False) or [""]))
    text(path + f" (od wiersza {first+1}; długie wiersze zawinięto)", "caption")
    story.append(Preformatted(wrapped, styles["code"]))
    md.append("```java\n" + raw + "\n```\n")

def data(name):
    return json.loads((ROOT / "docs/validation" / name).read_text(encoding="utf-8-sig"))

tests = data("tests-summary-1.1.json")
load = data("loadtest-result.json")
metrics = data("loadtest-summary.json")
backup = data("backup-restore.json")
recovery = data("database-recovery.json")
installer = data("installer-1.1.json")

text("BidRoom", "title")
text("Wieloużytkownikowy system aukcyjny w wirtualnych punktach", "heading")
text("Raport implementacji i weryfikacji lokalnej | wersja 1.1.0 | 11 listopada 2026", "caption")
text("Autorzy: do uzupełnienia przez trzyosobowy zespół. Nazwiska nie zostały przekazane i nie są zastępowane fikcyjnymi danymi.")
text("Zajęcia z Javy. Materiały źródłowe: dr inż. Sławomir Bąk, Politechnika Krakowska.")
text("Celem projektu jest poprawne rozstrzyganie współbieżnych ofert, utrwalanie wyników i prezentacja zmian w klientach JavaFX. Wdrożona wersja lokalna obejmuje konta, szkice, publikację, licytację, wyniki, raporty, administrację i logi. Nie obsługuje płatności ani rzeczywistej sprzedaży.")
table([["Część", "Rzeczywista implementacja"], ["common", "DTO, JSON, ramkowanie TCP, klient RPC i trwały bufor diagnostyki."], ["server", "Usługi domenowe, JDBC, migracje, sesje, automaty aukcji i serwer TCP/TLS."], ["client", "FXML, CSS, kontroler JavaFX, połączenie w tle i trwałe oczekujące oferty."], ["loadtest", "Oddzielny pomiar obciążenia, scenariusz demonstracji i próba utraty bazy."]])
text("Przepływ: klient JavaFX -> TCP (lokalnie) lub TLS (publicznie) -> serwer Java -> PostgreSQL. Klient nie zawiera sterownika JDBC ani hasła do bazy.")
text("Dokument opisuje lokalny wynik. Odbiór publiczny oraz test na drugim/czystym Windows pozostają niewykonane.", "caption")

page("1. Zadania, blokady i reguła czasu")
text("Wykład 1_Java, s. 10-13 i 24-25, przedstawia wykonawców, Callable i Future. W implementacji zadanie klienta oraz długowieczny automat każdej opublikowanej niezakończonej aukcji otrzymują osobne wątki wirtualne przez ExecutorService. Nie tworzymy klas extends Thread ani ręcznie uruchamianych wątków.")
code("server/src/main/java/pl/edu/bidroom/server/service/AuctionService.java", "    private final ExecutorService executor", "    private final Semaphore")
code("server/src/main/java/pl/edu/bidroom/server/service/AuctionService.java", "private final class AuctionLifecycleTask", "                } catch(InterruptedException")
text("Każda aukcja ma własny ReentrantLock i Condition. awaitNanos zwalnia blokadę podczas oczekiwania; po obudzeniu warunek jest sprawdzany ponownie. Nowa oferta sygnalizuje zmianę terminu. Nie blokuje to innej aukcji. Podstawa: wykład 1_Java, s. 72-76 i 87-88.")
table([["Pozostały czas przed ofertą", "Pozostały czas po przyjęciu"], ["90 s / dokładnie 60 s", "Bez zmiany"], ["59,999 s / 50 s", "60 s"], ["20 s / 5 s", "50 s / 35 s"], ["0 s lub mniej", "Oferta odrzucona"]])
text("Czas pochodzi z serwera i jest porównywany z precyzją mikrosekund. Wzór: nowy koniec = now + min(endAt - now + 30 s, 60 s), wyłącznie gdy 0 < endAt - now < 60 s. Zgodny duplikat polecenia zwraca zapisany wynik bez powtórzenia tej reguły.")

page("2. Trwałość, protokół i bezpieczeństwo")
text("Blokada Javy chroni kolejność czynności w procesie, ale nie zastępuje transakcji. Oferta, nowy termin, wersja, audyt i wynik polecenia są zatwierdzane razem. Snapshot w pamięci i odpowiedź o sukcesie powstają dopiero po commit. Każda operacja wypożycza osobne połączenie JDBC; pula ma limit 4. Podstawa: 3_Java_JDBC, s. 16, 18, 28 i 38-39.")
code("server/src/main/java/pl/edu/bidroom/server/persistence/Database.java", "    public <T> T transaction", "    public int activeConnections")
text("Idempotencja używa klucza (userId, requestId), blokady transakcyjnej PostgreSQL i HMAC kanonicznego DTO. Identyczna próba odzyskuje dawną odpowiedź również po restarcie i końcu aukcji. Inna treść z tym samym ID daje konflikt. Klient zapisuje ID, użytkownika, aukcję i kwotę na dysku przed wysłaniem; nie zapisuje tokena ani hasła.")
table([["Mechanizm", "Właściwość"], ["TCP", "4-bajtowa długość big-endian; 1-65 536 bajtów JSON UTF-8. Odczyt pełnej ramki z absolutnym limitem 10 s."], ["Kolejki", "Maks. 128 ramek i 1 MiB na serwerowego nadawcę; 16 MiB wspólnego budżetu. Wolny odbiorca jest odłączany."], ["Konta", "PBKDF2-HMAC-SHA256: 600 000 iteracji, losowa sól; kosztowne operacje ograniczone do dwóch równocześnie."], ["TLS", "Weryfikacja zaufania, nazwy hosta i ważności certyfikatu. Profil production nie przechodzi na jawny TCP."], ["Baza", "Dziewięć tabel domenowych/diagnostycznych, ograniczenia SQL, Flyway i parametryzowane zapytania."]])
text("TCP i Socket/ServerSocket omawia 2_Java_TCPUDP, s. 8, 29-32, 60 i 64. RMI jest materiałem porównawczym, bez drugiego transportu w projekcie.")
code("common/src/main/java/pl/edu/bidroom/common/FrameCodec.java", "    public static Wire read(InputStream", "    /** The frame deadline")

page("3. Rzeczywisty klient JavaFX")
text("Interfejs używa FXML i CSS, palety kolorów inspirowanej wydziałami PK, dostarczonego wzoru technicznego, kolorowych awatarów i polskich komunikatów. Sieć, baza i eksport nie pracują na wątku JavaFX. Zmiany kontrolek przechodzą przez Platform.runLater; wersje snapshotów chronią widok przed cofnięciem stanu. Podstawa: 5_Java_JavaFX, s. 3, 7, 12, 83 i 95-97.")
screenshot("04-oferta-przedluzenie.png", "Rys. 1. Przyjęta oferta, zdjęcie z serwera i przedłużony termin po operacji TCP/JDBC.")
text("Formularz pozwala tworzyć i edytować szkic, wybrać kategorię, cenę, przebicie oraz początek i czas trwania. Publikacja wymaga zdjęcia zapisanego na serwerze i podsumowania warunków. Po publikacji warunki są niezmienne. Administrator może anulować aktywną aukcję z powodem.")
text("Test GUI uruchamia prawdziwą scenę JavaFX i wywołuje kontrolki; nie jest makietą. Sprawdzono podwójne kliknięcie, ponowne połączenie, zakończenie, eksport i publikację przez formularz. Zrzuty w docs/screenshots-1.1 są osobnymi dowodami.")
code("client/src/main/java/pl/edu/bidroom/client/ui/MainController.java", "        int startedGeneration=generation;", "    private void message")

page("4. Wynik, raport i diagnostyka")
screenshot("05-wynik.png", "Rys. 2. Wynik aukcji po rzeczywistym upływie czasu. O zwycięzcy zdecydował serwer.", 310)
text("Raport TXT powstaje automatycznie po każdej terminalnej aukcji, z jednego obrazu PostgreSQL w REPEATABLE READ. Trwały plik ma nazwę aukcja-NR-tytul.txt; rejestr wszystkich raportów to raport-ogolny.txt, a baza zapisuje zdarzenie REPORT_GENERATED. Pobranie raportu nie usuwa oryginału. Restart odtwarza brakujące pliki. Zawiera kolejność ofert, przyjęte kwoty, terminy, przedłużenia i końcowy wynik. Porcje mają najwyżej 16 KiB przed base64; klient sprawdza liczbę bajtów oraz SHA-256.")
text("Przykład w docs/screenshots-1.1/przykladowy-raport.txt pochodzi z testu GUI. Aukcja zakończyła się jedną zaakceptowaną ofertą 150 pkt. Zwycięzca oraz termin są zgodne z zapisem serwera.")
text("Logi serwera i klienta trafiają do diagnostic_log. Identyfikatory zdarzeń umożliwiają deduplikację; ACK oznacza zapis w bazie. Trwały bufor jest ograniczony do 5000 rekordów i 10 MiB. Audyt biznesowy pozostaje w transakcji, a deklaracje klienta są wyraźnie oznaczone w panelu administratora.")
text("Eksport danych i logi nie zawierają haseł, tokenów ani pełnych żądań uwierzytelnienia. Pliki aktywnej bazy i konfiguracja sekretów znajdują się poza OneDrive.")

page("5. Testy funkcjonalne i współbieżność")
text(f"Końcowy przebieg Maven: {int(tests['tests'])} testów, {int(tests['failures'])} niepowodzeń, {int(tests['errors'])} błędów, {int(tests['skipped'])} pominiętych. Testy integracyjne używają prawdziwego PostgreSQL 17.11 w bidroom_test; nie używają H2 ani SQLite.")
rows = [["Zestaw", "Liczba testów"]]
for suite in tests["suites"]:
    rows.append([suite["name"].rsplit(".", 1)[-1], str(int(suite["tests"]))])
table(rows)
text("Sprawdzono m.in. równoczesne oferty tej samej kwoty, niezależne blokady dwóch aukcji, duplikat na dwóch socketach i po restarcie, konflikt treści ID, błąd SQL z rollback, anulowanie w wyścigu z ofertą, limit i zwalnianie automatów, konkurencyjną rejestrację i ochronę ostatniego administratora.")
text("Dodatkowe próby obejmują utratę odpowiedzi po commit, utratę blokady instancji, odrzucanie wadliwych ramek, zamknięcie nieobsługiwanej wersji, wolnego odbiorcę, wygasanie sesji i raport z 1050 ofert. TLS sprawdzono dla poprawnego, niezaufanego, wygasłego certyfikatu i błędnej nazwy hosta.")
text("Niepewny wynik JDBC sprawdzono przez testowe proxy TCP na loopback. PostgreSQL rzeczywiście zatwierdził ofertę; proxy przechwyciło potwierdzenie COMMIT i zerwało połączenie przed JDBC. Serwer wstrzymał obsługę. Odczyt niezależnym połączeniem, restart i replay potwierdziły jeden bid, jedno przedłużenie i identyczny trwały wynik, również po końcu aukcji.")
text("Test dużego raportu wykrył zbyt niski limit tekstu JSON: 16 KiB danych zamienia się w 21 848 znaków base64. Limit parsera zwiększono do 32 768 znaków, zachowując 64 KiB ramki i mniejsze limity pól biznesowych. Wcześniejsze nieudane przebiegi nie są liczone jako sukces.")
text("Osobna próba zatrzymania prywatnego PostgreSQL odłączyła klienta; po uruchomieniu bazy supervisor wznowił serwer. Odtworzono identyczny wynik polecenia, jedną ofertę i ten sam termin. Nie symulowano awarii zasilania ani uszkodzenia dysku.")

page("6. Pomiar i pokaz wielu klientów")
text("Pomiar poniżej pochodzi z wersji 1.0, przed dodaniem zdjęć i automatycznych raportów. Nie jest pomiarem wydajności 1.1 ani VPS.", "caption")
text("Środowisko: Windows x64, Intel Core Ultra 5 125H (18 procesorów logicznych), 15,4 GiB widocznego RAM. JDK/JavaFX 21.0.13; serwer -Xmx256m, klient -Xmx192m, Maven -Xmx384m; PostgreSQL shared_buffers=32MB. Budowanie i cięższe próby wykonywano kolejno.")
table([["Pomiar 10 klientów / 3 aukcje / 30 s", "Wynik"], ["Próby ofert", str(load["accepted"] + load["businessRejected"])], ["Przyjęte / odrzucone biznesowo", f"{load['accepted']} / {load['businessRejected']}"], ["Błędy infrastruktury", str(load["infrastructureErrors"])], ["Mediana / p95 odpowiedzi", f"{load['medianMs']:.2f} / {load['p95Ms']:.2f} ms"], ["Szczyt working set serwera", f"{metrics['serverPeakWorkingSetMiB']} MiB"], ["Szczyt working set generatora", f"{metrics['driverPeakWorkingSetMiB']} MiB"], ["Średnie CPU serwera (jeden rdzeń = 100%)", f"{metrics['serverMeanCpuPercentOneCore']}%"], ["Obserwowane klienty / automaty", f"{int(metrics['peakClients'])} / {int(metrics['peakLiveAuctionTasks'])}"], ["Próbkowane aktywne JDBC / limit puli", f"{int(metrics['sampledPeakBusyJdbcConnections'])} / 4 + osobna blokada instancji"]])
text("Jeden dodatkowy klient był wystawiającym. Pomiary procesu wykonywano co około sekundę, przez 43,09 s łącznie z przygotowaniem kont. Próbkowanie może ominąć chwilowe piki. Heap JVM nie oznacza całej pamięci procesu. Wynik opisuje wyłącznie ten profil laptopowy, nie maksymalną liczbę użytkowników.")
text("Pokaz uruchomił trzy osobne procesy JavaFX. Dwie osoby wysłały ofertę 150 pkt na tę samą aukcję: zapisano jeden bid i zgodny snapshot w obu oknach. Trzecie okno pokazywało drugą aukcję. Zrzuty oraz PID-y zapisano w docs/screenshots/demo; procesy po pokazie zamknięto.")

page("7. Dystrybucja i odtwarzanie danych")
text("jlink przygotowuje runtime, a jpackage i WiX 3.14.1 tworzą instalator EXE dla bieżącego użytkownika. Klient zawiera JavaFX i potrzebne biblioteki. Instalator lokalny wskazuje 127.0.0.1:5001 bez formularza adresu i portu.")
table([["Sprawdzenie", "Rzeczywisty wynik"], ["Instalator", installer.get("result", "Sprawdź docs/validation/installer-1.1.json")], ["SHA-256 EXE", installer.get("sha256", "")], ["Kopia bazy (próba 1.0)", f"{backup['source']['users']} kont, {backup['source']['auctions']} aukcji, {backup['source']['bids']} ofert"], ["Odtworzenie (próba 1.0)", "Nowa bidroom_restore_test; zgodne liczniki i skrót utrwalonych wyników. Migracja 1.1: rozdział 9."], ["Powrót po zatrzymaniu bazy", "Tak" if recovery.get("supervisorRecovered", False) else "Niepotwierdzony"]])
text("Skrypty umieszczają narzędzia, bazę i sekrety w USERPROFILE/BidRoomData, poza OneDrive i przekierowywanym AppData. Zainstalowany klient zapisuje swój bufor w LOCALAPPDATA/BidRoom/client. Nie zmienia się globalnego PATH, ustawień WSL ani zapory. Repozytorium pozostaje w pierwotnym folderze; nie zawiera sekretów ani aktywnej bazy.")
text("Przygotowano konfiguracje Linux/systemd oraz Compose z prywatną bazą, limitami pamięci i TLS. Nie były uruchamiane na rzeczywistym Linux ani serwerze publicznym. Wybrano wariant VPS Linux 2 vCPU / 4 GB RAM i przygotowano pakiet wdrożenia. Zakup/dostęp do hosta, certyfikat oraz test dwóch urządzeń z różnych sieci to nadal etap R14.")
text("Na tym laptopie sprawdza się pakiet z dołączoną Javą, ale zainstalowane narzędzia systemowe nadal istnieją. Test przy wyczyszczonym JAVA_HOME/PATH nie jest równoważny testowi czystego Windows. Podpisywanie kodu nie zostało wykonane.")

page("8. Uruchomienie, wnioski i granice odbioru")
text("Na przygotowanym laptopie plik BidRoom.cmd uruchamia bazę, serwer i klienta. Po zamknięciu okna Stop-BidRoom.cmd zatrzymuje usługi. Lokalne skrypty uruchamiane są w procesie Windows PowerShell z RemoteSigned; globalne zasady pozostają bez zmian. Przy przygotowaniu nowego komputera polecenia PowerShell wykonuje się w katalogu projektu:")
commands = "./scripts/setup-tools.ps1\n./scripts/start-db.ps1\n. ./scripts/env.ps1\n./mvnw.cmd -B -ntp package\n./scripts/start-server.ps1\n./scripts/start-client.ps1"
story.append(Preformatted(commands, styles["code"]))
md.append("```powershell\n" + commands + "\n```\n")
text("Scenariusz prezentacji: rejestracja, dwie aukcje, równoczesne oferty, pokaz przedłużenia, koniec bez kolejnych ofert, wynik i TXT, panel logów, reconnect i odtworzenie serwera. scripts/demo.ps1 przygotowuje świeże konta i trzy procesy; scripts/test-gui.ps1 sprawdza pełny przepływ na nowych danych.")
text("Najważniejszy wniosek: poprawność wynika ze współpracy blokady per aukcja, transakcji i trwałego wyniku polecenia. Wątek wirtualny upraszcza oczekiwanie, lecz nie usuwa potrzeby limitów połączeń, pamięci, kolejek i kosztu haszowania.")
text("Do pełnego odbioru pozostają: prawdziwe nazwiska autorów, drugi/czysty Windows, publiczny host/domena/certyfikat i różne sieci. Nie przeprowadzono fizycznego zapełnienia dysku ani zaniku zasilania. Nie sprawdzono wszystkich kombinacji klawiatury/fokusu i wielu monitorów. Nie deklaruje się nieograniczonej skali ani pełnego pokrycia wszystkich kombinacji awarii.")
text("Źródła i mapowanie do kodu", "heading")
text("S. Bąk: 1_Java.pdf, s. 10-13, 24-25, 72-76, 87-88, 115-116; 2_Java_TCPUDP.pdf, s. 8, 29-32, 60, 64; 3_Java_JDBC.pdf, s. 16, 18, 28, 38-39; 5_Java_JavaFX.pdf, s. 3, 7, 12, 83, 95-97. Kopie materiałów: docs/reference/lectures. Referencje wizualne: docs/reference/design.", "caption")
text("Dokumentacja uzupełniająca: docs.oracle.com/en/java/javase/21/ (Executors, Condition, JSSE, jpackage); openjfx.io/javadoc/21/; jdbc.postgresql.org/documentation/; postgresql.org/docs/17/; cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html. Pełne adresy i licencje: docs/REFERENCES.md i docs/THIRD_PARTY.md.", "caption")
text("Specyfikacja i dowody: docs/REQUIREMENTS.md, PROTOCOL.md, DATABASE.md, ACCEPTANCE.md, VALIDATION_RESULTS.md, validation/*.json oraz zrzuty. Fragmenty kodu w tym raporcie są odczytywane przez generator bezpośrednio ze źródeł. Kryteria niewykonane pozostają otwarte.", "caption")

page("9. Rozszerzenie 1.1 i zachowanie danych")
text("Wersja 1.1 zastępuje UUID kont prostymi numerami BIGINT IDENTITY. Osobna próba migracji na kopii potwierdziła zgodność kont, hashy haseł, aukcji, ofert, audytu, requestId i HMAC. Następnie zaktualizowano rzeczywistą bazę: 34 konta, 32 aukcje, 8 ofert, bez utraty tych danych. Przed zmianą powstała świeża kopia.")
text("Zdjęcia JPG/PNG są przesyłane przez TCP/TLS w porcjach 16 KiB, sprawdzane i normalizowane przez serwer. Dane BYTEA znajdują się w auction_photo z FK do aukcji. Nowa publikacja bez zdjęcia jest odrzucana; stare aukcje bez fotografii pokazują jawnie oznaczoną ilustrację. Awatar głowy i ramion jest wyliczany z numeru konta i palety PK.")
screenshot("09-panel-serwera.png", "Rys. 3. Rzeczywisty panel administratora: wątki zadań, połączenia, CPU i pamięć. Metryki pochodzą z izolowanego serwera testowego.", 330)
text("TaskRegistry rejestruje wejście i wyjście z Callable, identyfikator rzeczywistego wątku i rolę zadania. Nie utożsamia liczby wątków platformowych JVM z liczbą wątków wirtualnych. Start i koniec trafiają do diagnostic_log. Widok administracyjny odświeża się co 2 sekundy, poza wątkiem JavaFX.")
text("Schemat bazy: docs/database-schema.svg i docs/DATABASE_SCHEMA.md. Instrukcje podglądu rekordów, raportów, logów i uruchomienia: docs/RUNNING.md. Wdrożenie publiczne i pakiet VPS: docs/DEPLOYMENT.md. Rzeczywistego hostingu ani odbioru różnych sieci jeszcze nie wykonano.")

def furniture(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(NAVY)
    canvas.setLineWidth(1)
    canvas.line(42, 40, A4[0] - 42, 40)
    canvas.setFont("Body", 8)
    canvas.setFillColor(colors.HexColor("#516176"))
    canvas.drawString(42, 26, "BIDROOM / 1.1.0 / weryfikacja lokalna")
    canvas.drawRightString(A4[0] - 42, 26, str(doc.page))
    canvas.restoreState()

SimpleDocTemplate(str(OUT), pagesize=A4, leftMargin=42, rightMargin=42, topMargin=40, bottomMargin=55, title="BidRoom - implementacja i weryfikacja lokalna", author="Zespół BidRoom - nazwiska do uzupełnienia").build(story, onFirstPage=furniture, onLaterPages=furniture)
(ROOT / "docs/SPRAWOZDANIE.md").write_text("\n".join(md), encoding="utf-8")
print(OUT)
print("SHA256", hashlib.sha256(OUT.read_bytes()).hexdigest())
