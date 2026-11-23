#!/usr/bin/env bash
# Borra todos los datos (volúmenes) de central y tiendas, y vuelve a levantar el entorno desde cero.
set -euo pipefail

cd "$(dirname "$0")/.."

read -r -p "Esto borrará las bases de datos de central y tiendas. ¿Continuar? [s/N] " answer
case "$answer" in
  s|S|si|SI|sí|Sí) ;;
  *) echo "Cancelado."; exit 0 ;;
esac

docker compose down -v
docker compose up -d --build
echo "Entorno reiniciado desde cero."
