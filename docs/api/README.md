# Colección de pruebas de la API (Hoppscotch)

Esta carpeta contiene una colección de Hoppscotch con todos los endpoints actuales de la API y dos entornos para probarla contra la central y la tienda 1.

| Archivo | Qué es |
|---|---|
| `pos-api.hoppscotch.json` | Colección "POS API" con 100 peticiones en 10 carpetas. |
| `pos-local.hoppscotch-env.json` | Entorno "POS local (central)", apunta a `http://192.168.56.10:8080`. |
| `pos-store1.hoppscotch-env.json` | Entorno "POS local (tienda 1)", apunta a `http://192.168.56.11:8080`. |

## Requisitos

- Hoppscotch (la versión web o la aplicación de escritorio).
- Hoppscotch web necesita la extensión del navegador o la aplicación de escritorio para llamar a la IP de la máquina virtual (`192.168.56.10`, `192.168.56.11` y `192.168.56.12`). Sin eso, las peticiones fallan por CORS o por red.
- El entorno local arriba: `vagrant up` y `docker compose up -d` dentro de la VM.

## Importar

1. **Colección:** en Hoppscotch, ve a *Collections → Import* y elige *Import from Hoppscotch*. Selecciona `pos-api.hoppscotch.json`.
2. **Entornos:** ve a *Environments → Import* y selecciona `pos-local.hoppscotch-env.json` y `pos-store1.hoppscotch-env.json`.
3. En la parte superior de Hoppscotch, selecciona el entorno que vayas a usar: *POS local (central)* para la central o *POS local (tienda 1)* para la tienda.

## Preparar el administrador

1. Obtén la contraseña inicial del administrador desde el log de arranque de la central:

   ```
   vagrant ssh -c "cd /vagrant && docker compose logs central-api | grep -i password"
   ```

   La contraseña se muestra una sola vez. Si `POS_ADMIN_INITIAL_PASSWORD` estaba definida en `.env`, usa ese valor. En local con Vagrant es `Admin.Local.2026` (viene de `.env.example`).

2. En el entorno, pon la contraseña en la variable `adminPassword`.
3. Ejecuta **2. Sesión → Login**. Las variables `accessToken` y `refreshToken` se llenan solas.
4. Ejecuta **2. Sesión → Cambiar contraseña**. El sistema obliga a cambiarla en el primer ingreso y, mientras no se cambie, los demás endpoints responden 403.
5. Pon la nueva contraseña también en `adminPassword`. Así el siguiente login funciona.

## Cómo recorrer la colección

- Sigue las carpetas en orden, de la 1 a la 10. Cada petición de creación guarda el `id` en el entorno para usarlo en las siguientes.
- **Token:** el token de acceso dura 15 minutos. Cuando una petición responda 401, ejecuta **2. Sesión → Refrescar token**.
- **Sucursales visibles:** `GET /branches` muestra todas las sucursales en la central, y solo la asignada a la tienda en una tienda. Para que el usuario admin entre a una tienda, ejecuta **4. Organización → Asignar sucursales al usuario admin** (`user_branch` solo decide en qué tienda puede iniciar sesión). La variable `userId` se toma de **2. Sesión → Mi perfil**.
- **Carpeta 8:** son peticiones que deben fallar a propósito. El nombre indica el estado esperado (por ejemplo, `422 SKU repetido`). Si una de ellas no falla, revisa los datos creados antes.
- **Logout:** deja la petición **Logout** para el final. Revoca el refresh token.

## Tienda 1 (`192.168.56.11:8080`)

- En la tienda, catálogo, precios, usuarios, roles y privilegios son solo lectura. Las escrituras responden **403** con `code: READ_ONLY_NODE`. Eso es lo esperado.
- Las lecturas funcionan igual que en la central, siempre que la tienda ya tenga los datos sincronizados desde la central.
- Las peticiones a la tienda usan el token de la tienda (`storeAccessToken`), no el de la central. Ejecuta **7. Tiendas → Login en la tienda 1** antes de **Productos en la tienda 1**. La contraseña del administrador es la misma que en la central, porque el usuario se replica desde ella.
- **Estado de sincronización (tienda)** y **Listar tiendas** (panel de la central) sirven para ver si la tienda está al día. Ver la sección "Sincronización" del README principal.

## Idioma de los mensajes

La variable `lang` está en `es`. Cámbiala a `en` para recibir los mensajes de error en inglés. El encabezado `Accept-Language` se envía desde la variable.

## Notas

- La salud del servicio está en `/actuator/health`, fuera de `/api/v1`.
- Las contraseñas de prueba (`Cajero2026-seguro`, etc.) son solo para el entorno local. No las uses en otro lado.
