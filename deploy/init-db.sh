#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=app_password="$BIDROOM_DB_PASSWORD" --set=ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE bidroom LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
ALTER DATABASE bidroom OWNER TO bidroom;
GRANT USAGE, CREATE ON SCHEMA public TO bidroom;
SQL
