#!/usr/bin/env bash
# Carga .env y arranca el backend en local (perfil dev, puerto de SERVER_PORT o 8090 por defecto).
set -e
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "No existe .env en $(pwd). Copia env-template.txt a .env y rellénalo primero."
  exit 1
fi

set -a
source .env
set +a

./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
