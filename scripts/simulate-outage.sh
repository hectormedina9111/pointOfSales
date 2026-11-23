#!/usr/bin/env bash
# Simula un corte de internet de una tienda: la desconecta de la red "wan",
# por donde llega a la central, y la vuelve a conectar.
# Uso: scripts/simulate-outage.sh <store1|store2> <down|up>
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ $# -ne 2 ]]; then
  echo "Uso: $0 <store1|store2> <down|up>" >&2
  exit 1
fi

store="$1"
action="$2"

case "$store" in
  store1|store2) ;;
  *) echo "Tienda no válida: $store (use store1 o store2)" >&2; exit 1 ;;
esac

case "$action" in
  down|up) ;;
  *) echo "Acción no válida: $action (use down o up)" >&2; exit 1 ;;
esac

container="$(docker compose ps -q "${store}-api")"
if [[ -z "$container" ]]; then
  echo "No se encontró el contenedor ${store}-api. ¿Está levantado el entorno?" >&2
  exit 1
fi

network="$(docker network ls --filter name=_wan --format '{{.Name}}' | head -n 1)"
if [[ -z "$network" ]]; then
  echo "No se encontró la red wan. ¿Está levantado el entorno?" >&2
  exit 1
fi

if [[ "$action" == "down" ]]; then
  docker network disconnect "$network" "$container"
  echo "${store} desconectada de la central (corte de internet simulado)."
else
  docker network connect "$network" "$container"
  echo "${store} reconectada a la central."
fi
