#!/usr/bin/env bash
set -euo pipefail
host=${1:-${RENEWED_DOMAINS%% *}}
[[ $host =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])$ ]] || exit 1
config=/etc/bidroom/server.env
set -a
source "$config"
set +a
umask 077
openssl pkcs12 -export -in "/etc/letsencrypt/live/$host/fullchain.pem" -inkey "/etc/letsencrypt/live/$host/privkey.pem" -name bidroom -out /etc/bidroom/tls/server.p12.new -passout env:BIDROOM_TLS_KEYSTORE_PASSWORD
chown 10001:10001 /etc/bidroom/tls/server.p12.new
mv /etc/bidroom/tls/server.p12.new /etc/bidroom/tls/server.p12
project=${BIDROOM_DEPLOY_ROOT:-/opt/bidroom}
if docker compose --env-file "$config" -f "$project/deploy/compose.production.yaml" ps --status running --services | grep -qx server; then
  docker compose --env-file "$config" -f "$project/deploy/compose.production.yaml" restart server
fi
