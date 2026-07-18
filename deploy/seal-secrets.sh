#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${DEPLOY_DIR}/.." && pwd)"
SRC="${RA_SEAL_SOURCE:-}"
if [[ -z "$SRC" ]]; then
  if [[ -f "${DEPLOY_DIR}/secrets-cluster.env" ]]; then
    SRC="${DEPLOY_DIR}/secrets-cluster.env"
  else
    SRC="${ROOT_DIR}/secrets.env"
  fi
fi
# Het sealed-secrets public cert leeft in robberts-infrastructure (gedeeld met
# alle apps op het cluster); niet lokaal kopiëren, want de sealed-secrets-key
# roteert periodiek en een lokale kopie veroudert dan stil.
CERT="${DEPLOY_DIR}/../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem"
OUT="${DEPLOY_DIR}/base/sealed-secret-robberts-assistent.yaml"
NAMESPACE="${RA_NAMESPACE:-robberts-assistent}"
SECRET_NAME="${RA_SECRET_NAME:-robberts-assistent-secrets}"

if ! command -v kubeseal >/dev/null 2>&1; then
  echo "Error: kubeseal niet gevonden in PATH." >&2
  exit 1
fi

if [[ ! -f "$SRC" ]]; then
  echo "Error: secret source bestaat niet: $SRC" >&2
  echo "Maak root secrets.env of deploy/secrets-cluster.env aan." >&2
  exit 1
fi

if [[ ! -f "$CERT" ]]; then
  echo "Error: $CERT bestaat niet." >&2
  echo "Verwacht robberts-infrastructure als sibling-repo (~/git/robberts-infrastructure)." >&2
  echo "Cert daar verversen met:" >&2
  echo "  kubeseal --fetch-cert > $CERT" >&2
  exit 1
fi

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

{
  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${SECRET_NAME}
  namespace: ${NAMESPACE}
  annotations:
    # Reflector mirrort deze secret automatisch naar branch-preview-namespaces
    # (robberts-assistent-pr-*), zodat die niet elk hun eigen SealedSecret nodig
    # hebben — zelfde patroon als personal-feed se sealed-secret-api-keys.yaml.
    reflector.v1.k8s.emberstack.com/reflection-allowed: "true"
    reflector.v1.k8s.emberstack.com/reflection-allowed-namespaces: ^(robberts-assistent-pr-.*)\$
    reflector.v1.k8s.emberstack.com/reflection-auto-enabled: "true"
    reflector.v1.k8s.emberstack.com/reflection-auto-namespaces: ^(robberts-assistent-pr-.*)\$
type: Opaque
stringData:
EOF

  count=0
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" || "$line" =~ ^# ]] && continue
    [[ "$line" != *=* ]] && continue

    key="${line%%=*}"
    val="${line#*=}"
    val="${val%\"}"; val="${val#\"}"
    val="${val%\'}"; val="${val#\'}"

    case "$key" in
      # NB: RA_FIREBASE_CREDENTIALS_JSON (de service-account-JSON, multi-line) staat NIET in
      # secrets.env en wordt apart in de sealed secret gemerged (kubeseal --merge-into met
      # kubectl --from-file); dit script (line-based) dekt alleen de enkelregelige keys.
      RA_GOOGLE_CLIENT_ID|RA_REMEMBER_SECRET|RA_ALLOWED_EMAILS|RA_PREVIEW_SKIP_GOOGLE_AUTH|RA_DATABASE_URL|RA_OPENAI_API_KEY|RA_TELEGRAM_BOT_TOKEN|RA_TELEGRAM_CHAT_ID|RA_GOOGLE_OAUTH_CLIENT_ID|RA_GOOGLE_OAUTH_CLIENT_SECRET|RA_GOOGLE_OAUTH_REFRESH_TOKEN|RA_FIREBASE_PROJECT_ID|RA_FIREBASE_DATABASE_ID|RA_FIREBASE_STORAGE_BUCKET)
        printf '  %s: |-\n' "$key"
        printf '%s\n' "$val" | sed 's/^/    /'
        count=$((count + 1))
        ;;
    esac
  done < "$SRC"

  if (( count == 0 )); then
    echo "Error: geen robberts-assistent secret keys gevonden in $SRC." >&2
    exit 1
  fi
  echo "[seal] $count entries uit $SRC -> $OUT" >&2
} > "$tmp"

kubeseal --cert "$CERT" -o yaml < "$tmp" > "$OUT"
echo "[seal] klaar: $OUT" >&2
