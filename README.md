# Sistema Punto de Venta (backend)

Backend del sistema de punto de venta de Medina Systems. Es un solo proyecto Spring Boot que corre como **central** (dueña de catálogos, precios y usuarios) o como **tienda** (operación diaria de una sucursal), según el perfil activo.

El plan completo (requerimientos, arquitectura, modelo de datos, API y MVP) está en [docs/plan.md](docs/plan.md).

## Requisitos

En Windows:

- Git (con Git Bash)
- JDK 25
- Docker Desktop
- VirtualBox
- Vagrant

## Pruebas

Desde Git Bash:

```bash
./mvnw verify
```

En PowerShell:

```powershell
mvnw.cmd verify
```

Las pruebas de integración usan Testcontainers, así que Docker Desktop debe estar encendido.

## Levantar el entorno

Dentro de la carpeta del proyecto:

```bash
vagrant up
```

Esto crea la máquina virtual Ubuntu, instala Docker y levanta la central, la tienda 1 y la tienda 2, cada una con su base de datos.

| Nodo     | API (puerto) | Base de datos (puerto) |
|----------|--------------|------------------------|
| Central  | 8080         | 5432                   |
| Tienda 1 | 8081         | 5433                   |
| Tienda 2 | 8082         | 5434                   |

Comprobaciones rápidas:

- Swagger de la central: http://localhost:8080/swagger-ui.html (tiendas: `:8081` y `:8082`)
- Identidad del nodo: http://localhost:8080/api/v1/system/info (cambiar el puerto para cada tienda)

Para borrar todos los datos y empezar de cero: `scripts/reset.sh` (dentro de la VM, en `/vagrant`).

## Simular un corte de internet

Desconecta una tienda de la central (la tienda sigue funcionando, pero no puede sincronizar):

```bash
scripts/simulate-outage.sh store1 down
scripts/simulate-outage.sh store1 up
```

Se puede usar `store2` en lugar de `store1`.

## Flujo de trabajo

- No se hace push directo a `main`.
- Cada paso del MVP se desarrolla en su propia rama `feature/step-N-<nombre>`.
- Al terminar el paso se abre un Pull Request hacia `main`, con la descripción de los requerimientos cubiertos y cómo probarlos.
- El CI de GitHub Actions debe pasar antes de unir el PR.
- El siguiente paso no empieza hasta que el PR esté revisado y unido a `main`.
