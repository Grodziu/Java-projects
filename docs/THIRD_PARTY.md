# Zależności i zasoby

Wersja aplikacji 1.1.1. Dokładne wersje bibliotek są w nadrzędnym `pom.xml`.

| Składnik | Licencja / źródło |
|---|---|
| Eclipse Temurin JDK 21.0.13 | GPLv2 z Classpath Exception; [Adoptium](https://adoptium.net/temurin/releases/?version=21) |
| JavaFX 21.0.13 | GPLv2 z Classpath Exception; [OpenJFX](https://openjfx.io/) |
| Jackson 2.22.4 | Apache 2.0; [FasterXML](https://github.com/FasterXML/jackson) |
| PostgreSQL JDBC 42.7.14 | BSD-2-Clause; [pgjdbc](https://jdbc.postgresql.org/) |
| HikariCP 7.0.2 | Apache 2.0; [HikariCP](https://github.com/brettwooldridge/HikariCP) |
| Flyway 13.12.1 | Apache 2.0; [Flyway](https://github.com/flyway/flyway) |
| SLF4J 2.0.17 | MIT; [SLF4J](https://www.slf4j.org/) |
| JUnit 6.1.3 | EPL 2.0; [JUnit](https://junit.org/) |
| Apache Maven 3.9.16 / Wrapper 3.3.4 | Apache 2.0; [Maven](https://maven.apache.org/) |
| WiX 3.14.1 | Microsoft Reciprocal License; [WiX](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm) |
| PostgreSQL 17.11 | PostgreSQL License; [PostgreSQL](https://www.postgresql.org/about/licence/) |

Klient zawiera tylko JavaFX, Jackson, moduł common i runtime. Licencje bibliotek pozostają w dystrybuowanych plikach JAR oraz katalogu runtime/legal. Serwer dodatkowo korzysta z zależności JDBC i migracji. JUnit i Maven są narzędziami budowania/testów, nie zależnościami klienta.

Interfejs to własny układ FXML/CSS i wektorowe awatary/ilustracje kategorii. Dostarczony przez użytkownika wzór `docs/reference/design/pk-pattern.png` jest dołączony jako lokalny element kompozycji. Ikona aplikacji pochodzi z [oficjalnej witryny PK](https://pk.edu.pl/wp-content/uploads/2026/07/cropped-PK-192x192.png), pobrana 11.11.2026; plik ICO zawiera niezmienione piksele tego PNG. Znaki i grafiki PK nie są objęte licencjami bibliotek. Materiały dydaktyczne i wykłady nie są kopiowane do instalatora. To projekt studencki, nie oficjalny system uczelni.
