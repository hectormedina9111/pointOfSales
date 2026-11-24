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

El proyecto se hace en dos etapas: primero se programa todo y después se escriben las pruebas completas. Por ahora no hay pruebas: `./mvnw verify` solo compila.

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

## Inicio de sesión

- `POST /api/v1/auth/login` con `{"username": "...", "password": "..."}` devuelve `accessToken` (válido 15 minutos), `refreshToken`, `expiresIn` y `mustChangePassword`. Las rutas protegidas reciben el token en `Authorization: Bearer <accessToken>`.
- `POST /api/v1/auth/refresh` cambia el refresh token por uno nuevo (el anterior queda revocado). `POST /api/v1/auth/logout` revoca el refresh token.
- El usuario inicial es `admin`, creado solo en la central. Su contraseña se toma de `POS_ADMIN_INITIAL_PASSWORD`; si la variable está vacía, se genera en el primer arranque y se muestra una sola vez en el log de la central (`docker compose logs central-api`).
- Con `mustChangePassword` en `true`, el resto de endpoints responde 403 hasta que el usuario cambie su contraseña con `POST /api/v1/me/password` (`{"currentPassword": "...", "newPassword": "..."}`, mínimo 10 caracteres).
- `POS_JWT_SECRET` firma los tokens y debe tener al menos 32 caracteres. Los valores de `.env.example` son solo para desarrollo.

## Privilegios

- Cada endpoint de `/api/v1` tiene un privilegio ENDPOINT (tabla `privilege`). Al arrancar, la aplicación lee los controladores, crea los privilegios que falten y se los asigna al rol ADMIN. Si se quita un privilegio ENDPOINT a ADMIN, vuelve a asignarse en el siguiente arranque.
- Los demás roles (MANAGER, CASHIER, WAREHOUSE_CLERK) empiezan sin privilegios. Se configuran con `PUT /api/v1/roles/{id}/privileges` (`{"privilegeCodes": ["GET_USERS", ...]}`); el cambio aplica en la siguiente petición, sin volver a iniciar sesión.
- Los privilegios MENU y ACTION se mantienen con `/api/v1/privileges`. Los ENDPOINT solo se leen.
- El frontend usa `GET /api/v1/me/menus` para armar el menú y `GET /api/v1/me/privileges` para mostrar u ocultar botones.
- Las rutas públicas (`/api/v1/auth/**`, `/api/v1/system/**`) y las del propio usuario (`/api/v1/me/**`) no requieren privilegio.

## Organización y auditoría

- Los países, la empresa y las sucursales se mantienen en la central: `GET/POST/PUT /api/v1/countries` (`/countries/{id}` para uno), `GET/PUT /api/v1/company` (404 hasta configurarla) y `GET/POST/PUT /api/v1/branches` (`/branches/{id}` para una).
- Cada usuario solo ve las sucursales asignadas en `user_branch`. Un listado sin asignaciones devuelve una lista vacía y una sucursal ajena responde 404. Al crear una sucursal, el usuario que la crea recibe acceso a ella. Las asignaciones se mantienen con `GET/PUT /api/v1/users/{id}/branches` (`{"branchIds": [1, 2]}`).
- En una tienda, los endpoints de escritura de datos maestros (países, empresa, sucursales, usuarios, roles y privilegios) responden 403 con código `READ_ONLY_NODE`. Los cambios llegan a la tienda por sincronización.
- Cada alta y cambio de datos maestros queda en la bitácora `audit_log`, en la misma transacción. Se consulta con `GET /api/v1/audit-log?entity=&entityId=&from=&to=` (fechas en ISO-8601 UTC, por ejemplo `2026-11-24T00:00:00Z`), ordenada por fecha descendente. Requiere el privilegio ENDPOINT correspondiente.

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
