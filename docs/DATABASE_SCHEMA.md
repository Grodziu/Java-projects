# Schemat bazy BidRoom 1.1

Otwórz [schemat SVG](database-schema.svg) — działa offline w przeglądarce i można go powiększać bez utraty jakości. Źródło struktury: migracje Flyway V001–V003. Poniżej ten sam model relacji w Mermaid.

```mermaid
erDiagram
    app_user ||--o{ auction : "owner_id / leader_id"
    app_user ||--o{ bid : bidder_id
    auction ||--o{ bid : auction_id
    auction ||--o{ auction_event : auction_id
    app_user o|--o{ auction_event : actor_id
    auction ||--o| auction_photo : auction_id
    auction ||--o| auction_report : auction_id
    app_user ||--o{ command_result : user_id
    app_user o|--o{ security_audit : "actor_id / target_user_id"
    app_user o|--o{ diagnostic_log : reported_by_user_id
    app_user {
        bigint id PK
        uuid legacy_uuid UK
        varchar login_normalized UK
        varchar display_name
        text role
        boolean enabled
    }
    auction {
        uuid id PK
        bigint auction_number UK
        bigint owner_id FK
        bigint leader_id FK
        text status
        bigint current_price
        bigint photo_version
        timestamptz end_at
    }
    auction_photo {
        uuid auction_id PK,FK
        bytea content
        bigint version
        text sha256
    }
    auction_report {
        uuid auction_id PK,FK
        text filename UK
        text sha256
        timestamptz generated_at
    }
```

`diagnostic_log.auction_id` pozostaje identyfikatorem zgłoszonym w logu, nie kluczem obcym: diagnostyka może dotyczyć odrzuconego żądania do nieistniejącej aukcji. Oferty, raporty i zdjęcia mają rzeczywiste FK. `flyway_schema_history` to dodatkowa tabela narzędzia migracji, pominięta na diagramie domeny.

Numer konta jest generowany przez PostgreSQL (`BIGINT IDENTITY`), więc nie trzeba odczytywać UUID użytkownika. Pole `legacy_uuid` zachowano tylko dla odzyskania oczekujących ofert z wersji 1.0. Aukcje mają czytelny `auction_number`, a trwały identyfikator techniczny UUID pozostaje bez zmiany.
