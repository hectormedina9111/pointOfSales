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

## Idioma de los mensajes

Los mensajes de error y los textos que ve el usuario salen en español por defecto. Para recibirlos en inglés, envía la cabecera `Accept-Language: en`. Los códigos de error (por ejemplo `DUPLICATE_SKU`) no cambian: son identificadores estables en inglés. Los textos están en `messages.properties` (inglés) y `messages_es.properties` (español).

## Inicio de sesión

- `POST /api/v1/auth/login` con `{"username": "...", "password": "..."}` devuelve `accessToken` (válido 15 minutos), `refreshToken`, `expiresIn` y `mustChangePassword`. Las rutas protegidas reciben el token en `Authorization: Bearer <accessToken>`.
- `POST /api/v1/auth/refresh` cambia el refresh token por uno nuevo (el anterior queda revocado). `POST /api/v1/auth/logout` revoca el refresh token.
- El usuario inicial es `admin`, creado solo en la central. Su contraseña se toma de `POS_ADMIN_INITIAL_PASSWORD`; si la variable está vacía, se genera en el primer arranque y se muestra una sola vez en el log de la central (`docker compose logs central-api`).
- **En local (Vagrant)** la contraseña inicial es `Admin.Local.2026`, tomada de `.env.example`, que Vagrant copia a `.env` si no existe. Solo sirve para pruebas locales; en producción la variable es un secreto del servidor o se deja vacía.
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
- Cada usuario solo ve las sucursales asignadas en `user_branch`. Un listado sin asignaciones devuelve una lista vacía y una sucursal ajena responde 404. Crear una sucursal no da acceso a nadie: el administrador asigna las sucursales a cada usuario con `PUT /api/v1/users/{id}/branches`. Las asignaciones se mantienen con `GET/PUT /api/v1/users/{id}/branches` (`{"branchIds": [1, 2]}`).
- En una tienda, los endpoints de escritura de datos maestros (países, empresa, sucursales, usuarios, roles y privilegios) responden 403 con código `READ_ONLY_NODE`. Los cambios llegan a la tienda por sincronización.
- Cada alta y cambio de datos maestros queda en la bitácora `audit_log`, en la misma transacción. Se consulta con `GET /api/v1/audit-log?entity=&entityId=&from=&to=` (fechas en ISO-8601 UTC, por ejemplo `2026-11-24T00:00:00Z`), ordenada por fecha descendente. Requiere el privilegio ENDPOINT correspondiente.

## Catálogo

- Las categorías (árbol, `GET/POST/PUT/DELETE /api/v1/categories`, filtros `parentId` y `active`), las unidades de medida (`/api/v1/units-of-measure`), los impuestos por país (`/api/v1/taxes?countryId=`) y los productos (`/api/v1/products`) se mantienen en la central. El catálogo es global: no se filtra por sucursal.
- Búsqueda de productos: `GET /api/v1/products?q=&categoryId=&type=&active=`. `q` busca parte del nombre sin distinguir mayúsculas, o el SKU o código de barras exacto. También `GET /api/v1/products/{id}`, `/products/barcode/{code}` y `/products/sku/{sku}`.
- `DELETE` en categorías y productos los desactiva; no los borra. Una categoría no se desactiva si tiene subcategorías o productos activos, y un impuesto en uso no se desactiva.
- El impuesto de un producto para otro país se mantiene con `GET/PUT /api/v1/products/{id}/taxes` (`[{"countryId": 2, "taxId": 5}]`). `PUT` reemplaza la lista completa.
- Los precios incluyen ISV. Cambiar el precio o el costo de un producto deja un evento `PRICE_CHANGED` en la bitácora, además del cambio normal.
- Un servicio (`productType: SERVICE`) nunca controla inventario ni seriales. Los seriales exigen inventario y una unidad sin decimales.

## Precios

- Las listas de precios se mantienen en la central: `GET/POST/PUT /api/v1/price-lists` (`/price-lists/{id}` para una), con filtros `countryId` y `active`. Una lista es `FIXED_PRICE` (precio por producto) o `PERCENT_OFF_BASE` (descuento sobre el precio base, con `percent` de 0 a 100). Todos los precios incluyen ISV.
- Las escalas de mayoreo son los precios por producto de una lista `FIXED_PRICE`: `GET/POST/PUT/DELETE /api/v1/price-lists/{id}/items` (filtro `productId`). Cada escala empieza en `minQuantity` (1 por defecto) y no se repite para el mismo producto. `DELETE` borra la fila; la bitácora guarda el precio anterior.
- Precio resuelto: `GET /api/v1/products/{id}/price?priceListId=&branchId=&quantity=1&date=YYYY-MM-DD`. Sin `priceListId` se usa la lista por defecto de la sucursal, que debe estar asignada al usuario. La respuesta trae el precio, la base gravable y el impuesto, además de `source` (`PRICE_LIST` o `BASE_PRICE`) y `priceListId` (vacío si se usó el precio base).
- La lista por defecto de cada sucursal es `defaultPriceListId` en `PUT /api/v1/branches/{id}`.

## Tiendas y activación

Cada tienda tiene su propio servidor y se activa ante la central una sola vez. La tienda siempre llama a la central; la central nunca llama a la tienda. Cuando la tienda se activa, la central le asigna su número y su rango de ids (tienda N: desde N × 1 000 000 000 + 1).

**En local (Vagrant y Docker Compose).** La central crea al arrancar las tiendas 1 y 2 con los códigos de `POS_DEV_ACTIVATION_CODES` (por defecto `1:STORE1-DEV-CODE,2:STORE2-DEV-CODE`). Cada tienda toma su código de `POS_ACTIVATION_CODE` (`POS_STORE1_ACTIVATION_CODE` y `POS_STORE2_ACTIVATION_CODE` en `.env`) y se activa sola al arrancar, en segundo plano. Para comprobarlo, mira el log de la tienda (`Store node 1 activated`) o pregunta a `GET http://localhost:8081/api/v1/sync/status`. Los códigos de desarrollo son solo para local: en producción no se usan.

**Alta de una tienda real.**

1. En la central, un administrador registra la tienda: `POST /api/v1/store-nodes` con `{"branchId": <id de la sucursal>}` (o `null`). La respuesta trae `nodeNumber` y el rango de ids.
2. Genera el código de activación: `POST /api/v1/store-nodes/{id}/activation-code`. El código se muestra una sola vez y vence en 24 horas.
3. En el servidor de la tienda, configura el perfil `store`, la base de datos y estas variables:
   - `POS_NODE_NUMBER`: el `nodeNumber` que asignó la central. Si no coincide, la tienda no guarda su identidad.
   - `POS_CENTRAL_URL`: la dirección de la central, por ejemplo `https://central.empresa.com`.
   - `POS_ACTIVATION_CODE`: el código del paso 2.
4. Arranca la tienda. Se activa en segundo plano y, una vez activada, guarda su identidad en `store_identity`. A partir de ahí `POS_ACTIVATION_CODE` ya no se usa y puede quitarse. Si la central no responde, la tienda reintenta cada 10 segundos durante unos 5 minutos; si el código venció o fue rechazado, el log lo indica y hay que generar otro código.

**Revocar una tienda.** `POST /api/v1/store-nodes/{id}/revoke` deja de aceptar su token de inmediato y libera su sucursal. Una tienda revocada no vuelve a activarse: para reemplazarla se registra una tienda nueva.

**Token de la tienda.** Las llamadas de la tienda a la central llevan la cabecera `X-Store-Token`. La central guarda solo su SHA-256; la tienda lo guarda en claro en `store_identity`, en su propia base de datos local.

## Sincronización

La central es la dueña de los datos maestros (países, empresa, sucursales, usuarios, roles, privilegios, catálogo, impuestos y listas de precios). Cada tienda recibe una copia de solo lectura.

**Cómo funciona.**

- Cada cambio en un dato maestro se anota en la tabla `sync_change_log` de la central dentro de la misma transacción, con un trigger de PostgreSQL. Así no se puede modificar un dato sin dejar el registro.
- Cada 30 segundos, la tienda pregunta a la central qué cambió desde la última versión que aplicó, aplica los cambios en una sola transacción y los confirma. Si la central no responde, la tienda sigue vendiendo con sus datos locales y reintenta en el siguiente ciclo.
- La primera vez, antes de los cambios, la tienda carga una copia completa de los datos maestros (la carga inicial). Si esa carga se interrumpe, se vuelve a empezar en el siguiente ciclo.
- Cada ciclo envía un latido a la central con la versión aplicada. Una tienda sin latido durante dos minutos aparece como fuera de sincronía en el panel.

**Cómo verlo.**

- Panel: `GET /api/v1/store-nodes` en la central. Cada tienda muestra `latestVersion` (la versión de la central), `lastDownloadVersion` (la que confirmó la tienda), `pendingChanges` (cuántos cambios le faltan), `connected` y `syncState` (`ACTIVE`, `OUT_OF_SYNC`, `PENDING` o `DISABLED`).
- Estado de la tienda: `GET /api/v1/sync/status` en la tienda muestra `lastAppliedVersion`, `snapshotCompletedAt`, `lastSyncAt` y `lastSyncError`. Si la última sincronización falló, el error aparece ahí y en el log de la tienda (sin el token).
- Intervalo: la propiedad `pos.sync.interval` (30 segundos por defecto).

**Prueba de corte.** Con el entorno local levantado:

1. `scripts/simulate-outage.sh store2 down`: la tienda 2 pierde la conexión con la central, pero sigue funcionando.
2. En la central, cambia el precio de un producto (por ejemplo, con la colección Hoppscotch, carpeta 5 o 6).
3. En la tienda 1, `GET /api/v1/products/{id}/price` muestra el precio nuevo en unos segundos. En la tienda 2 sigue el precio anterior.
4. En el panel de la central, la tienda 2 aparece como `OUT_OF_SYNC` con `pendingChanges` mayor que cero (después de dos minutos sin latido).
5. `scripts/simulate-outage.sh store2 up`: en el siguiente ciclo, la tienda 2 aplica el cambio y `pendingChanges` vuelve a cero.

## Simular un corte de internet

Desconecta una tienda de la central (la tienda sigue funcionando, pero no puede sincronizar). La prueba completa de sincronización está en la sección anterior:

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
