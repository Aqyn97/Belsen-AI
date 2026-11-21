#!/bin/sh
# Invoked by systemd with a protected EnvironmentFile, never source an untrusted file.
set -eu
exec /usr/bin/python3 /opt/belsen/scripts/production/backup.py run \
  --compose-file /opt/belsen/infra/production/compose.yaml \
  --env-file /etc/belsen/production.env \
  --project belsen-ai-production \
  --out-dir /var/lib/belsen-backups \
  --recipients-file /etc/belsen/backup-recipients.txt \
  --credentials-file /etc/belsen/r2-credentials \
  --endpoint "${BELSEN_R2_ENDPOINT:?Set the explicit account HTTPS endpoint}" \
  --bucket "${BELSEN_R2_BUCKET:?Set the dedicated private backup bucket}"
