#!/usr/bin/env bash
# Simula un corte de internet de una tienda: la desconecta de la red "wan", por donde llega a la central,
# y la vuelve a conectar. La tienda sigue en su propia red (<store>-lan), así que sigue funcionando con sus datos.
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

if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
  echo "El contenedor ${store}-api no está en ejecución." >&2
  exit 1
fi

# Docker Compose pone a cada red el nombre del proyecto y el de la red del archivo ("wan"). Se filtra por esas
# dos etiquetas para no tomar la red de otro proyecto que también tenga una red "wan".
project="$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
network="$(docker network ls --filter "label=com.docker.compose.project=${project}" \
  --filter "label=com.docker.compose.network=wan" --format '{{.Name}}' | head -n 1)"
if [[ -z "$network" ]]; then
  echo "No se encontró la red wan del proyecto ${project}. ¿Está levantado el entorno?" >&2
  exit 1
fi

# Contenedores conectados a la red, por id. Así el script no falla si la tienda ya estaba en el estado pedido.
connected="$(docker network inspect --format '{{range $id, $c := .Containers}}{{$id}} {{end}}' "$network")"
is_connected=false
case " $connected " in
  *" $container "*) is_connected=true ;;
esac

if [[ "$action" == "down" ]]; then
  if [[ "$is_connected" == true ]]; then
    docker network disconnect "$network" "$container"
  fi
  echo "${store} desconectada de la central (corte de internet simulado)."
else
  if [[ "$is_connected" == false ]]; then
    docker network connect "$network" "$container"
  fi
  echo "${store} reconectada a la central."
fi
