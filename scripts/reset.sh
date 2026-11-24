#!/usr/bin/env bash
# Borra todos los datos (volúmenes) de central y tiendas, y vuelve a levantar el entorno desde cero.
set -euo pipefail

cd "$(dirname "$0")/.."

read -r -p "Esto borrará las bases de datos de central y tiendas. ¿Continuar? [s/N] " answer
case "$answer" in
  s|S|si|SI|sí|Sí) ;;
  *) echo "Cancelado."; exit 0 ;;
esac

# Las tres APIs usan la misma imagen (medinasystems/pos:dev), que solo construye el servicio central-api.
# Se construye primero para que las tiendas no intenten descargarla de un registro.
docker compose down -v --remove-orphans
docker compose build
docker compose up -d
echo "Entorno reiniciado desde cero. La central crea los datos iniciales al arrancar."
