#!/usr/bin/env bash
# Run on the chosen VPS, never on a client. Ubuntu 24.04 / Debian 12+ with Docker Compose.
set -euo pipefail
[[ $(id -u) == 0 ]] || { echo 'Uruchom przez sudo.' >&2; exit 1; }
host=${1:-}
[[ $host =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])$ && $host == *.* ]] || { echo 'Podaj publiczną nazwę DNS serwera.' >&2; exit 1; }
command -v docker >/dev/null && docker compose version >/dev/null
command -v openssl >/dev/null && command -v certbot >/dev/null
project=$(cd "$(dirname "$0")/.." && pwd)
[[ -f "$project/server/target/server-1.1.1.jar" ]] || { echo 'Brak zbudowanego server-1.1.1.jar.' >&2; exit 1; }
install -d -m 700 /etc/bidroom /var/backups/bidroom
install -d -m 755 /etc/bidroom/tls
config=/etc/bidroom/server.env
if [[ ! -e $config ]]; then
  umask 077
  {
    printf 'BIDROOM_SERVER_ENV_FILE=%s\nBIDROOM_TLS_CERT_DIR=/etc/bidroom/tls\n' "$config"
    printf 'BIDROOM_PUBLIC_HOST=%s\nBIDROOM_PUBLIC_PORT=5001\n' "$host"
    printf 'BIDROOM_POSTGRES_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'BIDROOM_DB_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'BIDROOM_COMMAND_HMAC_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'BIDROOM_TLS_KEYSTORE_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'BIDROOM_BOOTSTRAP_ADMIN_LOGIN=administrator\nBIDROOM_BOOTSTRAP_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'BIDROOM_DB_POOL_SIZE=4\nBIDROOM_MAX_CLIENTS=32\nBIDROOM_MAX_LIVE_AUCTIONS=16\n'
  } > "$config"
else
  echo 'Zachowano istniejące sekrety. Nie nadpisuję kont ani bazy.'
fi
if [[ ! -f /etc/letsencrypt/live/$host/fullchain.pem ]]; then
  echo 'Certbot uzyska certyfikat po potwierdzeniu warunków i podaniu kontaktu. DNS musi wskazywać ten VPS; port 80 musi być dostępny.'
  certbot certonly --standalone -d "$host"
fi
export BIDROOM_DEPLOY_ROOT="$project"
bash "$project/deploy/renew-tls.sh" "$host"
docker compose --env-file "$config" -f "$project/deploy/compose.production.yaml" config --quiet
docker compose --env-file "$config" -f "$project/deploy/compose.production.yaml" up -d --build
echo 'Serwer uruchomiony. Dane konta administratora: /etc/bidroom/server.env (tylko na VPS).'
echo 'Dodaj odnowienie certyfikatu i kopie według docs/DEPLOYMENT.md; sprawdź gotowość w logach.'
