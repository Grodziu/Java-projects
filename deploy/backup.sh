#!/usr/bin/env bash
set -euo pipefail
project=$(cd "$(dirname "$0")/.." && pwd)
install -d -m 700 /var/backups/bidroom
umask 077
target="/var/backups/bidroom/bidroom-$(date -u +%Y%m%d-%H%M%S).dump"
docker compose --env-file /etc/bidroom/server.env -f "$project/deploy/compose.production.yaml" exec -T postgres pg_dump -U bidroom -Fc bidroom > "$target.part"
test -s "$target.part"
mv "$target.part" "$target"
printf 'Zapisano %s\n' "$target"
