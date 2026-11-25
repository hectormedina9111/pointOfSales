# Sistema Punto de Venta (backend)

Backend del sistema de punto de venta de Medina Systems. Es un solo proyecto Spring Boot que corre como **central** (dueña de catálogos, precios, usuarios y organización) o como **tienda** (operación diaria de una sucursal), según el perfil activo. El plan completo (requerimientos, arquitectura, modelo de datos, API y MVP) está en [docs/plan.md](docs/plan.md).

## Guía de inicio a fin

### 1. Requisitos

En Windows, para el desarrollo:

- Git (con Git Bash)
- JDK 25 (para compilar con `./mvnw`)
- Docker Desktop (Testcontainers y la compilación local)
- VirtualBox y Vagrant (la máquina virtual que levanta el entorno completo)

### 2. Levantar el entorno

Dentro de la carpeta del proyecto:

```bash
vagrant up
```

Crea la máquina virtual Ubuntu, instala Docker y levanta la central, la tienda 1 y la tienda 2, cada una con su base de datos y su API. Si el repositorio del frontend ([pointOfSales-frontend](https://github.com/hectormedina9111/pointOfSales-frontend)) está clonado al lado de este (`repositorios/pointOfSales` y `repositorios/pointOfSales-frontend`), también levanta la web de cada nodo. La primera vez tarda varios minutos. Si `.env` no existe, Vagrant lo crea desde `.env.example` (valores de desarrollo).

| Nodo     | IP            | API (puerto) | Base de datos (puerto) | Web (puerto) |
|----------|---------------|--------------|------------------------|--------------|
| Central  | 192.168.56.10 | 8080         | 5432                   | 80           |
| Tienda 1 | 192.168.56.11 | 8080         | 5432                   | 80           |
| Tienda 2 | 192.168.56.12 | 8080         | 5432                   | 80           |

Cada nodo tiene su propia IP y su web llama directamente a la API de ese mismo nodo, como si fueran tres máquinas distintas. Las webs están en http://192.168.56.10 (central), http://192.168.56.11 (tienda 1) y http://192.168.56.12 (tienda 2).

Al arrancar, la central crea sola el país Honduras, la empresa de ejemplo, las unidades de medida (UNIT, KG, LB, LT) y los impuestos de Honduras (EXEMPT, ISV15, ISV18). No crea sucursales. Las tiendas 1 y 2 se activan solas y reciben una copia de los datos maestros.

### 3. Entrar como administrador

- Usuario: `admin`.
- **Contraseña en local: `Admin.Local.2026`.** Viene de `.env.example`, que Vagrant copia a `.env`. Solo es para pruebas locales.
- En producción la contraseña inicial no está en ningún archivo: se toma de `POS_ADMIN_INITIAL_PASSWORD` o se genera en el primer arranque y se muestra una sola vez en el log de la central (`docker compose logs central-api`).
- El sistema obliga a cambiarla en el primer ingreso: con `mustChangePassword` en `true`, los demás endpoints responden 403 hasta que se cambie con `POST /api/v1/me/password`. El cambio se puede hacer en la central o en cualquier tienda; ver la sección 6 para cómo viaja a la central.

### 4. Swagger

La máquina virtual tiene tres IPs privadas, una por nodo: `192.168.56.10` (central), `192.168.56.11` (tienda 1) y `192.168.56.12` (tienda 2), para no ocupar puertos de `localhost`. Si chocan con otra red de tu equipo, se cambian con las variables de entorno `POS_CENTRAL_IP`, `POS_STORE1_IP` y `POS_STORE2_IP` antes de `vagrant up`.


- Central: http://192.168.56.10:8080/swagger-ui.html
- Tienda 1: http://192.168.56.11:8080/swagger-ui.html
- Tienda 2: http://192.168.56.12:8080/swagger-ui.html

Identidad de cada nodo: `GET /api/v1/system/info` (cambiar la IP).

### 5. Colección Hoppscotch

La colección de pruebas está en [docs/api](docs/api/README.md): `pos-api.hoppscotch.json` (108 peticiones en 10 carpetas) y dos entornos, `pos-local.hoppscotch-env.json` (central) y `pos-store1.hoppscotch-env.json` (tienda 1). Importarla y recorrer las carpetas en orden, de la 1 a la 10.

| Carpeta | Qué prueba |
|---|---|
| 1. Sistema | Información del nodo y salud del servicio. |
| 2. Sesión | Login, refresco de token, cambio de contraseña, perfil, menús y privilegios del usuario, logout. |
| 3. Usuarios, roles y privilegios | Altas y cambios de usuarios y roles, y privilegios de un rol (`PUT /roles/{id}/privileges`). |
| 4. Organización | Países, empresa, sucursales y asignación de sucursales a usuarios. |
| 5. Catálogo | Unidades, impuestos, categorías (árbol), productos, servicios y búsquedas por texto, código de barras y SKU. |
| 6. Precios | Listas de mayoreo y VIP, escalas por cantidad, vigencias, precio por sucursal y precio de hoy. |
| 7. Tiendas | Panel de tiendas (central); estado de sincronización y login en la tienda 1 (tienda). |
| 8. Auditoría | Bitácora de cambios, por entidad y por fechas. |
| 9. Caja y ventas | Altas y listado de cajas por sucursal, listado de métodos de pago y, en la tienda, sesiones de caja (apertura, movimientos, resumen y cierre), cotización, esperas, cobros con vuelto y mixto, idempotencia y ticket. |
| 10. Errores de ejemplo | Respuestas de error esperadas (401, 422). Deben fallar a propósito. |

Antes de empezar, pon la contraseña de `admin` en el entorno (`adminPassword`) y ejecuta **2. Sesión → Login**. Después, **2. Sesión → Cambiar contraseña** y actualiza `adminPassword` con la nueva. El token de acceso dura 15 minutos; si una petición responde 401, ejecuta **Refrescar token**.

La colección no cubre todo. Los casos que faltan (por ejemplo, un MANAGER al que se le quita un privilegio, o un precio de 115 con ISV 15 %) están en [docs/plan.md](docs/plan.md), sección "Estado de los criterios (cierre del MVP)".

### 6. Sincronización y corte de internet

Cada 30 segundos (y 15 segundos después del arranque), cada tienda pide a la central los cambios de datos maestros desde la última versión que aplicó. La primera vez carga una copia completa. La tienda siempre llama a la central; la central nunca la llama.

Cada ciclo empieza subiendo a la central lo que la tienda tenía pendiente. Hoy eso son los cambios de contraseña hechos en la tienda (`POST /api/v1/me/password` funciona en la tienda): se aplican en la tienda al momento y se suben a la central, que conserva el cambio más reciente y lo replica a todas las tiendas. Si la central no responde, el ciclo se detiene antes de bajar cambios, para que una contraseña vieja no pise la nueva. El latido informa cuántos envíos quedan pendientes (`pendingUploadCount`), y el panel de tiendas (`GET /api/v1/store-nodes`) lo muestra.

Prueba de corte (con el entorno levantado):

1. Desconecta la tienda 2 de la red de la central: `scripts/simulate-outage.sh store2 down`. La tienda sigue funcionando con sus datos.
2. En la central, cambia el precio de un producto (carpeta 5, "Actualizar producto").
3. En la tienda 1, `GET /api/v1/products/{id}/price` muestra el precio nuevo en menos de un minuto. En la tienda 2 sigue el precio anterior.
4. En el panel de la central (`GET /api/v1/store-nodes`), la tienda 2 aparece como `OUT_OF_SYNC` con `pendingChanges` mayor que cero, después de dos minutos sin latido.
5. Reconéctala: `scripts/simulate-outage.sh store2 up`. En el siguiente ciclo aplica el cambio y `pendingChanges` vuelve a cero.

Se puede usar `store1` en lugar de `store2`. Para ver el estado de una tienda: `GET /api/v1/sync/status` en su puerto, o el log de su contenedor.

### 7. Reiniciar desde cero

```bash
scripts/reset.sh
```

Borra las bases de datos de la central y de las tiendas (volúmenes) y levanta de nuevo el entorno. Pide confirmación.

### 8. Compilar y pruebas

Desde Git Bash:

```bash
./mvnw verify
```

En PowerShell: `mvnw.cmd verify`.

Por ahora no hay pruebas en el repositorio: el proyecto se hace en dos etapas. Primero se programa todo; después se escriben las pruebas (unitarias, de integración con Testcontainers, de seguridad y de arquitectura). Mientras tanto, `./mvnw verify` solo compila.

### 9. Dónde están las decisiones

- [docs/plan.md](docs/plan.md): requerimientos, arquitectura, modelo de datos, API y MVP.
- "Decisiones de implementación" de cada paso (3, 4, 5, 6A, 6B y 7), al final de `docs/plan.md`. Ahí se documentan las desviaciones del plan y lo que queda pendiente.

## Referencia de la API

### Idioma de los mensajes

Los mensajes de error y los textos que ve el usuario salen en español por defecto. Para recibirlos en inglés, envía la cabecera `Accept-Language: en`. Los códigos de error (por ejemplo `DUPLICATE_SKU`) no cambian. Los textos están en `messages.properties` (inglés) y `messages_es.properties` (español).

### Inicio de sesión

- `POST /api/v1/auth/login` con `{"username": "...", "password": "..."}` devuelve `accessToken` (válido 15 minutos), `refreshToken`, `expiresIn` y `mustChangePassword`. Las rutas protegidas reciben el token en `Authorization: Bearer <accessToken>`.
- `POST /api/v1/auth/refresh` cambia el refresh token por uno nuevo (el anterior queda revocado). `POST /api/v1/auth/logout` lo revoca.
- `POS_JWT_SECRET` firma los tokens y debe tener al menos 32 caracteres. Los valores de `.env.example` son solo para desarrollo.

### Privilegios

- Cada endpoint de `/api/v1` tiene un privilegio ENDPOINT (tabla `privilege`). Al arrancar, la central lee los controladores, crea los privilegios que falten y se los asigna a ADMIN.
- MANAGER, CASHIER y WAREHOUSE_CLERK empiezan sin privilegios. Se configuran con `PUT /api/v1/roles/{id}/privileges` (`{"privilegeCodes": ["GET_USERS", ...]}`); el cambio aplica en la siguiente petición.
- Los privilegios MENU y ACTION se mantienen con `/api/v1/privileges`. Los ENDPOINT solo se leen.
- El frontend usa `GET /api/v1/me/menus` para armar el menú y `GET /api/v1/me/privileges` para mostrar u ocultar botones.
- Menús de las pantallas del frontend, creados al arrancar la central por `MenuSeeder` (no con Flyway) y asignados a ADMIN: `MENU_ORGANIZATION` (`_COUNTRIES`, `_COMPANY`, `_BRANCHES`), `MENU_CATALOG` (`_PRODUCTS`, `_CATEGORIES`, `_UNITS`, `_TAXES`), `MENU_PRICING` (`_PRICE_LISTS`, `_PRICE_CHECK`), `MENU_STORES` (`_NODES`, `_SYNC_STATUS`) y `MENU_AUDIT`.
- Las rutas públicas (`/api/v1/auth/**`, `/api/v1/system/**`) y las del propio usuario (`/api/v1/me/**`) no requieren privilegio.

### Organización y auditoría

- Países, empresa y sucursales: `GET/POST/PUT /api/v1/countries`, `GET/PUT /api/v1/company` (404 hasta configurarla) y `GET/POST/PUT /api/v1/branches`. Cada central atiende un solo país: un segundo `POST /api/v1/countries` responde 422 `SINGLE_COUNTRY_ONLY`.
- La visibilidad de sucursales depende del nodo, no del usuario: la central ve todas las sucursales y cada tienda solo la que le asignó la central (`store_identity.branch_id`). Una sucursal que el nodo no ve responde 404. La tabla `user_branch` solo decide en qué tienda puede iniciar sesión cada usuario: el administrador asigna las tiendas con `GET/PUT /api/v1/users/{id}/branches` (`{"branchIds": [1, 2]}`).
- En una tienda, los endpoints de escritura de datos maestros responden 403 con código `READ_ONLY_NODE`. Los cambios llegan a la tienda por sincronización.
- Cada alta y cambio de datos maestros queda en `audit_log`, en la misma transacción. Se consulta con `GET /api/v1/audit-log?entity=&entityId=&from=&to=` (fechas ISO-8601 UTC, por ejemplo `2026-11-24T00:00:00Z`).

### Catálogo

- Categorías (árbol, `GET/POST/PUT/DELETE /api/v1/categories`), unidades (`/api/v1/units-of-measure`), impuestos por país (`/api/v1/taxes?countryId=`) y productos (`/api/v1/products`). El catálogo es global: no se filtra por sucursal.
- Búsqueda: `GET /api/v1/products?q=&categoryId=&type=&active=`. `q` busca parte del nombre sin distinguir mayúsculas, o el SKU o código de barras exacto. También `GET /api/v1/products/{id}`, `/products/barcode/{code}` y `/products/sku/{sku}`.
- `DELETE` en categorías y productos los desactiva; no los borra. Una categoría no se desactiva si tiene subcategorías o productos activos, y un impuesto en uso no se desactiva.
- Impuesto de un producto para otro país: `GET/PUT /api/v1/products/{id}/taxes` (`[{"countryId": 2, "taxId": 5}]`). `PUT` reemplaza la lista completa.
- Los precios incluyen ISV. Cambiar el precio o el costo deja un evento `PRICE_CHANGED` en la bitácora, además del cambio normal.
- Un servicio (`productType: SERVICE`) nunca controla inventario ni seriales. Los seriales exigen inventario y una unidad sin decimales.

### Precios

- Listas de precios: `GET/POST/PUT /api/v1/price-lists` (`/price-lists/{id}` para una), con filtros `countryId` y `active`. Una lista es `FIXED_PRICE` (precio por producto) o `PERCENT_OFF_BASE` (descuento sobre el precio base, con `percent` de 0 a 100).
- Escalas de mayoreo (solo en listas `FIXED_PRICE`): `GET/POST/PUT/DELETE /api/v1/price-lists/{id}/items` (filtro `productId`). Cada escala empieza en `minQuantity` (1 por defecto) y no se repite para el mismo producto.
- Precio resuelto: `GET /api/v1/products/{id}/price?priceListId=&branchId=&quantity=1&date=YYYY-MM-DD`. Sin `priceListId` se usa la lista por defecto de la sucursal, que debe ser visible desde el nodo. Sin `date`, el día es hoy en la zona horaria del país (Honduras: UTC-6). La respuesta trae el precio, la base gravable y el impuesto, además de `source` (`PRICE_LIST` o `BASE_PRICE`) y `priceListId`.
- Lista por defecto de cada sucursal: `defaultPriceListId` en `PUT /api/v1/branches/{id}`.

### Cajas y métodos de pago

- Cajas: `GET/POST/PUT /api/v1/cash-registers` (`/cash-registers/{id}` para una), con filtros `branchId` y `active`. Cada caja pertenece a una sucursal y tiene `code` (único dentro de la sucursal), `name` y `receiptPrefix`. Solo la central las crea o cambia; cada nodo lista las cajas de las sucursales que ve.
- Métodos de pago: `GET /api/v1/payment-methods` (filtro `active`) y `PUT /api/v1/payment-methods/{id}` para cambiar `name`, `requiresReference`, `allowsChange` y `active`. Los códigos `CASH`, `CARD` y `TRANSFER` los fija el sistema: no hay alta ni cambio de código.
- Privilegios iniciales: al arrancar, la central concede a CASHIER y MANAGER sus privilegios de caja y venta, una sola vez por privilegio (registro en `role_default_grant`). Una concesión que se quite a mano no vuelve en los siguientes arranques. Detalle en `docs/plan.md`, "Decisiones de implementación: 3A paso 1".

### Sesiones de caja (tienda)

Solo la tienda abre, mueve y cierra sesiones de caja; la central responde 403 con `code: STORE_ONLY_OPERATION`. Detalle en `docs/plan.md`, "Decisiones de implementación: 3A paso 2".

- Abrir: `POST /api/v1/cash-sessions` con `cashRegisterId` y `openingAmount` (fondo inicial). Una caja tiene una sola sesión abierta y un cajero tiene una sola (`CASH_REGISTER_BUSY`, `USER_HAS_OPEN_SESSION`).
- Consultar: `GET /api/v1/cash-sessions/current` (la sesión abierta del cajero), `GET /api/v1/cash-sessions/{id}` (con sus movimientos) y `GET /api/v1/cash-sessions` con filtros `status`, `cashRegisterId`, `userId`, `from` y `to`, del más reciente al más antiguo. Un nodo ve las sesiones de las cajas que ve.
- Movimientos: `POST /api/v1/cash-sessions/{id}/movements` con `type` (`DEPOSIT` o `WITHDRAWAL`), `amount`, `reason` y, en un retiro, `authorization` (`username` y `password` de un jefe de tienda). Con el privilegio `ACTION_CASH_WITHDRAWAL` no hace falta autorización.
- Resumen: `GET /api/v1/cash-sessions/{id}/summary` con lo esperado por método de pago. En efectivo es el fondo, más depósitos, menos retiros y más lo cobrado en efectivo neto de vuelto (la parte de cobros llega con el módulo de ventas).
- Cierre: `POST /api/v1/cash-sessions/{id}/close` con `counts` (`paymentMethodCode` y `amount` por método) y `notes`. Devuelve la sesión cerrada con esperado, contado y diferencia por método.
- Pruebas: carpeta 9 de Hoppscotch, con la tienda 1. El retiro con autorización necesita un cajero sin `ACTION_CASH_WITHDRAWAL` (el administrador lo tiene); el usuario y la contraseña del jefe van en `managerUsername` y `managerPassword`.

### Ventas (tienda)

Solo la tienda cotiza, pone en espera y cobra; la central responde 403 con `code: STORE_ONLY_OPERATION`. Detalle en `docs/plan.md`, "Decisiones de implementación: 3A paso 3".

- Cotizar: `POST /api/v1/sales/quote` con `lines` (`productId`, `quantity`, y `discountType` y `discountValue` opcionales por línea) y el descuento de la venta. Devuelve líneas y totales calculados en el servidor, sin guardar nada. Los precios son los de la sucursal de la tienda. `discountAuthorizationRequired` indica si el descuento necesita al jefe.
- Esperas: `POST /api/v1/sales/holds` guarda la venta sin cobrar en la caja de la sesión abierta (`NO_OPEN_SESSION` si no hay una). `GET /api/v1/sales/holds` lista las esperas de la caja, `GET /api/v1/sales/holds/{id}` muestra una y `DELETE /api/v1/sales/holds/{id}` la borra de verdad (no es un documento: no se cobró nada).
- Cobrar: `POST /api/v1/sales` con `lines`, `payments` (`paymentMethodCode`, `amount`, y en efectivo `amountReceived`; en tarjeta o transferencia, `reference`, y en tarjeta `cardLast4`), `idempotencyKey` (obligatoria) y, si hace falta, `authorization` (`username` y `password` de un jefe). Con `holdId` cobra una venta en espera. El servidor recalcula todo; los pagos deben sumar el total exacto. Devuelve 201 con la venta, o 200 con la que creó esa misma clave: repetir un cobro nunca crea otra venta.
- Descuentos: más de `pos.sales.max-discount-percent` (10 % por defecto, variable `POS_SALES_MAX_DISCOUNT_PERCENT`) en una línea o en la venta necesita `ACTION_SALE_DISCOUNT_OVERRIDE` o la autorización de un jefe (`DISCOUNT_AUTHORIZATION_REQUIRED`).
- Consultar: `GET /api/v1/sales/{id}` (con líneas y pagos), `GET /api/v1/sales` con `status`, `cashSessionId`, `cashierId`, `from` y `to`, del más reciente al más antiguo. Solo el jefe de tienda tiene el listado (`GET_SALES`); el cajero consulta por id.
- Ticket: `GET /api/v1/sales/{id}/ticket` con los datos de impresión de 80 mm: empresa (razón social, nombre comercial, RTN), sucursal, caja, recibo, fecha y hora en la zona de la empresa, cajero, líneas, totales con exento, gravado 15 % y 18 %, ISV, pagos y vuelto. Solo para ventas cobradas.
- Pruebas: carpeta 9 de Hoppscotch. Los cobros de ejemplo suponen un producto de precio L 115.00; ajuste `productId` o los montos al precio real.

### Tiendas y activación

Cada tienda tiene su propio servidor y se activa ante la central una sola vez. La central le asigna su número y su rango de ids (tienda N: desde N × 1 000 000 000 + 1).

**En local.** La central crea al arrancar las tiendas 1 y 2 con los códigos de `POS_DEV_ACTIVATION_CODES` (por defecto `1:STORE1-DEV-CODE,2:STORE2-DEV-CODE`). Cada tienda toma su código de `POS_ACTIVATION_CODE` y se activa sola al arrancar, en segundo plano (`Store node 1 activated` en su log). Las tiendas de desarrollo se crean **sin sucursal**. Los códigos de desarrollo son solo para local.

**Alta de una tienda real.**

1. En la central, un administrador registra la tienda: `POST /api/v1/store-nodes` con `{"branchId": <id de la sucursal>}`. La respuesta trae `nodeNumber` y el rango de ids.
2. Genera el código de activación: `POST /api/v1/store-nodes/{id}/activation-code`. Se muestra una sola vez y vence en 24 horas.
3. En el servidor de la tienda, configura el perfil `store`, la base de datos y `POS_NODE_NUMBER` (el número que asignó la central; si no coincide, la tienda no guarda su identidad), `POS_CENTRAL_URL` y `POS_ACTIVATION_CODE`.
4. Arranca la tienda. Si la central no responde, reintenta cada 10 segundos durante unos 5 minutos. Si el código venció o fue rechazado, el log lo indica y hay que generar otro.

**Asignar o cambiar la sucursal de una tienda.** Desde la central, con `PUT /api/v1/store-nodes/{id}/branch` y el cuerpo `{"branchId": <id de la sucursal>}`. Para quitarla, envía `{"branchId": null}`. La tienda recibe el cambio en su siguiente latido, en unos 30 segundos. A partir de ese momento, la tienda atiende a los usuarios de esa sucursal. Una sucursal pertenece a una sola tienda, y una tienda revocada no se reasigna. Las tiendas de desarrollo se registran solas antes de que exista ninguna sucursal, por eso necesitan esta asignación.

**Revocar una tienda.** `POST /api/v1/store-nodes/{id}/revoke` deja de aceptar su token de inmediato y libera su sucursal. Una tienda revocada no vuelve a activarse.

**Token de la tienda.** Las llamadas de la tienda a la central llevan la cabecera `X-Store-Token`. La central guarda solo su SHA-256; la tienda lo guarda en claro en `store_identity`, en su propia base de datos.

### Sincronización

La central es la dueña de los datos maestros (países, empresa, sucursales, usuarios, roles, privilegios, catálogo, impuestos y listas de precios). Cada tienda recibe una copia de solo lectura.

- Cada cambio en un dato maestro se anota en `sync_change_log` con un trigger de PostgreSQL, dentro de la misma transacción. Los cambios se confirman en el mismo orden en que se numeran (ver "Decisiones: paso 7").
- Cada ciclo de la tienda aplica los cambios en transacciones y los confirma. Si la central no responde, la tienda sigue vendiendo con sus datos locales y reintenta en el siguiente ciclo.
- La carga inicial es una copia completa de los datos maestros. Si se interrumpe, se vuelve a empezar.
- Cada ciclo envía un latido. Una tienda sin latido durante dos minutos aparece como fuera de sincronía.
- **Purga:** cada día a las 03:00 la central borra del registro los cambios que todas las tiendas activas ya descargaron y que tienen más de 30 días (`pos.sync.purge-cron`). Si una tienda pide cambios que ya no existen, la central responde `409 SNAPSHOT_REQUIRED`, la tienda vuelve a cargar la copia completa en su siguiente ciclo.
- **Panel:** `GET /api/v1/store-nodes` en la central. Cada tienda muestra `latestVersion`, `lastDownloadVersion`, `pendingChanges`, `connected` y `syncState` (`ACTIVE`, `OUT_OF_SYNC`, `PENDING` o `DISABLED`).
- **Estado de la tienda:** `GET /api/v1/sync/status` muestra `lastAppliedVersion`, `snapshotCompletedAt`, `lastSyncAt` y `lastSyncError`. Nunca muestra el token.
- Intervalo: `pos.sync.interval` (30 segundos por defecto).

## Flujo de trabajo

- No se hace push directo a `main`.
- Cada paso del MVP se desarrolla en su propia rama `feature/step-N-<nombre>`.
- Al terminar el paso se abre un Pull Request hacia `main`, con la descripción de los requerimientos cubiertos y cómo probarlos. El CI de GitHub Actions debe pasar antes de unirlo.
