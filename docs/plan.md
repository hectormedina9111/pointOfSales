# Sistema Punto de Venta — Plan del Backend

Creado: 16 de octubre de 2026 · Actualizado: 2 de noviembre de 2026

## Resumen y alcance

La fase 1 entrega un backend REST de punto de venta (POS) en Spring Boot 4.1.1 y Java 25, organizado como monolito modular al estilo de Salespoint. El frontend queda para una fase posterior y consumirá esta API. Decisión del 24 de noviembre de 2026: el frontend se hará en Angular, como proyecto aparte, cuando el backend esté completo.

**Objetivo.** Registrar ventas en caja de forma rápida, descontar inventario automáticamente, emitir comprobantes y llevar el control de caja, usuarios y reportes básicos.

**Dentro del alcance de la fase backend**

- Catálogo de productos, categorías e impuestos.
- Inventario por sucursal, con movimientos y alertas de stock bajo.
- Ventas (órdenes), líneas, descuentos, pagos mixtos, anulaciones y devoluciones.
- Clientes, sesiones de caja (apertura, arqueo, cierre) y comprobantes fiscales.
- Usuarios, roles, privilegios, auditoría y reportes de ventas. Listas de precios configurables y venta a crédito con cuentas por cobrar. Un servidor por tienda que sigue vendiendo sin internet y se sincroniza con la central.

**Fuera de alcance por ahora**

- Interfaz de usuario, impresión física de tickets y lectores de código de barras (los manejará el frontend).
- Contabilidad completa, nómina, compras a proveedores con cuentas por pagar y tienda en línea.
- Integraciones con terminales de tarjeta, ERP o contabilidad externa. Réplica directa entre tiendas: cada tienda solo se comunica con la central.

**Supuestos de trabajo** (se confirman en Preguntas abiertas)

- Comercio minorista de productos y servicios, con muchas sucursales y varias cajas por sucursal. Precios siempre con ISV incluido.
- Opera en Honduras: moneda Lempira (HNL), impuesto ISV y facturación SAR, como primer país. El sistema es multipaís: cada país se implementa como un módulo fiscal independiente.
- Base de datos PostgreSQL.

## Referencia: Salespoint

Tomamos de [Salespoint](https://github.com/st-tu-dresden/salespoint) el modelo de dominio y la forma de organizar el código, no la dependencia en sí. Salespoint (versión [11.0.0](https://st.inf.tu-dresden.de/SalesPoint/api/)) es un framework académico de la TU Dresden pensado para aplicaciones web con Thymeleaf; nosotros necesitamos una API REST, impuestos locales, cajas y comprobantes fiscales que él no trae.

Su [pom.xml](https://github.com/st-tu-dresden/salespoint/blob/main/pom.xml) confirma la base técnica que replicaremos: Spring Boot 4.1.1, Java 25, Spring Modulith 2.1.1, jMolecules (DDD), JPA y Moneta para montos monetarios.

| Módulo Salespoint | Qué aporta | Cómo lo adaptamos |
| --- | --- | --- |
| `catalog` | `Product` con nombre, precio y categorías | Agregamos SKU, código de barras, costo, impuesto y unidad de medida |
| `inventory` | `UniqueInventoryItem` (un stock por producto) y `MultiInventoryItem` (varios almacenes); evento `StockShort` | Usamos el enfoque multi: stock por producto y sucursal, más una tabla de movimientos |
| `order` | `Order`, `OrderLine`, `ChargeLine`, `Cart`; estados y eventos `OrderPaid`, `OrderCompleted`, `OrderCanceled` | Se modela como `Sale`; añadimos descuentos, impuestos por línea, devoluciones y vínculo con caja |
| `payment` | Métodos de pago (efectivo, tarjeta, cheque) | Pagos múltiples por venta (pago mixto) con referencia y vuelto |
| `accountancy` | Asientos generados al pagar una orden | Se reemplaza por movimientos de caja y reportes; contabilidad completa queda fuera |
| `useraccount` | Cuentas y roles sobre Spring Security | Igual, pero con JWT para API REST y roles de POS |
| `time` | `BusinessTime`, un reloj de negocio ajustable | Lo conservamos para pruebas y cierres de día |
| `quantity` | `Quantity` con unidad de medida | Lo conservamos para productos por peso o volumen |

La idea clave que heredamos: cada módulo publica eventos (venta pagada, venta anulada) y los demás reaccionan. Así el inventario se descuenta sin que el módulo de ventas lo conozca.

## Requerimientos funcionales

Son 50 requerimientos en 15 módulos; la prioridad indica si entran en el MVP (Alta) o en una iteración posterior.

| ID | Módulo | Requerimiento | Prioridad |
| --- | --- | --- | --- |
| RF-01 | Seguridad | Iniciar sesión con usuario y contraseña; recibir token JWT y token de refresco | Alta |
| RF-02 | Seguridad | Gestionar usuarios (crear, editar, desactivar, restablecer contraseña) | Alta |
| RF-03 | Seguridad | Asignar roles: Administrador, Gerente, Cajero, Bodeguero | Alta |
| RF-04 | Seguridad | Exigir autorización de Gerente para anular ventas y aplicar descuentos sobre un límite | Media |
| RF-05 | Organización | Configurar datos de la empresa (nombre, RTN, dirección, moneda) | Alta |
| RF-06 | Organización | Gestionar sucursales y las cajas (terminales) de cada una | Alta |
| RF-07 | Catálogo | Crear, editar y desactivar productos con SKU, código de barras, precio, costo e impuesto | Alta |
| RF-08 | Catálogo | Organizar productos en categorías jerárquicas | Alta |
| RF-09 | Catálogo | Buscar productos por nombre, SKU o código de barras en menos de 300 ms | Alta |
| RF-10 | Catálogo | Vender por unidad, peso o volumen (unidad de medida y decimales) | Media |
| RF-11 | Catálogo | Configurar tasas de impuesto (exento, 15 %, 18 %) | Alta |
| RF-12 | Inventario | Consultar existencias por producto y sucursal | Alta |
| RF-13 | Inventario | Descontar stock automáticamente al pagar una venta y reponerlo al anularla o devolverla | Alta |
| RF-14 | Inventario | Registrar entradas, salidas y ajustes manuales con motivo | Alta |
| RF-15 | Inventario | Alertar cuando el stock baje del mínimo configurado | Media |
| RF-16 | Inventario | Transferir mercadería entre sucursales | Baja |
| RF-17 | Ventas | Crear una venta, agregar y quitar líneas, y calcular subtotal, impuestos y total | Alta |
| RF-18 | Ventas | Aplicar descuentos por línea o a la venta completa (monto o porcentaje) | Alta |
| RF-19 | Ventas | Cobrar con uno o varios métodos de pago y calcular el vuelto | Alta |
| RF-20 | Ventas | Poner una venta en espera y retomarla | Media |
| RF-21 | Ventas | Anular una venta pagada, con motivo y autorización | Alta |
| RF-22 | Ventas | Registrar devoluciones parciales o totales como documentos RETURN con los mismos campos que la venta, y emitir nota de crédito | Media |
| RF-23 | Ventas | Impedir vender más de lo disponible, salvo que la sucursal lo permita | Media |
| RF-24 | Clientes | Registrar clientes con nombre, RTN, teléfono y correo; usar "Consumidor final" por defecto | Alta |
| RF-25 | Clientes | Consultar el historial de compras de un cliente | Baja |
| RF-26 | Caja | Abrir una sesión de caja con monto inicial | Alta |
| RF-27 | Caja | Registrar entradas y retiros de efectivo con motivo | Alta |
| RF-28 | Caja | Cerrar la sesión con arqueo: esperado vs. contado por método de pago | Alta |
| RF-29 | Caja | Impedir ventas si el cajero no tiene una sesión abierta | Alta |
| RF-30 | Facturación | Configurar rangos autorizados (CAI, rango inicial y final, fecha límite) por caja | Alta |
| RF-31 | Facturación | Asignar el número correlativo fiscal al pagar y bloquear el rango agotado o vencido | Alta |
| RF-32 | Facturación | Generar el comprobante en PDF y JSON para que el frontend lo imprima | Media |
| RF-33 | Reportes | Ventas por día, sucursal, cajero, producto y método de pago | Alta |
| RF-34 | Reportes | Productos más vendidos y utilidad bruta (precio menos costo) | Media |
| RF-35 | Reportes | Exportar reportes a CSV | Baja |
| RF-36 | Auditoría | Registrar quién hizo qué y cuándo en acciones sensibles (anulaciones, ajustes, precios) | Alta |
| RF-37 | Seguridad | Definir privilegios de tres tipos (endpoint del backend, menú del frontend y acción especial como imprimir reportes) y asignarlos a roles | Alta |
| RF-38 | Seguridad | Entregar al frontend el menú y las acciones que el usuario tiene permitidas | Alta |
| RF-39 | Integración | Guardar el código externo (ERP) de usuarios y clientes, y permitir buscarlos por ese código | Alta |
| RF-40 | Clientes | Registrar el nombre de personas en cuatro campos (primer y segundo nombre, primer y segundo apellido) o la razón social si es empresa | Alta |
| RF-41 | Ventas | Impedir mezclar en una misma venta un producto de factura exclusiva con otros productos | Alta |
| RF-42 | Inventario | Registrar los seriales al ingresar productos seriados, exigir el serial al venderlos y rastrear cada serial (estado, venta, garantía) | Alta |
| RF-43 | Recibos | Emitir recibos no fiscales y sus devoluciones, con numeración propia por caja, separados de las facturas | Alta |
| RF-44 | Fiscal | Operar en varios países, con moneda, impuestos y facturación propios de cada uno; la facturación de cada país es un módulo independiente | Alta |
| RF-45 | Precios | Crear listas de precios configurables (precio fijo o porcentaje, vigencia, escalas por cantidad) y asignarlas a sucursales y clientes | Alta |
| RF-46 | Crédito | Vender al crédito respetando el límite del cliente, generar cuentas por cobrar con vencimiento y registrar abonos | Media |
| RF-47 | Crédito | Reportes de antigüedad de saldos y estado de cuenta por cliente | Media |
| RF-48 | Sin conexión | Cada tienda opera con su propio servidor y sigue vendiendo sin internet; sincroniza con la central al volver la conexión | Media |
| RF-49 | Sincronización | Dar de alta una tienda con un código de activación de un solo uso, sin configurar IP ni rangos a mano, y revocar su acceso desde la central | Alta |
| RF-50 | Sincronización | Mostrar en la central el estado de sincronización de cada tienda (cambios por descargar, registros por subir, cuadre diario) y alertar retrasos o descuadres | Alta |

## Requerimientos no funcionales

La caja no puede esperar: el criterio que manda es que cobrar sea rápido y que nunca se pierda ni se duplique una venta.

| ID | Categoría | Requerimiento | Meta medible |
| --- | --- | --- | --- |
| RNF-01 | Rendimiento | Respuesta de búsqueda de producto y de agregar línea | p95 < 300 ms |
| RNF-02 | Rendimiento | Confirmar el pago de una venta | p95 < 800 ms |
| RNF-03 | Capacidad | Cajas trabajando a la vez sin degradarse | 50 cajas, 20 ventas/min cada una |
| RNF-04 | Integridad | Montos con `BigDecimal` / `NUMERIC(14,2)`, nunca `double`; redondeo HALF\_UP | 0 diferencias de centavos en pruebas |
| RNF-05 | Integridad | Pago, número fiscal y descuento de stock en una sola transacción | Sin ventas pagadas sin número fiscal |
| RNF-06 | Integridad | Idempotencia en el cobro con encabezado `Idempotency-Key` | Un reintento no crea un segundo pago |
| RNF-07 | Concurrencia | Bloqueo optimista (`@Version`) en stock y bloqueo pesimista en el correlativo fiscal | Sin números fiscales repetidos |
| RNF-08 | Seguridad | JWT de vida corta, contraseñas con BCrypt o Argon2, HTTPS obligatorio | Token de acceso de 15 min |
| RNF-09 | Seguridad | Autorización por rol y por sucursal en cada endpoint | Pruebas de acceso por rol |
| RNF-10 | Auditoría | Columnas de creación y modificación en todas las tablas; borrado lógico | 100 % de tablas de negocio |
| RNF-11 | Disponibilidad | Respaldos diarios de base de datos con restauración probada | Restauración mensual verificada |
| RNF-12 | Observabilidad | Logs JSON con id de correlación, métricas en Actuator y trazas OpenTelemetry | Panel con ventas/min y errores |
| RNF-13 | Mantenibilidad | Módulos sin dependencias cíclicas, verificados con Spring Modulith en las pruebas | Prueba de arquitectura en CI |
| RNF-14 | Calidad | Cobertura de pruebas en reglas de negocio | ≥ 80 % en módulos de dominio |
| RNF-15 | Documentación | Contrato de API publicado con OpenAPI 3 | Swagger UI disponible en ambientes no productivos |
| RNF-16 | Localización | Zona horaria, moneda e idioma configurables | Predeterminado: America/Tegucigalpa, HNL, es |
| RNF-17 | Portabilidad | Ejecución en contenedor Docker | Arranque con `docker compose up` |

## Stack tecnológico

Usaremos Spring Boot 4.1.1, la versión estable vigente según [spring.io](https://spring.io/projects/spring-boot), sobre Java 25, igual que Salespoint. La 4.2.0 está en fase milestone; no la usamos hasta que salga estable.

| Capa | Tecnología | Versión | Motivo |
| --- | --- | --- | --- |
| Lenguaje | Java (LTS) | 25 | Base de Spring Boot 4 y de Salespoint |
| Framework | Spring Boot | 4.1.1 | Estable actual; Spring Framework 7 |
| Arquitectura | Spring Modulith | 2.1.1 | Módulos verificados, eventos entre módulos con registro persistente |
| Dominio | jMolecules | 2025.0.2 | Anotaciones DDD (agregados, entidades, objetos de valor) |
| Web | Spring Web MVC + Bean Validation | incluida | API REST síncrona; más simple que WebFlux para un POS |
| Persistencia | Spring Data JPA (Hibernate) | incluida | Mismo enfoque que Salespoint |
| Base de datos | PostgreSQL | 17 o superior | Transacciones fuertes, `NUMERIC` exacto, JSONB para auditoría |
| Migraciones | Flyway | incluida | Esquema versionado y reproducible |
| Dinero | `BigDecimal` + Moneta (JSR 354) | 1.4.5 | Montos exactos; Moneta solo en el dominio |
| Seguridad | Spring Security + OAuth2 Resource Server (JWT) | incluida | Autenticación sin estado para la API |
| Mapeo | MapStruct | última estable | DTOs sin código repetitivo |
| Documentación API | springdoc-openapi | compatible con Boot 4 | Swagger UI y contrato OpenAPI 3 |
| PDF | OpenPDF o JasperReports | por decidir | Comprobantes y reportes |
| Observabilidad | Actuator + Micrometer + OpenTelemetry | incluida | Métricas y trazas |
| Pruebas | JUnit 5, Testcontainers, Spring Modulith Test, ArchUnit | incluida | Pruebas con PostgreSQL real y de arquitectura |
| Build | Maven (wrapper) | 3.9+ | Igual que Salespoint |
| Despliegue | Docker + Docker Compose | — | Ambiente local y de producción iguales |

Las versiones marcadas "incluida" las fija el BOM de Spring Boot; antes de iniciar el código se confirman en [start.spring.io](https://start.spring.io/).

## Arquitectura propuesta

Proponemos un monolito modular con Spring Modulith: una sola aplicación desplegable, dividida en 8 módulos con fronteras verificadas por pruebas. Es el mismo estilo de Salespoint y evita la complejidad de microservicios mientras el sistema es pequeño.

> Diagrama: arquitectura · 8 módulos en un monolito modular. Ver la versión en línea del documento.

Al cobrar, Ventas valida la sesión de Caja y pide el número a Facturación dentro de la misma transacción; el inventario se descuenta después, al escuchar el evento.

**Estructura de paquetes**: un paquete raíz por módulo (`com.medinasystems.pos.sales`, `….inventory`, etc.). Dentro de cada uno, solo la API pública del módulo queda en el paquete raíz; el resto va en subpaquetes internos que otros módulos no pueden usar.

**Idioma**: todo el código va en inglés (paquetes, clases, métodos, variables, tablas, columnas, rutas de la API, JSON, valores de enumeración, mensajes de commit y nombres de pruebas). Solo la documentación, como este plan, va en español. Los mensajes que ve el usuario final, incluidos los de error, se sacan a archivos de traducción en inglés y en español (`messages.properties` y `messages_es.properties`); cada error de negocio tiene un código fijo y su texto se busca con la llave `error.<CODE>`. El idioma lo decide el encabezado `Accept-Language`; si no viene, se responde en español.

**Capas dentro de cada módulo**

1. `web`: controladores REST y DTOs de entrada y salida.
2. `application`: servicios que orquestan casos de uso y abren transacciones.
3. `domain`: entidades, agregados, objetos de valor y reglas de negocio (anotados con jMolecules).
4. `infrastructure`: repositorios JPA y adaptadores externos (PDF, correo).

**Reglas de diseño**

- Un módulo nunca accede a las tablas de otro; usa su servicio público o escucha sus eventos.
- Los eventos se guardan en `event_publication` antes de entregarse, así que sobreviven a un reinicio y se reintentan.
- Las reglas de dinero viven en el dominio (`Sale.calculateTotals()`), no en los controladores.
- Seguridad sin estado: token de acceso JWT de 15 minutos y token de refresco guardado en base de datos para poder revocarlo.
- Si en el futuro un módulo necesita escalar por separado (por ejemplo, reportes), se puede extraer como servicio gracias a que ya se comunica por eventos.

### Facturación multipaís

La facturación vive en un módulo `fiscal` que expone una sola interfaz, `FiscalProvider`, con operaciones como emitir factura, emitir nota de crédito, anular y obtener datos para imprimir. Cada país es una implementación separada, en su propio módulo Maven, que se activa según `country.fiscal_provider`:

- `fiscal-hn` (Honduras, SAR): asigna el correlativo del rango CAI de la caja dentro de la transacción de cobro. Es la primera implementación.
- `fiscal-none`: para países o sucursales sin requisito fiscal; solo numera internamente.
- Futuras (`fiscal-gt`, `fiscal-sv`…): factura electrónica que envía el documento a la autoridad. Como esa validación es remota, se hace por evento después del cobro y `sale.fiscal_status` pasa de PENDING a ISSUED o REJECTED.

Así agregar un país no toca ventas, caja ni inventario: se compila el módulo nuevo, se registra el país y se configuran sus impuestos.

### Arquitectura central y tienda

Las redes fallan con frecuencia, así que cada tienda tiene su propio servidor. Las cajas trabajan contra él por la red local y siguen vendiendo aunque se caiga internet; la central consolida los datos de todas las tiendas cuando hay conexión.

> Diagrama: arquitectura central y tienda · un servidor por sucursal. Ver la versión en línea del documento.

Si falla internet, la tienda no lo nota: solo se atrasa la consolidación en la central. Si falla el servidor de la tienda, las cajas se detienen, por eso ese equipo lleva UPS y respaldo local.

**Un solo backend, dos perfiles.** Es la misma aplicación Spring Boot y el mismo código Java, desplegado con el perfil `central` o con el perfil `store`. El perfil decide qué módulos aceptan escrituras y hacia dónde se sincroniza. No hace falta otro lenguaje.

**Módulos por perfil.** Cada regla de negocio se escribe una sola vez, en su módulo, y el perfil decide dónde se ejecuta. La tienda lleva la lógica de la operación frente al cliente; la central lleva la de administración y consolidación.

| Módulo | Perfil tienda | Perfil central |
| --- | --- | --- |
| Ventas, recibos, pagos, caja | Lógica completa: precios, descuentos, impuestos, totales, cobro, arqueo | Solo recibe y almacena |
| Inventario | Lógica completa de su sucursal: stock, movimientos, seriales | Recibe y almacena los movimientos; gestiona las transferencias entre tiendas |
| Catálogo, precios, impuestos | Solo lectura | Lógica completa: crear, validar y programar cambios con vigencia |
| Usuarios, roles, privilegios | Solo lectura | Lógica completa |
| Fiscal | Consume el rango CAI de sus cajas | Registra rangos y vigila consumo y vencimiento |
| Crédito y cuentas por cobrar | Consulta saldo y registra abonos | Dueña de límites, saldos y aplicación de abonos |
| Clientes | Crea clientes nuevos | Consolida y detecta duplicados |
| Reportes | De su sucursal | Consolidados de todas las sucursales |
| Sincronización | Cliente: latido, bajada, subida | Servidor: alta de tiendas, cambios, cuadre diario, alertas |

**La central no revalida la operación de las tiendas.** Una venta ya cobrada pasó por las reglas de negocio en la tienda; repetirlas en la central no podría deshacerla y solo duplicaría lógica. La central guarda los documentos transaccionales tal cual llegan y solo controla la transmisión: que cada documento llegue completo (con sus líneas y pagos, guardado en una sola transacción) y que no se duplique si llega dos veces (se guarda por id). Si algo se pierde en el camino, lo detectan el conteo de pendientes y el cuadre diario.

**Cada dato tiene un dueño.** Solo el dueño puede crear o modificar un registro; los demás reciben una copia de lectura. Esto evita casi todos los conflictos.

| Datos | Dueño | Sentido |
| --- | --- | --- |
| Países, empresa, sucursales, cajas, usuarios, roles, privilegios | Central | Baja a todas las tiendas |
| Catálogo, impuestos, listas de precios | Central | Baja a todas las tiendas |
| Clientes y condiciones de crédito | Central (las tiendas pueden crear clientes) | Baja a todas; un cliente nuevo de tienda sube y luego se reparte |
| Ventas, recibos, pagos, sesiones y movimientos de caja | Tienda | Sube a la central |
| Stock, movimientos de inventario y seriales de su sucursal | Tienda | Sube a la central |
| Rangos fiscales (CAI) de sus cajas | Central los registra, la tienda los consume | Baja; el consumo sube |
| Cuentas por cobrar y abonos | Central | Abonos hechos en tienda suben |

**Ids: un solo BIGINT, con rango definido en la central.** Todas las tablas usan un `id BIGINT` simple. Para que dos tiendas nunca generen el mismo id, la central reparte rangos y nadie los configura a mano:

1. **Registro.** Un administrador da de alta la tienda en la central. La central crea su fila en `store_node` con el siguiente `node_number` libre y calcula su rango: la central usa del 1 al 999 999 999, la tienda 1 desde 1 000 000 001, la tienda 2 desde 2 000 000 001, y así sucesivamente.
2. **Réplica.** En la primera sincronización, la tienda descarga su fila de `store_node` junto con el resto de los datos de la central.
3. **Ajuste automático.** Al arrancar, el backend de la tienda lee su rango y mueve sus secuencias de PostgreSQL al inicio del rango si todavía no están ahí. Desde ese momento, todo lo que crea la tienda cae dentro de su rango.
4. **Control.** La central vigila cuánto de su rango ha usado cada tienda y avisa al acercarse al final. Con mil millones de ids por tienda no debería pasar, pero si ocurre, la central le asigna un rango nuevo por el mismo camino.

En pantalla, el id se puede mostrar como `3-1042` (número de tienda y correlativo dentro de su rango), calculado a partir del `id`, sin guardar una columna más.

**Sincronización**

1. **Subida (tienda a central).** Cada venta, recibo, movimiento de caja o de inventario se guarda en la tienda junto con un evento en `event_publication` (Spring Modulith), dentro de la misma transacción. Un proceso de la tienda envía esos eventos a la central en lotes y los marca como enviados solo cuando la central confirma. Si no hay internet, se acumulan y salen al volver.
2. **Bajada (central a tienda).** Cada cambio en datos de la central se anota en `sync_change_log` con un número de versión creciente. La tienda pide los cambios desde la última versión que aplicó.
3. **Idempotencia.** Como el id ya es único entre tiendas, la central aplica cada registro con un upsert por id: recibir dos veces la misma venta no la duplica.
4. **Transferencias entre sucursales.** La tienda de origen registra la salida; la central la reenvía a la tienda destino como entrada pendiente, que se confirma al recibir la mercadería.

**Lo que necesita conexión con la central**

- Venta a crédito: el saldo del cliente vive en la central. Sin conexión, la tienda solo puede vender a crédito dentro de un cupo asignado (ver Preguntas abiertas).
- Factura electrónica en países que validan en línea: se emite en contingencia y se certifica al volver internet. En Honduras no aplica, porque el rango CAI ya es local.
- Reportes consolidados de todas las sucursales.

**Operación de las tiendas**

- Hardware: una mini PC con 8 a 16 GB de RAM, disco SSD y UPS. Si se necesita menos memoria, el backend se puede compilar como imagen nativa con GraalVM.
- Instalación con Docker Compose (backend + PostgreSQL) y actualizaciones remotas desde la central.
- Respaldo diario de la base local, además de la copia que ya vive en la central.
- Monitoreo en la central: alerta si una tienda pasa más de cierto tiempo sin sincronizar.

### Alta de tiendas y réplica de datos maestros

**La tienda siempre inicia la comunicación.** La central tiene una dirección fija y conocida (por ejemplo `central.empresa.com`); las tiendas no necesitan IP pública, puertos abiertos ni VPN. Así funciona aunque el internet de la tienda tenga IP dinámica, y el servidor de la tienda no queda expuesto a internet. La central nunca necesita saber la IP de una tienda.

**Alta de una tienda nueva**

1. Un administrador registra la tienda en la central. La central crea su `store_node` (número y rango de ids) y genera un código de activación de un solo uso que vence en 24 horas.
2. El instalador de la tienda (Docker Compose) pide dos datos: la dirección de la central y el código de activación.
3. La tienda canjea el código por credenciales permanentes propias. Desde ese momento la central sabe quién es en cada conexión y puede revocar esas credenciales, por ejemplo si roban el equipo.
4. La tienda descarga la carga inicial completa: su `store_node`, catálogo, precios, impuestos, usuarios, clientes y los rangos CAI de sus cajas. Al terminar, ajusta sus secuencias a su rango y queda lista para vender.

**Ciclo de sincronización**

Cada 30 a 60 segundos, la tienda hace este ciclo:

1. **Latido:** informa a la central su versión aplicada, cuántos registros tiene por subir, desde cuándo está pendiente el más antiguo y su versión de software.
2. **Bajada:** pide los cambios desde su última versión aplicada y recibe solo lo que le corresponde (datos generales y los de su sucursal).
3. **Aplicación:** aplica el lote en una sola transacción y confirma a la central la versión aplicada.
4. **Subida:** envía sus ventas, recibos, cajas y movimientos pendientes; los marca como enviados solo cuando la central confirma que los guardó.

Para que los cambios lleguen en segundos y no en un minuto, la tienda además mantiene abierta una conexión permanente (WebSocket) hacia la central. Por ahí la central avisa "hay cambios nuevos" y la tienda adelanta el ciclo. Como la conexión la abre la tienda, sigue sin hacer falta IP fija. Si la conexión se cae, el ciclo periódico sigue funcionando como respaldo, y al reconectar la tienda pide todo lo que le falte desde su última versión.

**Qué viaja en cada cambio**

- Si se modifica un campo de un producto, viaja ese producto completo (aproximadamente 1 KB), no todo el catálogo ni solo el campo. Enviar el registro completo es igual de liviano y más seguro: si un mensaje se repite o llega desordenado, la tienda sobrescribe con la versión más reciente y queda correcta.
- Si un registro cambió varias veces entre dos ciclos, viaja una sola vez, con su estado final.
- Las respuestas van comprimidas con gzip y paginadas en bloques de hasta 2 000 registros.
- La carga para la central es mínima: una consulta sin cambios responde vacía en milisegundos, y 100 tiendas consultando cada minuto suman menos de 2 peticiones por segundo.

**Cambios masivos**

Estimación para 100 000 productos completos (unos 100 MB, unos 10 MB comprimidos), que habrá que confirmar en las pruebas de carga de la fase 6:

| Etapa | Internet de 10 Mbps | Internet de 2 Mbps |
| --- | --- | --- |
| Descarga | ≈10 segundos | ≈45 segundos |
| Preparación de bloques en la central | ≈20 a 40 segundos | igual |
| Aplicación en la base de la tienda | ≈20 a 60 segundos | igual |
| Total por tienda | ≈1 a 2 minutos | ≈2 a 3 minutos |

Reglas para estos casos:

- Una repreciación viaja como filas de `price_list_item`, mucho más livianas que productos completos, y baja a pocos segundos.
- La tienda descarga todos los bloques a una tabla temporal y los aplica juntos en una sola transacción. Mientras descarga, sigue vendiendo con los datos anteriores, y nunca queda a medias.
- Los cambios grandes se publican fuera del horario de venta o con fecha de vigencia (`price_list.valid_from`): la tienda los descarga con anticipación y entran en vigor a la hora indicada.

**Cómo se sabe que todo está sincronizado**

- **Bajada:** cada tienda reporta su versión aplicada (`store_node.last_download_version`). Si la central va en 9 050 y la tienda en 9 000, le faltan 50 cambios. Para un cambio concreto, la central sabe qué tiendas ya tienen esa versión o una mayor.
- **Subida:** cada tienda reporta cuántos registros tiene por subir y desde cuándo (`pending_upload_count`, `oldest_pending_at`). Cero pendientes significa que la central tiene todo lo de esa tienda.
- **Cuadre diario:** al cierre de cada día, la tienda envía por caja la cantidad y el total de ventas y recibos. La central lo compara con lo que recibió y lo guarda en `store_daily_reconciliation`; si no coincide, marca el día y pide a la tienda reenviarlo.
- **Panel y alertas:** la central muestra una fila por tienda (última conexión, cambios por descargar, registros por subir, estado del cuadre) y alerta si una tienda pasa demasiado tiempo sin conectarse o si un cuadre no coincide.

Todo esto se construye con Spring Boot y PostgreSQL. Herramientas como Kafka, RabbitMQ o Debezium se pueden incorporar si algún día hay cientos de tiendas o requisitos de tiempo real estrictos, sin cambiar el modelo de datos.

## Modelo de datos

Son 40 tablas en PostgreSQL (más una que administra Spring Modulith), agrupadas por módulo; cada módulo es dueño de sus tablas y los demás solo guardan su id. Nombres de tablas, columnas y valores de enumeración van en inglés; las descripciones, en español.

**Convenciones para todas las tablas**

- Clave primaria `id BIGINT GENERATED BY DEFAULT AS IDENTITY`: incremental, corta y fácil de mostrar en pantallas, formularios y reportes. Todas las llaves foráneas son `BIGINT`. La central asigna a cada tienda un rango de mil millones al registrarla y se lo replica; la tienda ajusta sus secuencias sola al arrancar, así los ids nunca chocan al sincronizar (ver Arquitectura central y tienda). Los números de negocio (`internal_number`, `fiscal_number`, `sku`) siguen siendo lo que se imprime en documentos.
- Montos en `NUMERIC(14,2)`, cantidades en `NUMERIC(12,3)`, tasas en `NUMERIC(5,2)`.
- Fechas en `TIMESTAMPTZ` (UTC en base de datos, zona local en la API).
- Columnas de auditoría en todas: `created_at`, `created_by`, `updated_at`, `updated_by`, `version INT` (bloqueo optimista).
- Borrado lógico con `active BOOLEAN` en catálogos; las ventas nunca se borran, se anulan.
- Nombres en inglés, `snake_case`, tablas en singular. `user` es palabra reservada en PostgreSQL, por eso la tabla se llama `app_user`.
- `external_code` (código en el ERP) es opcional y único cuando existe.

### Diagramas de relaciones

Las 40 tablas se reparten en cuatro diagramas por tema. Una tabla con borde punteado es una referencia a otro diagrama, donde aparece con detalle. `audit_log` y `sync_change_log` no tienen llaves foráneas y no se dibujan; tampoco la tabla de control store\_daily\_reconciliation.

> Diagrama: relaciones · organización y seguridad, 11 tablas. Ver la versión en línea del documento.

La sucursal cuelga de la empresa y del país; el usuario no tiene permisos propios, los hereda de sus roles por `role_privilege`.

> Diagrama: relaciones · catálogo, precios e inventario, 10 tablas. Ver la versión en línea del documento.

Un producto toma su impuesto, categoría y unidad del catálogo; su precio puede venir de una lista, y su stock se lleva por sucursal, con un serial por unidad cuando aplica.

> Diagrama: relaciones · caja, ventas y recibos, 12 tablas. Ver la versión en línea del documento.

Ventas y recibos son espejo uno del otro; cada devolución es una fila del mismo documento que apunta a su original, y `payment` sirve a ambos.

> Diagrama: relaciones · crédito, 4 tablas. Ver la versión en línea del documento.

Un abono puede repartirse entre varias cuentas por cobrar del mismo cliente mediante `receivable_payment_allocation`.

Las tablas siguientes no repiten las columnas de auditoría.

### Organización

**country** (país; define moneda, zona horaria y qué módulo fiscal se usa)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| iso\_code | CHAR(2) | NOT NULL, UNIQUE | HN, GT, SV, NI, CR… |
| name | VARCHAR(80) | NOT NULL |  |
| currency | CHAR(3) | NOT NULL | HNL, GTQ, USD… |
| time\_zone | VARCHAR(50) | NOT NULL |  |
| tax\_id\_label | VARCHAR(20) | NOT NULL | Nombre local del identificador tributario: RTN, NIT, RUC… |
| fiscal\_provider | VARCHAR(30) | NOT NULL | Implementación fiscal: HN\_SAR, NONE; futuras: GT\_FEL, SV\_DTE… |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

**company** (empresa)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| legal\_name | VARCHAR(200) | NOT NULL | Razón social |
| trade\_name | VARCHAR(200) |  | Nombre comercial en el ticket |
| tax\_id | VARCHAR(14) | NOT NULL, UNIQUE | RTN |
| address | VARCHAR(300) |  |  |
| phone | VARCHAR(20) |  |  |
| email | VARCHAR(150) |  |  |
| currency | CHAR(3) | NOT NULL, DEFAULT HNL | Código ISO 4217 |
| time\_zone | VARCHAR(50) | NOT NULL, DEFAULT America/Tegucigalpa |  |

**branch** (sucursal)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| company\_id | BIGINT | FK company, NOT NULL |  |
| code | VARCHAR(10) | NOT NULL, UNIQUE | Ej.: 001 (forma parte del número fiscal) |
| name | VARCHAR(120) | NOT NULL |  |
| address | VARCHAR(300) |  |  |
| phone | VARCHAR(20) |  |  |
| allow\_negative\_stock | BOOLEAN | NOT NULL, DEFAULT false | Regla de RF-23 |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |
| country\_id | BIGINT | FK country, NOT NULL | País de la sucursal; decide moneda, impuestos y módulo fiscal |
| default\_price\_list\_id | BIGINT | FK price\_list, NULL | Lista de precios que usa la sucursal si el cliente no tiene una propia |

**cash\_register** (caja o punto de emisión)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| branch\_id | BIGINT | FK branch, NOT NULL |  |
| code | VARCHAR(10) | NOT NULL; UNIQUE con branch\_id | Punto de emisión, ej.: 001 |
| name | VARCHAR(80) | NOT NULL | Ej.: Caja 1 |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |
| receipt\_prefix | VARCHAR(20) | NOT NULL | Prefijo de los recibos de esta caja, ej.: REC-001-001- |
| last\_receipt\_number | BIGINT | NOT NULL, DEFAULT 0 | Último correlativo de recibo emitido; se bloquea al emitir |

### Seguridad y privilegios

**app\_user** (usuario)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| external\_code | VARCHAR(50) | UNIQUE, NULL | Código del usuario en el ERP |
| username | VARCHAR(50) | NOT NULL, UNIQUE |  |
| password\_hash | VARCHAR(255) | NOT NULL | BCrypt o Argon2 |
| full\_name | VARCHAR(150) | NOT NULL |  |
| email | VARCHAR(150) | UNIQUE |  |
| authorization\_pin\_hash | VARCHAR(255) |  | PIN de gerente para RF-04 |
| failed\_attempts | SMALLINT | NOT NULL, DEFAULT 0 | Bloqueo tras 5 intentos |
| last\_login\_at | TIMESTAMPTZ |  |  |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

**role** (rol)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(30) | NOT NULL, UNIQUE | ADMIN, MANAGER, CASHIER, WAREHOUSE\_CLERK |
| name | VARCHAR(80) | NOT NULL | Nombre para mostrar |
| description | VARCHAR(150) |  |  |

**user\_role**: `user_id` (FK app\_user, PK), `role_id` (FK role, PK).

**user\_branch**: `user_id` (FK app\_user, PK), `branch_id` (FK branch, PK). Limita a qué sucursales accede cada usuario. Las asigna el administrador; crear una sucursal no la asigna a nadie.

**privilege** (privilegio): una sola tabla guarda los tres tipos de permiso, distinguidos por `type`. Las columnas que no aplican a un tipo quedan en NULL; un `CHECK` exige las obligatorias de cada tipo.

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(80) | NOT NULL, UNIQUE | Ej.: `SALE_PAYMENT_CREATE`, `MENU_INVENTORY`, `REPORT_PRINT` |
| type | VARCHAR(10) | NOT NULL | ENDPOINT, MENU, ACTION |
| name | VARCHAR(120) | NOT NULL | Nombre legible para la pantalla de roles |
| description | VARCHAR(300) |  |  |
| module | VARCHAR(30) | NOT NULL | sales, inventory, catalog…; agrupa en pantalla |
| http\_method | VARCHAR(7) | Obligatorio si ENDPOINT | GET, POST, PUT, PATCH, DELETE |
| path\_pattern | VARCHAR(200) | Obligatorio si ENDPOINT; UNIQUE con http\_method | Ej.: `/api/v1/sales/{id}/payments` |
| parent\_id | BIGINT | FK privilege, NULL | Jerarquía de menús (menú y submenú) |
| frontend\_route | VARCHAR(200) | Obligatorio si MENU, salvo menús agrupadores | Ej.: `/inventory/stock` |
| icon | VARCHAR(50) |  | Nombre del ícono en el frontend |
| sort\_order | SMALLINT | DEFAULT 0 | Orden en el menú |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

Ejemplos de cada tipo: ENDPOINT `POST /api/v1/sales/{id}/cancellation`; MENU Inventario, con su submenú Existencias; ACTION Imprimir reporte de ventas, Reimprimir factura o Abrir cajón de dinero.

**role\_privilege**: `role_id` (FK role, PK), `privilege_id` (FK privilege, PK). Un usuario tiene la unión de los privilegios de todos sus roles.

Cómo se usa: los privilegios ENDPOINT se cargan como autoridades de Spring Security y un filtro compara método y ruta de cada petición; los MENU los consulta el frontend para armar su menú; los ACTION los verifica el código en puntos concretos (por ejemplo, antes de generar un PDF) y el frontend los usa para mostrar u ocultar botones. Los ENDPOINT se registran solos al arrancar, leyendo los controladores, para que no se olvide ninguno.

**Privilegios por defecto:** al arrancar, cada nodo registra los endpoints nuevos y se los asigna al rol ADMIN. Los demás roles (MANAGER, CASHIER, WAREHOUSE_CLERK) empiezan sin privilegios y se configuran desde `PUT /api/v1/roles/{id}/privileges`; cada módulo nuevo propone en su Pull Request los privilegios iniciales de cada rol. Los endpoints propios del usuario (`/api/v1/me/**`) y los públicos (`/api/v1/auth/**`, `/api/v1/system/**`) no requieren privilegio.

### Catálogo

**category** (categoría)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| parent\_id | BIGINT | FK category, NULL | Jerarquía |
| name | VARCHAR(100) | NOT NULL |  |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

**tax** (impuesto)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(20) | NOT NULL, UNIQUE | EXEMPT, ISV15, ISV18 |
| name | VARCHAR(80) | NOT NULL |  |
| rate | NUMERIC(5,2) | NOT NULL, CHECK ≥ 0 | 0.00, 15.00, 18.00 |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |
| country\_id | BIGINT | FK country, NOT NULL; UNIQUE con code | Cada país tiene sus propias tasas; un producto puede tener un impuesto distinto por país (ver product\_tax) |

**unit\_of\_measure** (unidad de medida)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(10) | NOT NULL, UNIQUE | UNIT, KG, LB, LT |
| name | VARCHAR(50) | NOT NULL |  |
| allows\_decimals | BOOLEAN | NOT NULL, DEFAULT false |  |

**product** (producto)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| sku | VARCHAR(40) | NOT NULL, UNIQUE | Código interno |
| barcode | VARCHAR(50) | UNIQUE, NULL | EAN/UPC; índice para escaneo |
| name | VARCHAR(200) | NOT NULL | Índice de texto (pg\_trgm) para búsqueda |
| description | TEXT |  |  |
| category\_id | BIGINT | FK category |  |
| tax\_id | BIGINT | FK tax, NOT NULL |  |
| unit\_of\_measure\_id | BIGINT | FK unit\_of\_measure, NOT NULL |  |
| sale\_price | NUMERIC(14,2) | NOT NULL, CHECK ≥ 0 |  |
| price\_includes\_tax | BOOLEAN | NOT NULL, DEFAULT true | true por decisión del negocio: el precio ya trae ISV y la base se calcula hacia atrás |
| cost | NUMERIC(14,2) | CHECK ≥ 0 | Para utilidad bruta |
| tracks\_inventory | BOOLEAN | NOT NULL, DEFAULT true | false para servicios |
| tracks\_serials | BOOLEAN | NOT NULL, DEFAULT false | Exige un serial por unidad; requiere unidad sin decimales y tracks\_inventory = true |
| requires\_exclusive\_invoice | BOOLEAN | NOT NULL, DEFAULT false | true = se factura solo; la venta no admite otros productos |
| min\_stock | NUMERIC(12,3) | DEFAULT 0 | Umbral de alerta |
| image\_url | VARCHAR(300) |  |  |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |
| product\_type | VARCHAR(10) | NOT NULL, DEFAULT GOOD | GOOD (producto físico) o SERVICE; un SERVICE nunca mueve inventario ni lleva seriales |

`product.tax_id` es el impuesto del país principal. Para vender el mismo producto en otro país se usa **product\_tax**: `product_id` (FK, PK), `country_id` (FK, PK), `tax_id` (FK tax). Si no hay fila para el país, se usa `product.tax_id`.

### Listas de precios

El precio de una línea se resuelve en este orden: lista del cliente, lista de la sucursal y, si ninguna tiene el producto, `product.sale_price`. Todas las listas guardan precios con ISV incluido.

**price\_list** (lista de precios)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(30) | NOT NULL, UNIQUE | Ej.: RETAIL, WHOLESALE, VIP |
| name | VARCHAR(100) | NOT NULL |  |
| country\_id | BIGINT | FK country, NOT NULL | La moneda sale del país |
| pricing\_method | VARCHAR(15) | NOT NULL | FIXED\_PRICE (precio por producto) o PERCENT\_OFF\_BASE (porcentaje sobre el precio base) |
| percent | NUMERIC(5,2) | Obligatorio si PERCENT\_OFF\_BASE | Ej.: 10.00 = 10 % menos que el precio base |
| valid\_from | DATE |  | Vigencia opcional |
| valid\_to | DATE |  |  |
| priority | SMALLINT | NOT NULL, DEFAULT 0 | Desempate si aplican varias |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

**price\_list\_item** (precio de un producto en una lista)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| price\_list\_id | BIGINT | FK price\_list, NOT NULL |  |
| product\_id | BIGINT | FK product, NOT NULL |  |
| min\_quantity | NUMERIC(12,3) | NOT NULL, DEFAULT 1 | Escalas de mayoreo: a partir de esta cantidad |
| price | NUMERIC(14,2) | NOT NULL, CHECK ≥ 0 | Con ISV incluido |
|  |  | UNIQUE (price\_list\_id, product\_id, min\_quantity) |  |

Las listas se asignan con `branch.default_price_list_id` y `customer.price_list_id`, y cada `sale_line` guarda `price_list_id` para saber de dónde salió su precio.

### Inventario

**inventory\_item** (stock actual; equivale a `MultiInventoryItem` de Salespoint)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| product\_id | BIGINT | FK product, NOT NULL |  |
| branch\_id | BIGINT | FK branch, NOT NULL | UNIQUE (product\_id, branch\_id) |
| quantity | NUMERIC(12,3) | NOT NULL, DEFAULT 0 |  |
| version | INT | NOT NULL | Bloqueo optimista |

**inventory\_movement** (bitácora inmutable; el stock se puede reconstruir sumando)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| product\_id | BIGINT | FK product, NOT NULL |  |
| branch\_id | BIGINT | FK branch, NOT NULL |  |
| type | VARCHAR(20) | NOT NULL | ENTRY, SALE\_OUT, RETURN\_IN, ADJUSTMENT, TRANSFER\_IN, TRANSFER\_OUT |
| quantity | NUMERIC(12,3) | NOT NULL | Positiva o negativa |
| resulting\_balance | NUMERIC(12,3) | NOT NULL | Stock después del movimiento |
| unit\_cost | NUMERIC(14,2) |  |  |
| reason | VARCHAR(200) |  | Obligatorio en ADJUSTMENT |
| reference\_type | VARCHAR(20) |  | SALE, SALE\_RETURN, RECEIPT, RECEIPT\_RETURN, TRANSFER |
| reference\_id | BIGINT |  | Id del documento origen |
| moved\_at | TIMESTAMPTZ | NOT NULL |  |
| user\_id | BIGINT | NOT NULL |  |

**product\_serial** (una fila por unidad física de los productos con `tracks_serials = true`)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| product\_id | BIGINT | FK product, NOT NULL |  |
| serial\_number | VARCHAR(100) | NOT NULL; UNIQUE con product\_id | Serie, IMEI, etc. |
| branch\_id | BIGINT | FK branch, NOT NULL | Sucursal donde está ahora |
| status | VARCHAR(15) | NOT NULL | AVAILABLE, RESERVED, SOLD, RETURNED, DEFECTIVE, IN\_TRANSIT |
| entry\_movement\_id | BIGINT | FK inventory\_movement | Movimiento con el que ingresó |
| received\_at | TIMESTAMPTZ | NOT NULL |  |
| sold\_at | TIMESTAMPTZ |  | Última venta |
| warranty\_expires\_at | DATE |  |  |
| notes | VARCHAR(300) |  |  |

Regla: en productos con seriales, el número de `product_serial` en estado AVAILABLE por sucursal debe coincidir con `inventory_item.quantity`. El historial de ventas de cada serial queda en `sale_line_serial`.

### Clientes

**customer** (cliente)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK | Registro fijo Consumidor final sembrado por migración |
| external\_code | VARCHAR(50) | UNIQUE, NULL | Código del cliente en el ERP |
| customer\_type | VARCHAR(10) | NOT NULL, DEFAULT PERSON | PERSON (persona natural), COMPANY (empresa) |
| document\_type | VARCHAR(10) | NOT NULL | RTN, DNI, PASSPORT, NONE |
| document\_number | VARCHAR(20) | UNIQUE con document\_type |  |
| first\_name | VARCHAR(60) | Obligatorio si PERSON | Primer nombre |
| middle\_name | VARCHAR(60) |  | Segundo nombre |
| last\_name | VARCHAR(60) | Obligatorio si PERSON | Primer apellido |
| second\_last\_name | VARCHAR(60) |  | Segundo apellido |
| business\_name | VARCHAR(200) | Obligatorio si COMPANY | Razón social cuando el cliente es una empresa |
| display\_name | VARCHAR(250) | Columna generada | Nombres y apellidos unidos, o razón social; se usa en búsqueda y comprobantes |
| phone | VARCHAR(20) |  |  |
| email | VARCHAR(150) |  |  |
| address | VARCHAR(300) |  |  |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |
| price\_list\_id | BIGINT | FK price\_list, NULL | Lista especial del cliente (mayorista, VIP) |

### Caja

**cash\_session** (turno de un cajero en una caja)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| cash\_register\_id | BIGINT | FK cash\_register, NOT NULL | Solo una sesión OPEN por caja (índice único parcial) |
| user\_id | BIGINT | FK app\_user, NOT NULL | Cajero |
| status | VARCHAR(10) | NOT NULL | OPEN, CLOSED |
| opened\_at | TIMESTAMPTZ | NOT NULL |  |
| opening\_amount | NUMERIC(14,2) | NOT NULL | Fondo inicial |
| closed\_at | TIMESTAMPTZ |  |  |
| expected\_total | NUMERIC(14,2) |  | Calculado al cerrar |
| counted\_total | NUMERIC(14,2) |  | Lo que cuenta el cajero |
| difference | NUMERIC(14,2) |  | Contado menos esperado |
| count\_detail | JSONB |  | Esperado vs. contado por método de pago |
| notes | VARCHAR(500) |  |  |

**cash\_movement** (entradas y retiros de efectivo que no son ventas)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| cash\_session\_id | BIGINT | FK cash\_session, NOT NULL |  |
| type | VARCHAR(10) | NOT NULL | DEPOSIT, WITHDRAWAL |
| amount | NUMERIC(14,2) | NOT NULL, CHECK > 0 |  |
| reason | VARCHAR(200) | NOT NULL |  |
| moved\_at | TIMESTAMPTZ | NOT NULL |  |
| user\_id | BIGINT | NOT NULL |  |

### Facturación fiscal

La facturación es un módulo enchufable por país. El núcleo de ventas solo conoce tres campos genéricos en `sale` (`fiscal_number`, `fiscal_status`, `fiscal_data`) y llama a la interfaz `FiscalProvider` del país de la sucursal. Cada país aporta sus propias tablas con su prefijo (`hn_` para Honduras); un país nuevo, como Guatemala con factura electrónica FEL, se agrega sin tocar ventas. La tabla siguiente es la única que necesita Honduras.

hn\_**fiscal\_range** (autorización de numeración por caja y tipo de documento)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| cash\_register\_id | BIGINT | FK cash\_register, NOT NULL |  |
| document\_type | VARCHAR(20) | NOT NULL | INVOICE, CREDIT\_NOTE (los recibos no usan rango fiscal) |
| cai | VARCHAR(50) | NOT NULL | Código de autorización |
| prefix | VARCHAR(20) | NOT NULL | Ej.: 001-001-01- (sucursal, caja, tipo) |
| start\_number | BIGINT | NOT NULL |  |
| end\_number | BIGINT | NOT NULL, CHECK ≥ start\_number |  |
| current\_number | BIGINT | NOT NULL | Último emitido; se bloquea con `SELECT … FOR UPDATE` |
| issue\_deadline | DATE | NOT NULL | Fecha límite de emisión |
| status | VARCHAR(10) | NOT NULL | ACTIVE, EXHAUSTED, EXPIRED |

### Ventas

**sale** (venta; equivale a `Order` de Salespoint)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| type | VARCHAR(10) | NOT NULL | SALE (venta) o RETURN (devolución) |
| original\_sale\_id | BIGINT | FK sale; obligatorio si RETURN | Venta que se devuelve |
| internal\_number | BIGINT | NOT NULL, UNIQUE | Secuencia; existe aunque el documento no se pague |
| fiscal\_number | VARCHAR(30) | UNIQUE, NULL | Número de factura (SALE) o de nota de crédito (RETURN); se asigna al confirmar |
| fiscal\_authorization\_id | BIGINT | Id lógico, sin FK | Autorización usada, en la tabla del módulo fiscal del país (en Honduras, un hn\_fiscal\_range INVOICE o CREDIT\_NOTE) |
| document\_type | VARCHAR(20) | NOT NULL | INVOICE o CREDIT\_NOTE; se deriva de type |
| branch\_id | BIGINT | FK branch, NOT NULL |  |
| cash\_register\_id | BIGINT | FK cash\_register, NOT NULL |  |
| cash\_session\_id | BIGINT | FK cash\_session, NOT NULL | Caja donde se cobra o se reembolsa |
| cashier\_id | BIGINT | FK app\_user, NOT NULL |  |
| customer\_id | BIGINT | FK customer, NOT NULL | Consumidor final por defecto; en RETURN, el de la venta original |
| status | VARCHAR(20) | NOT NULL | OPEN, ON\_HOLD, PAID, CANCELED, PARTIALLY\_RETURNED, RETURNED (los dos últimos solo en SALE) |
| is\_exclusive | BOOLEAN | NOT NULL, DEFAULT false | true si contiene un producto con factura exclusiva |
| subtotal | NUMERIC(14,2) | NOT NULL | Sin impuesto y antes de descuento |
| discount\_total | NUMERIC(14,2) | NOT NULL, DEFAULT 0 |  |
| exempt\_amount | NUMERIC(14,2) | NOT NULL, DEFAULT 0 | Desglose fiscal |
| taxable\_amount\_15 | NUMERIC(14,2) | NOT NULL, DEFAULT 0 |  |
| taxable\_amount\_18 | NUMERIC(14,2) | NOT NULL, DEFAULT 0 |  |
| tax\_total | NUMERIC(14,2) | NOT NULL |  |
| total | NUMERIC(14,2) | NOT NULL | Siempre positivo; el signo lo da type |
| amount\_paid | NUMERIC(14,2) | NOT NULL, DEFAULT 0 | Cobrado (SALE) o reembolsado (RETURN) |
| change\_due | NUMERIC(14,2) | NOT NULL, DEFAULT 0 | Vuelto; solo SALE |
| paid\_at | TIMESTAMPTZ |  | Fecha de cobro o de reembolso |
| reason | VARCHAR(300) | Obligatorio si RETURN | Motivo de la devolución |
| refund\_method | VARCHAR(20) | Obligatorio si RETURN | CASH, ORIGINAL\_METHOD, STORE\_CREDIT |
| authorized\_by | BIGINT | FK app\_user | Gerente que autorizó la devolución o la anulación |
| cancel\_reason | VARCHAR(300) |  |  |
| canceled\_by | BIGINT | FK app\_user |  |
| canceled\_at | TIMESTAMPTZ |  |  |
| notes | VARCHAR(500) |  |  |
| fiscal\_status | VARCHAR(15) | NOT NULL, DEFAULT NOT\_REQUIRED | NOT\_REQUIRED, PENDING, ISSUED, REJECTED; para países con factura electrónica que validan con la autoridad |
| fiscal\_data | JSONB | — | Datos propios del país para imprimir: CAI, rango y fecha límite (HN); UUID de certificación y QR (factura electrónica) |
| synced\_at | TIMESTAMPTZ | — | Cuándo llegó la venta a la central desde el servidor de la tienda; NULL mientras está pendiente |

Una devolución es una fila más de `sale` con `type = RETURN`: tiene sus propias líneas, pagos (reembolsos) y seriales, y apunta a la venta original. Así los reportes netos salen de una sola tabla: ventas menos devoluciones.

**sale\_line** (línea de venta; equivale a `OrderLine`; guarda una foto del producto al vender)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| sale\_id | BIGINT | FK sale, NOT NULL |  |
| line\_number | SMALLINT | NOT NULL | Orden en el ticket |
| product\_id | BIGINT | FK product, NOT NULL |  |
| sku | VARCHAR(40) | NOT NULL | Copia histórica |
| description | VARCHAR(200) | NOT NULL | Copia histórica |
| quantity | NUMERIC(12,3) | NOT NULL, CHECK > 0 |  |
| unit\_price | NUMERIC(14,2) | NOT NULL | Precio vigente al vender |
| unit\_cost | NUMERIC(14,2) |  | Para utilidad |
| discount\_type | VARCHAR(10) |  | AMOUNT, PERCENTAGE |
| discount\_value | NUMERIC(14,2) | DEFAULT 0 |  |
| discount\_amount | NUMERIC(14,2) | NOT NULL, DEFAULT 0 | Calculado |
| tax\_code | VARCHAR(20) | NOT NULL | Copia histórica |
| tax\_rate | NUMERIC(5,2) | NOT NULL | Copia histórica |
| tax\_amount | NUMERIC(14,2) | NOT NULL |  |
| subtotal | NUMERIC(14,2) | NOT NULL | Cantidad por precio, menos descuento |
| total | NUMERIC(14,2) | NOT NULL | Subtotal más impuesto |
| returned\_quantity | NUMERIC(12,3) | NOT NULL, DEFAULT 0 | Solo en SALE; suma de lo devuelto, impide devolver más de lo vendido |
| original\_line\_id | BIGINT | FK sale\_line; obligatorio en RETURN | Línea de la venta original que se devuelve |
| restock | BOOLEAN | Solo en RETURN, DEFAULT true | false si el producto vuelve dañado y no reingresa al inventario |
| price\_list\_id | BIGINT | FK price\_list, NULL | Lista de la que salió el precio; NULL = precio base del producto |

**sale\_line\_serial** (qué seriales salieron en cada línea): `id`, `sale_line_id` (FK sale\_line), `product_serial_id` (FK product\_serial), `returned BOOLEAN DEFAULT false`. UNIQUE (sale\_line\_id, product\_serial\_id). Al pagar, cada serial pasa a SOLD; al devolver, a RETURNED o DEFECTIVE.

**payment\_method** (método de pago)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| code | VARCHAR(20) | NOT NULL, UNIQUE | CASH, CARD, TRANSFER, STORE\_CREDIT, CUSTOMER\_CREDIT (venta al crédito; genera una cuenta por cobrar) |
| name | VARCHAR(60) | NOT NULL |  |
| requires\_reference | BOOLEAN | NOT NULL, DEFAULT false | Ej.: autorización del POS bancario |
| allows\_change | BOOLEAN | NOT NULL, DEFAULT false | Solo efectivo da vuelto |
| active | BOOLEAN | NOT NULL, DEFAULT true |  |

**payment** (pago; uno o varios por venta)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| sale\_id | BIGINT | FK sale, NULL | Una de las dos columnas es obligatoria (CHECK) |
| receipt\_id | BIGINT | FK receipt, NULL |  |
| payment\_method\_id | BIGINT | FK payment\_method, NOT NULL |  |
| amount | NUMERIC(14,2) | NOT NULL, CHECK > 0 | Cobro, o reembolso si el documento es RETURN |
| amount\_received | NUMERIC(14,2) |  | Efectivo entregado por el cliente |
| reference | VARCHAR(60) |  | Autorización de tarjeta o transferencia |
| card\_last4 | CHAR(4) |  | Nunca el número completo |
| idempotency\_key | VARCHAR(64) | UNIQUE | RNF-06 |
| paid\_at | TIMESTAMPTZ | NOT NULL |  |

### Recibos

Los recibos son documentos no fiscales (sin CAI) y viven en tablas propias, separadas de las facturas. Siguen el mismo patrón: un recibo y su devolución están en la misma tabla, distinguidos por `type`.

**receipt** (recibo): mismas columnas que `sale`, con estas diferencias.

| Columna | Cambio frente a sale | Descripción |
| --- | --- | --- |
| type | Valores RECEIPT o RETURN |  |
| original\_receipt\_id | Reemplaza a original\_sale\_id; FK receipt | Recibo que se devuelve |
| number | Reemplaza a fiscal\_number; VARCHAR(30), UNIQUE | Numeración propia por caja (prefijo + correlativo), sin CAI |
| fiscal\_authorization\_id, fiscal\_status, fiscal\_data | No existe | Los recibos no pasan por el módulo fiscal |
| document\_type | No existe | type basta |
| exempt\_amount, taxable\_amount\_15, taxable\_amount\_18 | Se conservan | Por si el recibo desglosa impuestos; confirmar |

El resto (sucursal, caja, sesión, cajero, cliente, estado, totales, motivo, reembolso, autorización, anulación) es idéntico a `sale`.

**receipt\_line**: mismas columnas que `sale_line`, cambiando `sale_id` por `receipt_id` (FK receipt) y `original_line_id` por FK a receipt\_line.

**receipt\_line\_serial**: igual que `sale_line_serial`, con `receipt_line_id` (FK receipt\_line).

Los pagos de recibos van en la misma tabla `payment` (columna `receipt_id`). El correlativo de recibos se guarda en dos columnas nuevas de `cash_register`: `receipt_prefix VARCHAR(20)` y `last_receipt_number BIGINT`, que se bloquean con `SELECT … FOR UPDATE` al emitir.

### Crédito y cuentas por cobrar

Una venta o recibo pagado total o parcialmente con `CUSTOMER_CREDIT` crea una cuenta por cobrar; los abonos se registran aparte y se aplican a una o varias cuentas. El alcance detallado (intereses, mora, bloqueos) queda en Preguntas abiertas.

**customer\_credit** (condiciones de crédito del cliente)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| customer\_id | BIGINT | FK customer, NOT NULL, UNIQUE |  |
| credit\_limit | NUMERIC(14,2) | NOT NULL, CHECK ≥ 0 |  |
| payment\_term\_days | SMALLINT | NOT NULL | Plazo, ej.: 30 días |
| current\_balance | NUMERIC(14,2) | NOT NULL, DEFAULT 0 | Suma de saldos abiertos; se actualiza por evento |
| status | VARCHAR(10) | NOT NULL | ACTIVE, BLOCKED, SUSPENDED |
| approved\_by | BIGINT | FK app\_user |  |
| notes | VARCHAR(300) |  |  |

**receivable** (cuenta por cobrar; una por documento a crédito)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| customer\_id | BIGINT | FK customer, NOT NULL |  |
| branch\_id | BIGINT | FK branch, NOT NULL |  |
| sale\_id | BIGINT | FK sale, NULL | Una de las dos es obligatoria |
| receipt\_id | BIGINT | FK receipt, NULL |  |
| issued\_at | DATE | NOT NULL |  |
| due\_date | DATE | NOT NULL | issued\_at más el plazo |
| original\_amount | NUMERIC(14,2) | NOT NULL |  |
| balance | NUMERIC(14,2) | NOT NULL | Baja con abonos y devoluciones |
| status | VARCHAR(15) | NOT NULL | OPEN, PARTIALLY\_PAID, PAID, OVERDUE, WRITTEN\_OFF |

**receivable\_payment** (abono del cliente)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| number | VARCHAR(30) | NOT NULL, UNIQUE | Número del comprobante de abono |
| customer\_id | BIGINT | FK customer, NOT NULL |  |
| branch\_id | BIGINT | FK branch, NOT NULL |  |
| cash\_session\_id | BIGINT | FK cash\_session, NOT NULL | Entra al arqueo de la caja |
| payment\_method\_id | BIGINT | FK payment\_method, NOT NULL |  |
| amount | NUMERIC(14,2) | NOT NULL, CHECK > 0 |  |
| reference | VARCHAR(60) |  |  |
| paid\_at | TIMESTAMPTZ | NOT NULL |  |
| status | VARCHAR(10) | NOT NULL | APPLIED, CANCELED |

**receivable\_payment\_allocation**: `receivable_payment_id` (FK, PK), `receivable_id` (FK, PK), `amount NUMERIC(14,2)`. Reparte un abono entre varias cuentas; por defecto se aplica a la más antigua.

### Sincronización

**store\_node** (servidor de tienda registrado en la central)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| node\_number | SMALLINT | NOT NULL, UNIQUE | 0 = central; 1, 2, 3… = tiendas. Lo asigna la central al registrar la tienda y define su rango de ids |
| branch\_id | BIGINT | FK branch, UNIQUE, NULL | Sucursal que atiende; NULL en la central |
| id\_range\_start | BIGINT | NOT NULL | node\_number × 1 000 000 000 + 1 |
| id\_range\_end | BIGINT | NOT NULL | Fin del rango; alerta al acercarse |
| last\_upload\_at | TIMESTAMPTZ |  | Último envío confirmado a la central |
| last\_download\_version | BIGINT |  | Última versión de `sync_change_log` aplicada |
| app\_version | VARCHAR(20) |  | Versión del backend instalada en la tienda |
| status | VARCHAR(15) | NOT NULL | ACTIVE, OUT\_OF\_SYNC, DISABLED |
| last\_heartbeat\_at | TIMESTAMPTZ | — | Último latido recibido; base de la alerta de tienda sin conexión |
| pending\_upload\_count | INT | NOT NULL, DEFAULT 0 | Registros que la tienda aún no ha subido, según su último latido |
| oldest\_pending\_at | TIMESTAMPTZ | — | Fecha del registro pendiente más antiguo |
| activation\_code\_hash | VARCHAR(255) | — | Código de activación de un solo uso; se borra al canjearlo |
| activation\_expires\_at | TIMESTAMPTZ | — | Vencimiento del código (24 horas) |
| credential\_revoked\_at | TIMESTAMPTZ | — | Si tiene valor, la central rechaza a esa tienda |

**sync\_change\_log** (solo en la central; lo que las tiendas deben descargar)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| version | BIGINT | PK, secuencia | Número creciente de cambio |
| entity | VARCHAR(40) | NOT NULL | product, price\_list\_item, customer… |
| entity\_id | BIGINT | NOT NULL |  |
| operation | VARCHAR(10) | NOT NULL | UPSERT, DELETE |
| branch\_id | BIGINT | NULL | NULL = para todas las tiendas; con valor = solo para esa sucursal |
| changed\_at | TIMESTAMPTZ | NOT NULL |  |

**store\_daily\_reconciliation** (cuadre diario entre tienda y central)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| store\_node\_id | BIGINT | FK store\_node, NOT NULL |  |
| cash\_register\_id | BIGINT | FK cash\_register, NOT NULL | UNIQUE con business\_date |
| business\_date | DATE | NOT NULL | Día de operación |
| store\_sales\_count | INT | NOT NULL | Lo que reporta la tienda |
| store\_sales\_total | NUMERIC(14,2) | NOT NULL |  |
| store\_receipts\_count | INT | NOT NULL |  |
| store\_receipts\_total | NUMERIC(14,2) | NOT NULL |  |
| central\_sales\_count | INT | NOT NULL | Lo que tiene la central |
| central\_sales\_total | NUMERIC(14,2) | NOT NULL |  |
| central\_receipts\_count | INT | NOT NULL |  |
| central\_receipts\_total | NUMERIC(14,2) | NOT NULL |  |
| status | VARCHAR(15) | NOT NULL | MATCHED, MISMATCH, RESEND\_REQUESTED, RESOLVED |
| checked\_at | TIMESTAMPTZ | NOT NULL |  |

### Auditoría e infraestructura

**audit\_log** (bitácora de auditoría)

| Columna | Tipo | Restricciones | Descripción |
| --- | --- | --- | --- |
| id | BIGINT | PK |  |
| occurred\_at | TIMESTAMPTZ | NOT NULL |  |
| user\_id | BIGINT |  |  |
| action | VARCHAR(40) | NOT NULL | SALE\_CANCELED, PRICE\_CHANGED, INVENTORY\_ADJUSTED, LOGIN\_FAILED… |
| entity | VARCHAR(40) | NOT NULL |  |
| entity\_id | BIGINT |  |  |
| old\_values | JSONB |  |  |
| new\_values | JSONB |  |  |
| ip\_address | VARCHAR(45) |  |  |

**event\_publication**: la crea y administra Spring Modulith. Guarda cada evento entre módulos hasta que su receptor lo procesa, de modo que un evento SalePaid no se pierde si el descuento de inventario falla y se reintenta.

**Relaciones principales**: una sucursal tiene muchas cajas; una caja tiene muchas sesiones y rangos fiscales; una sesión tiene muchas ventas y movimientos de caja; una venta tiene muchas líneas y pagos, y puede tener devoluciones (filas RETURN de la misma tabla); los recibos repiten ese esquema en sus propias tablas; cada línea apunta a un producto y, si aplica, a sus seriales; el stock es único por producto y sucursal; un rol tiene muchos privilegios.

## Diseño de API REST

Todas las rutas cuelgan de `/api/v1`, usan JSON y devuelven errores en formato RFC 9457 (`application/problem+json`). Las listas se paginan con `page`, `size` y `sort`.

| Método | Ruta | Uso | Privilegio típico |
| --- | --- | --- | --- |
| POST | `/auth/login` | Obtener token de acceso y de refresco | Público |
| POST | `/auth/refresh` | Renovar token | Autenticado |
| GET | `/me/menus` | Menú del frontend según los privilegios MENU del usuario | Autenticado |
| GET | `/me/privileges` | Códigos ACTION y ENDPOINT del usuario, para mostrar u ocultar botones | Autenticado |
| GET / POST / PUT | `/users`, `/users/{id}` | Gestionar usuarios (incluye `externalCode`) | ADMIN |
| GET / POST / PUT | `/roles`, `/roles/{id}` | Gestionar roles | ADMIN |
| GET / PUT | `/roles/{id}/privileges` | Ver y asignar privilegios a un rol | ADMIN |
| GET / POST / PUT | `/privileges?type=MENU\|ACTION\|ENDPOINT` | Mantener menús y acciones; los ENDPOINT solo se leen | ADMIN |
| GET / POST / PUT | `/branches`, `/branches/{id}/cash-registers` | Sucursales y cajas | ADMIN |
| GET | `/products?q=&categoryId=` | Buscar productos | Todos |
| GET | `/products/barcode/{code}` | Escaneo en caja | CASHIER+ |
| POST / PUT / DELETE | `/products`, `/products/{id}` | Mantener catálogo (DELETE = desactivar) | MANAGER, ADMIN |
| GET / POST / PUT | `/categories`, `/taxes`, `/units-of-measure` | Catálogos auxiliares | MANAGER, ADMIN |
| GET | `/inventory?branchId=&belowMin=true` | Existencias y alertas | WAREHOUSE\_CLERK+ |
| POST | `/inventory/movements` | Entrada, salida o ajuste (con seriales si aplica) | WAREHOUSE\_CLERK, MANAGER |
| POST | `/inventory/transfers` | Transferir entre sucursales | MANAGER |
| GET | `/products/{id}/serials?status=AVAILABLE&branchId=` | Seriales disponibles para vender | CASHIER+ |
| GET | `/serials/{serialNumber}` | Rastrear un serial: estado, venta y garantía | CASHIER+ |
| POST | `/cash-registers/{id}/sessions` | Abrir sesión de caja | CASHIER+ |
| POST | `/cash-sessions/{id}/movements` | Ingreso o retiro de efectivo | CASHIER+ |
| POST | `/cash-sessions/{id}/closing` | Arqueo y cierre | CASHIER+ |
| POST | `/sales` | Crear venta abierta | CASHIER+ |
| POST / PUT / DELETE | `/sales/{id}/lines`, `/sales/{id}/lines/{lineId}` | Agregar (con `serialIds` si aplica), cambiar cantidad, quitar | CASHIER+ |
| PUT | `/sales/{id}/discount` | Descuento a la venta | CASHIER+ (sobre límite: MANAGER) |
| PUT | `/sales/{id}/customer` | Asignar cliente | CASHIER+ |
| POST | `/sales/{id}/hold`, `/sales/{id}/resume` | Venta en espera | CASHIER+ |
| POST | `/sales/{id}/payments` | Cobrar (requiere `Idempotency-Key`) | CASHIER+ |
| POST | `/sales/{id}/cancellation` | Anular con motivo y PIN de gerente | MANAGER |
| POST | `/sales/{id}/returns` | Devolución parcial o total; crea una fila sale con type RETURN | MANAGER |
| GET | `/sales/{id}/receipt?format=pdf\|json` | Comprobante para imprimir | CASHIER+ |
| GET / POST / PUT | `/customers`, `/customers/{id}` | Buscar (por nombre, documento o `externalCode`) y registrar | CASHIER+ |
| GET / POST | `/fiscal-ranges` | Configurar CAI y rangos | ADMIN |
| GET | `/reports/sales?from=&to=&groupBy=` | Ventas por día, cajero, producto o método | MANAGER+ |
| GET | `/reports/top-products`, `/reports/gross-profit` | Más vendidos y utilidad bruta | MANAGER+ |
| POST | /receipts, /receipts/{id}/lines, /receipts/{id}/payments | Crear recibo, agregar líneas y cobrar (mismo flujo que ventas) | CASHIER+ |
| POST | /receipts/{id}/returns, /receipts/{id}/cancellation | Devolver o anular un recibo | MANAGER |
| GET | /receipts?type=RECEIPT\|RETURN, /receipts/{id}/print | Consultar e imprimir recibos y devoluciones | CASHIER+ |
| GET / POST / PUT | /countries, /price-lists, /price-lists/{id}/items | Países y listas de precios | ADMIN, MANAGER |
| GET | /products/{id}/price?customerId=&branchId=&quantity= | Precio resuelto para un cliente, sucursal y cantidad | CASHIER+ |
| GET / PUT | /customers/{id}/credit, /customers/{id}/statement | Condiciones de crédito y estado de cuenta | MANAGER (consulta: CASHIER+) |
| GET / POST | /receivables?customerId=&status=, /receivable-payments | Cuentas por cobrar y registro de abonos | CASHIER+ |
| POST / GET | /sync/activate, /sync/snapshot, /sync/changes?sinceVersion=&limit=, /sync/ack, /sync/upload, /sync/heartbeat, /sync/reconciliation | Solo entre servidores: activar la tienda, carga inicial, bajar cambios y confirmar la versión aplicada, subir documentos, latido y cuadre diario. Además, el WebSocket /sync/notifications avisa de cambios nuevos | Servidor de tienda registrado (credencial propia) |

La última columna es orientativa: el acceso real lo decide la tabla `role\_privilege`, no un rol fijo en el código. "CASHIER+" significa cajero o cualquier rol superior. Los cuerpos JSON usan `camelCase` en inglés (por ejemplo `externalCode`, `firstName`). Además del rol, cada consulta se filtra por nodo: la central ve todas las sucursales y cada tienda solo la sucursal que le asignó la central (`store_identity.branch_id`). Esto es obligatorio con ids incrementales: como son predecibles (`/sales/1041`, `/sales/1042`), la API debe verificar en cada petición que el registro pertenece a una sucursal visible desde el nodo.

## Plan de fases

Se construye en 8 iteraciones; cada una termina con algo que se puede probar de punta a punta por la API. Las duraciones se fijan cuando sepamos el tamaño del equipo.

1. **Base del proyecto**: estructura de módulos, Docker Compose con PostgreSQL, Flyway, seguridad JWT, usuarios, roles y privilegios (endpoints, menús y acciones; RF-37, RF-38), prueba de arquitectura de Spring Modulith y pipeline de CI.
2. **Organización y catálogo**: empresa, sucursales, cajas, categorías, impuestos, unidades y productos con búsqueda por código de barras, países y listas de precios (RF-05 a RF-11, RF-44, RF-45).
3. **Inventario**: stock por sucursal, movimientos, ajustes, alertas y seriales (RF-12 a RF-15, RF-42).
4. **Venta y caja (MVP)**: sesiones de caja, venta con líneas, descuentos, pagos mixtos, número fiscal y descuento de stock por evento, regla de factura exclusiva (RF-17 a RF-19, RF-26 a RF-31, RF-41). Al cerrar esta fase ya se puede vender.
5. **Postventa**: anulaciones, devoluciones con nota de crédito, ventas en espera, comprobante PDF y auditoría (RF-20 a RF-22, RF-32, RF-36).
6. **Reportes y endurecimiento**: reportes, exportación CSV, transferencias, pruebas de carga contra RNF-01 a RNF-03 y respaldos.
7. **Crédito**: condiciones de crédito, cuentas por cobrar, abonos y estado de cuenta (RF-46, RF-47).
8. **Servidor por tienda**: perfil de tienda, subida y bajada de cambios, transferencias entre tiendas, alta con código de activación, panel de sincronización con cuadre diario, instalación y monitoreo remotos (RF-48 a RF-50). Desde la fase 1 la central ya usa su rango de ids, así que no habrá que migrar datos.

## MVP: base y catálogos

El MVP construye la base técnica del sistema y un único módulo de negocio, el catálogo, para validar la arquitectura antes de abordar ventas. Incluye los dos perfiles: una central y dos tiendas que reciben el catálogo por sincronización, todo corriendo en local dentro de una máquina virtual con Vagrant. Así se valida desde el inicio la arquitectura central y tienda.

**Dentro del MVP**

- **Base técnica:** proyecto Spring Boot 4.1.1 con Java 25 y Spring Modulith, PostgreSQL con Flyway, Docker Compose y documentación OpenAPI con Swagger UI.
- **Seguridad:** inicio de sesión con JWT y token de refresco; usuarios, roles y privilegios por endpoint, menú y acción.
- **Organización mínima:** países, empresa y sucursales (cada tienda del MVP es una sucursal, todavía sin cajas).
- **Auditoría:** columnas de auditoría en todas las tablas y bitácora `audit_log` para cambios sensibles.
- **Catálogo:** categorías, unidades de medida, productos y servicios, impuestos por país, listas de precios con escalas y vigencia, y resolución de precio.
- **Perfil tienda y réplica:** alta de tiendas con código de activación, carga inicial, bajada periódica de cambios, confirmación de versión, latido y panel de estado en la central.
- **Entorno local con Vagrant:** una máquina virtual que levanta con Docker una central y dos tiendas, cada una con su propia base de datos.

**Fuera del MVP**

Cajas, inventario y seriales, clientes, ventas, recibos, caja, facturación fiscal, crédito y reportes. De la sincronización quedan fuera la subida de documentos transaccionales, el cuadre diario, el aviso inmediato por WebSocket y las transferencias entre tiendas, porque dependen de esos módulos. Los campos de producto que dependen de esos módulos (`tracks_inventory`, `tracks_serials`, `requires_exclusive_invoice`) se guardan y validan desde ahora, pero su efecto llega con cada módulo.

### Tablas del MVP

Son 19 de las 40 tablas, con los mismos campos del Modelo de datos. Existen en la central y en cada tienda; en la tienda, todas salvo store\_node y audit\_log son copias de solo lectura que llegan por sincronización.

| Módulo | Tablas |
| --- | --- |
| Organización | `country`, `company`, `branch` |
| Seguridad | `app_user`, `role`, `user_role`, `user_branch`, `privilege`, `role_privilege` |
| Catálogo | `category`, `unit_of_measure`, `tax`, `product`, `product_tax` |
| Precios | `price_list`, `price_list_item` |
| Sincronización | `store_node`, `sync_change_log` (este último solo en la central) |
| Auditoría | `audit_log` |

Cambios respecto al modelo completo, solo para el MVP:

- Las sucursales entran con sus datos básicos; `cash_register` llega con el módulo de caja.
- `customer.price_list_id` espera al módulo de clientes. La resolución de precio usa la lista indicada o, si no se indica, `branch.default_price_list_id` de la sucursal.
- Spring Modulith crea su tabla `event_publication`; en el MVP la usan la auditoría y el registro de cambios para sincronizar.

### Estructura del proyecto

Un solo proyecto Maven con un paquete raíz por módulo de Spring Modulith:

| Paquete | Contenido |
| --- | --- |
| `com.medinasystems.pos.shared` | Tipos comunes: dinero, errores RFC 9457, paginación, columnas de auditoría |
| `com.medinasystems.pos.security` | Login, JWT, usuarios, roles, privilegios y registro automático de endpoints |
| `com.medinasystems.pos.organization` | Países, empresa y sucursales |
| `com.medinasystems.pos.catalog` | Categorías, unidades, impuestos, productos |
| `com.medinasystems.pos.pricing` | Listas de precios y resolución de precio |
| `com.medinasystems.pos.sync` | En la central: alta de tiendas, registro de cambios y panel. En la tienda: activación, carga inicial, bajada y latido |
| `com.medinasystems.pos.audit` | Bitácora de auditoría; escucha eventos de los demás módulos |

Cada módulo sigue las capas `web`, `application`, `domain` e `infrastructure` descritas en Arquitectura propuesta. Una prueba de Spring Modulith verifica en cada build que ningún módulo use clases internas de otro.

### Central y tiendas en el MVP

Es el mismo artefacto (una imagen Docker) arrancado con el perfil `central` o con el perfil `store`.

| Aspecto | Central | Tienda |
| --- | --- | --- |
| Catálogo, precios, usuarios, roles | Crea, modifica y registra cada cambio en `sync_change_log` | Solo lectura: rechaza escrituras con 403 |
| Consultas de producto y precio | Sí | Sí, contra su base local, aunque no haya internet |
| Inicio de sesión | Sí | Sí, con los usuarios replicados que tienen acceso a su sucursal (`user_branch`) |
| Rango de ids | 1 a 999 999 999 | El que le asigna la central al registrarla |
| Sincronización | Registra tiendas, genera códigos de activación, sirve cambios y muestra el panel | Se activa, hace la carga inicial y cada 30 segundos envía latido y baja cambios |
| Auditoría | Cambios de catálogo, precios y seguridad | Inicios de sesión y eventos de sincronización |

En el MVP solo baja información de la central a las tiendas; las tiendas no generan todavía documentos que subir. El aviso inmediato por WebSocket se agrega con el módulo de ventas: aquí basta la consulta periódica.

### Endpoints del MVP

Todas las rutas cuelgan de `/api/v1`. Los listados se paginan y aceptan filtros por texto y por estado activo.

| Método | Ruta | Uso | Privilegio típico |
| --- | --- | --- | --- |
| POST | `/auth/login`, `/auth/refresh`, `/auth/logout` | Sesión con JWT | Público / autenticado |
| GET | `/me`, `/me/menus`, `/me/privileges` | Datos, menú y acciones del usuario | Autenticado |
| GET / POST / PUT | `/users`, `/users/{id}`, `/users/{id}/password-reset` | Usuarios | ADMIN |
| GET / POST / PUT | `/roles`, `/roles/{id}`, `/roles/{id}/privileges` | Roles y sus privilegios | ADMIN |
| GET / POST / PUT | `/privileges?type=` | Menús y acciones; los ENDPOINT solo se leen | ADMIN |
| GET / POST / PUT | `/countries`, `/company` | País y empresa | ADMIN |
| GET / POST / PUT / DELETE | `/categories`, `/categories/{id}` | Categorías (árbol); DELETE desactiva | MANAGER, ADMIN |
| GET / POST / PUT | `/units-of-measure` | Unidades de medida | MANAGER, ADMIN |
| GET / POST / PUT | `/taxes?countryId=` | Impuestos por país | ADMIN |
| GET | `/products?q=&categoryId=&type=&active=` | Buscar productos | Autenticado |
| GET | `/products/{id}`, `/products/barcode/{code}`, `/products/sku/{sku}` | Consultar un producto | Autenticado |
| POST / PUT / DELETE | `/products`, `/products/{id}` | Mantener productos; DELETE desactiva | MANAGER, ADMIN |
| GET / PUT | `/products/{id}/taxes` | Impuesto del producto por país | MANAGER, ADMIN |
| GET / POST / PUT | `/price-lists`, `/price-lists/{id}` | Listas de precios | MANAGER, ADMIN |
| GET / POST / PUT / DELETE | `/price-lists/{id}/items` | Precios por producto y escala | MANAGER, ADMIN |
| GET | `/products/{id}/price?priceListId=&quantity=&date=` | Precio resuelto con base gravable e impuesto | Autenticado |
| GET | `/audit-log?entity=&entityId=&from=&to=` | Consultar la bitácora | ADMIN |

Endpoints de tiendas y sincronización:

| Método | Ruta | Uso | Quién lo usa |
| --- | --- | --- | --- |
| GET / POST / PUT | `/branches`, `/branches/{id}` | Sucursales y su lista de precios por defecto | ADMIN (central) |
| GET / POST | `/store-nodes`, `/store-nodes/{id}` | Registrar tiendas y ver su estado de sincronización | ADMIN (central) |
| POST | `/store-nodes/{id}/activation-code` | Generar un código de activación de 24 horas | ADMIN (central) |
| POST | `/store-nodes/{id}/revoke` | Revocar las credenciales de una tienda | ADMIN (central) |
| POST | `/sync/activate` | La tienda canjea su código por credenciales | Servidor de tienda |
| GET | `/sync/snapshot` | Carga inicial completa | Servidor de tienda |
| GET | `/sync/changes?sinceVersion=&limit=` | Cambios desde una versión, paginados y comprimidos | Servidor de tienda |
| POST | `/sync/ack` | Confirmar la versión aplicada | Servidor de tienda |
| POST | `/sync/heartbeat` | Latido con versión aplicada y versión de software | Servidor de tienda |
| GET | `/sync/status` | En la tienda: estado local de la sincronización | ADMIN (tienda) |

### Reglas de negocio del catálogo

**Productos**

- `sku` y `barcode` son únicos; el código de barras es opcional.
- Todo producto necesita impuesto y unidad de medida activos.
- Un `SERVICE` siempre tiene `tracks_inventory = false` y `tracks_serials = false`.
- `tracks_serials = true` exige `tracks_inventory = true` y una unidad sin decimales.
- `sale_price` y `cost` no pueden ser negativos; el precio siempre incluye ISV.
- Los productos no se borran: DELETE los desactiva. Un producto inactivo no aparece en búsquedas de venta, pero sí en consultas históricas.
- Cambiar precio o costo deja registro en `audit_log` con el valor anterior y el nuevo.

**Categorías**

- Forman un árbol sin ciclos: una categoría no puede ser su propio ancestro.
- No se puede desactivar una categoría con productos activos o subcategorías activas.

**Impuestos**

- El código es único por país y la tasa va de 0 a 100.
- Un impuesto en uso por productos no se puede desactivar.
- `product_tax` solo admite un impuesto del mismo país que indica la fila.

**Listas de precios**

- `PERCENT_OFF_BASE` exige un porcentaje entre 0 y 100; `FIXED_PRICE` usa los precios de `price_list_item`.
- `valid_to` no puede ser anterior a `valid_from`.
- `min_quantity` es al menos 1, y no se repite la misma escala para un producto en una lista.

**Resolución de precio**

Para un producto, una lista, una cantidad y una fecha:

1. Si la lista está activa y vigente en esa fecha, se busca en ella la escala con el mayor `min_quantity` que no supere la cantidad.
2. Si la lista es de porcentaje, se aplica el descuento sobre `sale_price`.
3. Si no hay lista, la lista no está vigente o no tiene ese producto, se usa `sale_price`.
4. Como el precio incluye ISV, la base gravable se calcula hacia atrás: base = precio / (1 + tasa / 100), redondeando a 2 decimales con HALF\_UP, e impuesto = precio − base.

La respuesta indica el precio, la base, el impuesto, la tasa aplicada y de qué lista salió el precio.

### Datos iniciales

Flyway los carga al crear la base, para que el sistema quede usable de inmediato:

- País Honduras (HN, HNL, America/Tegucigalpa, RTN, proveedor fiscal HN\_SAR) y una empresa de ejemplo.
- Impuestos de Honduras: EXEMPT (0 %), ISV15 (15 %) e ISV18 (18 %).
- Unidades de medida: UNIT, KG, LB y LT.
- Roles ADMIN, MANAGER, CASHIER y WAREHOUSE\_CLERK.
- Un usuario administrador inicial. Su contraseña nunca va en el código ni en las migraciones: se toma de la variable de entorno POS_ADMIN_INITIAL_PASSWORD o, si no existe, se genera al instalar y se muestra una sola vez en el log de arranque. El sistema obliga a cambiarla en el primer ingreso. Solo en el entorno local, `.env.example` trae una contraseña de desarrollo (`Admin.Local.2026`) que Vagrant copia a `.env` si no existe; en producción nunca se usa ese archivo.
- Los privilegios ENDPOINT se registran solos al arrancar, leyendo los controladores. Los MENU y ACTION del catálogo se cargan por migración y se asignan a ADMIN y MANAGER.

**Desviación (paso 7):** el país Honduras, la empresa de ejemplo, las unidades y los impuestos no se cargan con Flyway, sino con dos `ApplicationRunner` que solo corren en la central (`OrganizationSeeder`, orden 10, y `CatalogSeeder`, orden 20). Motivo: una migración dejaría en la base de cada tienda filas con ids de la central antes de la réplica, y los rangos de ids de las tiendas deben empezar limpios. Cada fila se crea solo si falta, así que el arranque es seguro en cualquier momento. Detalle en "Decisiones de implementación: paso 7 (cierre)".

### Pruebas

**Etapas (decisión del 24 de noviembre de 2026):** el proyecto se hace en dos etapas. En la primera solo se programa: no hay pruebas en el repositorio (las de los pasos 1 y 2 se retiraron) y el CI solo compila el proyecto. La segunda etapa, después de terminar todo el proyecto, agrega las pruebas descritas abajo para todos los módulos.


- **Dominio:** pruebas unitarias de cada regla anterior, en especial la resolución de precio y el cálculo de la base gravable.
- **Integración:** pruebas de repositorios y endpoints contra PostgreSQL real con Testcontainers.
- **Seguridad:** cada endpoint se prueba con un usuario que tiene el privilegio y con uno que no lo tiene.
- **Arquitectura:** la prueba de Spring Modulith falla si un módulo usa clases internas de otro.
- **Meta:** al menos 80 % de cobertura en los paquetes de dominio.

### Entorno local con Vagrant

Un `Vagrantfile` en la raíz del repositorio crea una máquina virtual con todo lo necesario, para que cualquier desarrollador levante la central y las dos tiendas con un solo comando (`vagrant up`), sin instalar Java ni PostgreSQL en su equipo. La máquina virtual usa tres redes privadas, una IP por nodo (`192.168.56.10` central, `192.168.56.11` tienda 1 y `192.168.56.12` tienda 2, configurables con `POS_CENTRAL_IP`, `POS_STORE1_IP` y `POS_STORE2_IP`) en lugar de redirigir puertos a `localhost`, para no chocar con otras aplicaciones del equipo (decisión del 24 de noviembre de 2026).

El desarrollo se hace en **Windows**. En el equipo del desarrollador se instalan Git (con Git Bash), VirtualBox, Vagrant, JDK 25, Docker Desktop y Claude Code: así Claude Code compila y ejecuta las pruebas (incluidas las de Testcontainers) directamente en el equipo, y la máquina virtual Ubuntu se usa para levantar el entorno completo de central y tiendas. El proyecto Maven usa `groupId` `com.medinasystems` y `artifactId` `pos`, con paquete raíz `com.medinasystems.pos`.

**La máquina virtual**

- Ubuntu 24.04 LTS con VirtualBox como proveedor.
- 4 CPU y 8 GB de RAM: alcanzan para tres backends y tres PostgreSQL.
- Al crearse instala Docker y Docker Compose, y al arrancar ejecuta `docker compose up` sobre la carpeta del proyecto, que se comparte con la máquina virtual.
- Reenvía puertos al equipo del desarrollador para usar Swagger desde su navegador.

**Los contenedores**

| Servicio | Contenido | Dirección en la VM |
| --- | --- | --- |
| `central-api` + `central-db` | Backend con perfil central y su PostgreSQL | 192.168.56.10: 8080 (API), 5432 (base), 80 (web) |
| `store1-api` + `store1-db` | Tienda 1: backend con perfil tienda y su PostgreSQL | 192.168.56.11: 8080 (API), 5432 (base), 80 (web) |
| `store2-api` + `store2-db` | Tienda 2: backend con perfil tienda y su PostgreSQL | 192.168.56.12: 8080 (API), 5432 (base), 80 (web) |

**Redes de Docker**

- `wan`: une a los tres backends y simula internet entre tiendas y central.
- `store1-lan` y `store2-lan`: unen cada backend de tienda con su propia base. Ninguna tienda ve la base de otra ni la de la central.

Para simular un corte de internet, se desconecta una tienda de la red `wan` (`docker network disconnect wan store2-api`) y se vuelve a conectar después; un script `scripts/simulate-outage.sh` lo hace en un paso.

**Arranque automático de las tiendas**

Con el perfil `dev`, la central siembra las dos tiendas registradas (nodo 1 y nodo 2, cada una con su sucursal) con códigos de activación fijos que solo existen en desarrollo. Cada tienda recibe su código por variable de entorno, se activa sola al arrancar y hace la carga inicial. En producción, el alta sigue el flujo normal con código de un solo uso.

**Archivos del entorno**

- `Vagrantfile`: crea la máquina virtual e instala Docker.
- `docker-compose.yml`: los seis contenedores y las tres redes.
- `.env.example`: puertos, credenciales de desarrollo y códigos de activación de prueba.
- `scripts/simulate-outage.sh` y `scripts/reset.sh` (borra los datos y vuelve a empezar).

### Criterios de aceptación

- [ ] `vagrant up` crea la máquina virtual y levanta la central y las dos tiendas, con los datos iniciales, sin pasos manuales.
- [ ] Swagger UI de la central (8080) y de cada tienda (8081, 8082) muestra y permite probar sus endpoints.
- [ ] Las dos tiendas se activan solas, hacen la carga inicial y aparecen al día en el panel de la central.
- [ ] El administrador inicia sesión, cambia su contraseña, crea un usuario con rol MANAGER y le quita un privilegio; el MANAGER recibe 403 en ese endpoint.
- [ ] Un usuario con acceso solo a la tienda 1 inicia sesión en la tienda 1 y es rechazado en la tienda 2.
- [ ] `/me/menus` devuelve solo los menús permitidos al usuario.
- [ ] Se crea un árbol de categorías de tres niveles y se rechaza un ciclo.
- [ ] Se crea un producto y un servicio, se busca por nombre, SKU y código de barras, y la búsqueda responde en menos de 300 ms con 10 000 productos, en la central y en las tiendas.
- [ ] Se rechaza un producto con SKU repetido, un servicio con seriales y un producto con seriales y unidad con decimales.
- [ ] Una lista mayorista con escalas devuelve el precio correcto para 1, 12 y 100 unidades, y el precio base fuera de su vigencia.
- [ ] La base gravable e impuesto de un producto de L 115.00 con ISV 15 % dan L 100.00 y L 15.00.
- [ ] Un producto creado o modificado en la central aparece en ambas tiendas en menos de un minuto.
- [ ] Con la tienda 2 desconectada, un cambio de precio llega a la tienda 1; la tienda 2 sigue respondiendo con el precio anterior y el panel la muestra con cambios pendientes. Al reconectarla, se pone al día sola.
- [ ] Un cambio de 10 000 precios se aplica completo en cada tienda, nunca a medias.
- [ ] Una tienda rechaza con 403 cualquier intento de modificar el catálogo.
- [ ] Cambiar el precio de un producto queda en `audit_log` de la central con valor anterior, nuevo, usuario y fecha.
- [ ] Los ids de cada instalación están dentro de su rango (central, tienda 1, tienda 2).
- [ ] Todas las pruebas pasan, incluida la de arquitectura (las pruebas completas se agregan en la segunda etapa).

#### Estado de los criterios (cierre del MVP)

Ningún criterio está marcado todavía: nada se ha probado en la máquina virtual ni con las pruebas automatizadas. Las columnas indican qué paso lo implementa y cómo comprobarlo a mano. Las pruebas automatizadas de estos criterios pertenecen a la **segunda etapa** del proyecto (ver "Pruebas").

Carpetas de la colección Hoppscotch: 1 Sistema, 2 Sesión, 3 Usuarios, 4 Organización, 5 Catálogo, 6 Precios, 7 Tiendas, 8 Auditoría, 9 Errores. Central en `http://192.168.56.10:8080`, tiendas en `http://192.168.56.11:8080` y `http://192.168.56.12:8080`.

| # | Criterio | Implementado en | Cómo verificarlo a mano | Estado |
| --- | --- | --- | --- | --- |
| 1 | `vagrant up` levanta central y tiendas con datos iniciales, sin pasos manuales | Paso 1 (Vagrantfile, `docker-compose.yml`); paso 7 (datos iniciales) | `vagrant up`; después `docker compose logs central-api \| grep "Initial data loaded"` debe mostrar la línea de país, empresa, unidades e impuestos | Pendiente de probar en la VM |
| 2 | Swagger de la central (8080) y de cada tienda (8081, 8082) | Paso 1 | Abrir `/swagger-ui.html` en cada puerto y probar un `GET` | Pendiente de probar en la VM |
| 3 | Las dos tiendas se activan solas, hacen la carga inicial y aparecen al día en el panel | Pasos 6A y 6B | Logs de `store1-api` y `store2-api`: "activated" y "Initial snapshot loaded". Carpeta 7: `GET /store-nodes` con `syncState` `ACTIVE` | Pendiente de probar en la VM |
| 4 | Admin inicia sesión, cambia contraseña, crea un MANAGER, le quita un privilegio y el MANAGER recibe 403 | Paso 2 (login, privilegios) y paso 2B (usuarios y roles) | Carpetas 2 y 3. La colección crea un cajero, no un MANAGER: cambiar el rol a MANAGER a mano. Quitar un privilegio con `PUT /roles/{id}/privileges` y repetir la petición con el token del MANAGER | Pendiente de probar |
| 5 | Usuario con acceso solo a la tienda 1 entra en la tienda 1 y es rechazado en la tienda 2 | Paso 6A (`StoreLoginPolicy`) y paso 3 (`user_branch`) | Requiere que cada tienda tenga su sucursal (`POST /store-nodes` con `branchId`). Las tiendas de desarrollo se crean **sin sucursal**, así que en el entorno local por defecto este criterio no se puede comprobar | No cumplido en el entorno local por defecto |
| 6 | `/me/menus` devuelve solo los menús permitidos | Paso 2 | Carpeta 2, "Mis menús", con el admin y con un MANAGER sin menús: la lista debe cambiar | Pendiente de probar |
| 7 | Árbol de categorías de tres niveles y rechazo de ciclos | Paso 4 | Carpeta 5 crea raíz y subcategoría; crear una tercera a mano bajo la subcategoría. Carpeta 9, "422 Ciclo de categorías" | Pendiente de probar (el nivel tres se hace a mano) |
| 8 | Producto y servicio; búsqueda por nombre, SKU y código de barras en menos de 300 ms con 10 000 productos, en central y tiendas | Paso 4 | Carpeta 5 (búsquedas). **El tiempo de respuesta no está medido**: hace falta cargar 10 000 productos (no hay script de carga) | Parcial: funcionalidad sin medir el tiempo |
| 9 | Se rechaza SKU repetido, servicio con seriales y producto con seriales y unidad con decimales | Paso 4 | Carpeta 9: "422 SKU repetido" y "422 Servicio con series". El caso de seriales con unidad decimal se prueba a mano con `allowsDecimals: true` y `tracksSerials: true` | Pendiente de probar (dos casos en la colección) |
| 10 | Lista mayorista con escalas: precio correcto para 1, 12 y 100 unidades, y precio base fuera de vigencia | Paso 5 | Carpeta 6: "Precio con lista mayoreo" (cantidades 1, 12 y 100) y "Precio VIP fuera de vigencia" | Pendiente de probar |
| 11 | Un producto de L 115.00 con ISV 15 % da base L 100.00 e impuesto L 15.00 | Paso 5 (`TaxBreakdown`) | Un producto con precio 115 y `ISV15`; `GET /products/{id}/price` debe devolver `taxableBase` 100.00 y `taxAmount` 15.00 | Pendiente de probar |
| 12 | Un producto creado o modificado en la central aparece en ambas tiendas en menos de un minuto | Paso 6B (intervalo de 30 s y 15 s de arranque) | Carpeta 5 cambia el precio en la central; luego `GET /products/{id}/price` en `192.168.56.11:8080` y `192.168.56.12:8080` tras unos 60 s | Pendiente de probar |
| 13 | Con la tienda 2 desconectada, el cambio llega a la tienda 1; la tienda 2 responde el precio anterior, el panel muestra cambios pendientes y al reconectarla se pone al día | Pasos 6B y 7 (script de corte) | `scripts/simulate-outage.sh store2 down`, cambiar el precio, comprobar las tres tiendas, `scripts/simulate-outage.sh store2 up`. Detalle en el README | Pendiente de probar |
| 14 | Un cambio de 10 000 precios se aplica completo en cada tienda, nunca a medias | Paso 6B | **No cumplido por diseño actual:** la réplica aplica cada página de 500 cambios en su propia transacción. Una tienda puede mostrar precios nuevos y viejos mezclados mientras dura el ciclo. Pendiente de decidir (ver "Decisiones: paso 7") | No cumplido |
| 15 | Una tienda rechaza con 403 cualquier intento de modificar el catálogo | Pasos 4 y 6A (`@CentralOnly`, `READ_ONLY_NODE`) | Con el token de la tienda, `POST /products` en `192.168.56.11:8080` debe responder 403 con `code: READ_ONLY_NODE` | Pendiente de probar |
| 16 | Cambiar el precio de un producto queda en `audit_log` de la central con valor anterior, nuevo, usuario y fecha | Paso 3 (`audit_log`), paso 4 (`PRICE_CHANGED`) | Carpeta 8: `GET /audit-log?entity=product&entityId=<id>` tras cambiar el precio | Pendiente de probar |
| 17 | Los ids de cada instalación están dentro de su rango (central, tienda 1, tienda 2) | Paso 6A (`IdRangeSequenceAdjuster`, rangos por nodo) | Central: ids de 1 a 999 999 999. Tiendas: `store_identity` con su rango. Comprobar que las secuencias de la tienda arrancan en su rango. Las tiendas todavía no crean registros propios (eso llega con las ventas), así que no hay ids generados por la tienda que verificar | Parcial |
| 18 | Todas las pruebas pasan, incluida la de arquitectura | Segunda etapa | `./mvnw verify`. En esta etapa no hay pruebas en el repositorio, solo compilación | Fuera de alcance de esta etapa |

### Orden de trabajo

1. **Esqueleto y entorno:** proyecto Maven, módulos de Spring Modulith, perfiles `central` y `store`, Flyway, errores RFC 9457, Swagger, `Vagrantfile` y `docker-compose.yml` con la central y las dos tiendas.
2. **Seguridad:** login JWT, usuarios, roles, privilegios, acceso por sucursal y registro automático de endpoints.
3. **Organización y auditoría:** países, empresa, sucursales, columnas de auditoría y `audit_log`.
4. **Catálogo:** categorías, unidades, impuestos, productos y `product_tax`.
5. **Precios:** listas, escalas y resolución de precio.
6. **Sincronización:** alta de tiendas, códigos de activación, registro de cambios, carga inicial, bajada periódica, latido y panel de estado.
7. **Cierre:** datos iniciales, script de corte de internet, revisión de criterios de aceptación, README y colección de ejemplos para probar la API.

**Entregables:** el repositorio con el código, el Vagrantfile, el `docker-compose.yml`, las migraciones Flyway, el contrato OpenAPI, la colección de ejemplos de la API y un README para levantar el proyecto.

## MVP del frontend (Angular)

El MVP del backend (base y catálogos, pasos 1 a 7) quedó terminado y unido a `main` el 24 de noviembre de 2026. El MVP del frontend (Angular, pasos 1 a 7) quedó terminado el 24 de noviembre de 2026. Las pruebas automatizadas del backend (etapa 2) quedan para más adelante.

### Decisiones

| Tema | Decisión | Motivo |
| --- | --- | --- |
| Framework | Angular 22 con TypeScript en modo estricto | Versión estable actual; soporte activo hasta junio de 2027 y parches de seguridad hasta junio de 2028 |
| Componentes y estilos | Bootstrap 5.3 con ng-bootstrap 21 (licencia MIT) | Gratis para uso comercial, compatible con Angular 22 y fácil de personalizar para pantallas de caja |
| Descartado | PrimeNG | Desde la versión 22 su licencia es de pago para empresas que no son pequeñas |
| Repositorio | Aparte: `pointOfSales-frontend` | Se comunica con el backend solo por la API; mismo flujo de ramas, PR y CI |
| Cliente de la API | Generado desde el OpenAPI del backend | Si cambia un endpoint, se regenera y el compilador avisa |
| Idiomas | Español e inglés, español por defecto | La misma regla del backend; el idioma elegido viaja en `Accept-Language` |
| Despliegue local | Una VM con tres IPs privadas, una por nodo (central `192.168.56.10`, tienda 1 `.11`, tienda 2 `.12`); cada nodo tiene su web en el puerto 80 y su API en el 8080 de su IP; el `docker-compose.yml` del backend construye la web desde la carpeta vecina `../pointOfSales-frontend` | Cada nodo se comporta como una máquina distinta, como en producción, sin ocupar puertos de localhost |
| Despliegue en servidores | Imagen publicada por el CI del frontend en GitHub Container Registry | Para Oracle Cloud y producción, sin necesitar el código |
| Llamadas a la API | La web llama directamente a la API de su propio nodo; la dirección se configura al arrancar el contenedor (`API_BASE_URL` → `config.json`); el backend permite ese origen con CORS (`POS_CORS_ALLOWED_ORIGINS`) | Cada tienda usa su web y su API locales y sigue funcionando sin internet; no hay proxy ni selector de servidor |
| Menús | Los menús de cada pantalla se crean al arrancar la central (no con Flyway) y llegan a las tiendas por réplica; el frontend traduce el nombre por su código | Una migración en la tienda crearía filas con ids de su rango que chocarían con las replicadas |

### Dentro del MVP del frontend

- **Sesión:** login, cambio de contraseña obligatorio, renovación automática del token y cierre de sesión.
- **Menú y permisos:** el menú se arma con `/me/menus`; los botones se muestran según `/me/privileges`.
- **Administración:** usuarios, roles, privilegios y sucursales asignadas a cada usuario.
- **Organización:** países, empresa y sucursales.
- **Catálogo:** categorías en árbol, unidades, impuestos y productos, con búsqueda por nombre, SKU o código de barras.
- **Precios:** listas, escalas y un consultor de precio que muestra precio, base e ISV.
- **Tiendas y réplica:** panel de tiendas en la central (conectada, cambios pendientes, último latido), estado de sincronización en la tienda y bitácora de auditoría.

**Fuera del MVP del frontend:** pantalla de venta (caja), inventario, clientes, reportes e instalación como aplicación (PWA); llegan con sus módulos del backend.

### Orden de trabajo del frontend

1. **Base:** proyecto Angular 22 con Bootstrap y ng-bootstrap, cliente de la API generado, traducciones, Docker con nginx y CI. (hecho)
2. **Sesión y menú:** login, cambio de contraseña, token y menú por privilegios. (hecho)
3. **Administración:** usuarios, roles, privilegios y sucursales por usuario. (hecho)
4. **Organización y catálogo:** países, empresa, sucursales, categorías, unidades, impuestos y productos. (hecho)
5. **Precios:** listas, escalas y consultor de precio. (hecho)
6. **Tiendas y réplica:** panel de tiendas, estado en la tienda y bitácora. (hecho)
7. **Cierre:** integración con Vagrant, README y revisión de criterios. (hecho)

### Criterios de aceptación del frontend

- [x] `vagrant up` levanta también la web y se abre en el navegador.
- [x] El administrador inicia sesión, cambia la contraseña y ve solo los menús que le corresponden; un usuario sin un privilegio no ve el botón de esa acción.
- [x] Se crean país, empresa, sucursal, categorías, impuesto, unidad y producto desde la web, y los errores del backend se muestran en el idioma elegido.
- [x] Un producto creado en la web de la central aparece en la web de la tienda 1 (http://192.168.56.11) en menos de un minuto.
- [x] El panel muestra la tienda 2 desconectada durante un corte de internet y al día cuando vuelve.
- [x] El consultor de precio muestra L 100.00 de base y L 15.00 de ISV para un producto de L 115.00 con ISV 15 %.
- [x] Las pantallas funcionan en español y en inglés, y en una tablet.

Verificados manualmente por el responsable el 24 de noviembre de 2026 en el entorno local (Vagrant con central y dos tiendas). Las pruebas automatizadas quedan para la segunda etapa.

## Decisiones y preguntas abiertas

Las respuestas del 16 de octubre fijan diez decisiones; quedan siete preguntas por cerrar antes de escribir código.

| Tema | Decisión | Impacto en el diseño |
| --- | --- | --- |
| Régimen fiscal | Multipaís; la facturación es un módulo que se implementa por país, independiente del resto | Módulo `fiscal` con un proveedor por país; Honduras (SAR) es el primero. Ver Arquitectura y tablas `country`, `fiscal_authorization` |
| Giro | Retail de productos y servicios | Campo `product_type` (GOOD, SERVICE); los servicios no mueven inventario. Sin lotes, vencimientos ni mesas |
| Escala | Muchas sucursales | Todo filtrado por sucursal, índices por `branch_id`, reportes con tablas resumen diarias |
| Crédito | Sí, con alcance ampliado | Nuevo módulo de cuentas por cobrar: límite de crédito, plazos, saldos y abonos (fase 7) |
| Precios | Siempre con ISV incluido | El precio de lista ya trae impuesto; el sistema calcula la base gravable hacia atrás |
| Listas de precios | Sí, configurables | Tablas `price_list` y `price_list_item`, asignables a sucursal y a cliente |
| Sin conexión | Servidor por tienda, porque las redes fallan con frecuencia | Un servidor local por tienda con el mismo backend, que sincroniza con la central; ver Arquitectura central y tienda |
| Frontend | Web y aplicación web instalable (PWA) | API con CORS configurable, tokens JWT, impresión desde el navegador |
| Despliegue | Servidor propio o nube | Contenedores Docker sin servicios exclusivos de un proveedor de nube |
| Integraciones | Ninguna por ahora | Los códigos externos quedan listos para un ERP futuro |

**Preguntas que siguen abiertas**

- [ ] ¿Quién decide si una venta va como factura o como recibo: el cajero, el tipo de producto o el cliente?
- [ ] ¿Los recibos llevan desglose de ISV?
- [ ] Factura exclusiva: ¿la venta solo puede llevar ese producto, o pueden ir juntos varios productos exclusivos?
- [x] Resuelto: los productos no llevan código externo del ERP.
- [ ] Crédito: ¿hay intereses o recargos por mora? ¿Se aceptan abonos parciales en cualquier sucursal? ¿Se bloquea al cliente que excede su límite o se pide autorización? Sin conexión con la central, ¿cada tienda recibe un cupo de crédito o se exige conexión para vender al crédito?
- [ ] Listas de precios: ¿se necesitan precios por cantidad (escalas de mayoreo) o promociones como 2x1 y combos?
- [ ] Multipaís: ¿una empresa puede tener sucursales en varios países, o cada país es una empresa distinta?

### Decisiones de implementación: paso 3 (organización y auditoría)

- Las columnas `CHAR(n)` del modelo se implementan como `VARCHAR(n)` (`iso_code`, `currency`). `ddl-auto=validate` compara tipos y así coinciden con los campos `String` de JPA; el contenido y las restricciones no cambian.
- `country` lleva también `name` y `tax_id_label`, tal como indica la tabla de este documento.
- `branch.default_price_list_id` no tiene clave foránea hasta el paso 5, cuando existe `price_list`.
- Códigos de error nuevos: `COUNTRY_INACTIVE`, `COUNTRY_NOT_FOUND`, `DUPLICATE_ISO_CODE`, `DUPLICATE_TAX_ID`, `DUPLICATE_BRANCH_CODE`, `COMPANY_NOT_CONFIGURED`, `BRANCH_NOT_FOUND` (422), y `READ_ONLY_NODE` (403 en la tienda).
- Una sucursal que este nodo no ve responde 404 en lugar de 403, para no revelar que existe. La central ve todas; cada tienda solo la suya.
- Crear una sucursal no da acceso a nadie, ni siquiera a quien la crea. Cada usuario entra solo a las tiendas que el administrador le asigna en `user_branch` con `PUT /users/{id}/branches`.
- Los eventos de auditoría de cambios guardan solo los campos que cambiaron; las altas no tienen valores anteriores.
- Decisión del 24 de noviembre de 2026: cada servidor central atiende un solo país. La tabla `country` no cambia; crear un segundo país se rechaza con `SINGLE_COUNTRY_ONLY` (422). Cambiar el país existente sigue permitido. Sucursales, impuestos y listas de precios siguen llevando su país, y los países se replican a las tiendas como el resto de datos maestros. El arranque de la central crea Honduras solo si no hay ningún país.

### Decisiones de implementación: paso 4 (catálogo)

- Migración `V6__catalog.sql`. Desviaciones del modelo, por las reglas de `CLAUDE.md`:
  - `unit_of_measure` lleva `active`, porque la regla `UNIT_INACTIVE` lo necesita.
  - `product_tax` tiene `id` propio, `UNIQUE (product_id, country_id)` y columnas de auditoría. La clave compuesta del plan se cambia por esta, para cumplir "id BIGINT y auditoría en todas las tablas".
  - `product.min_stock` es `NOT NULL DEFAULT 0` y tiene `CHECK >= 0`. `price_includes_tax` tiene `CHECK (price_includes_tax)`.
  - `tax` y `product_tax` tienen clave foránea a `country` (integridad referencial del esquema). El código del módulo catálogo no accede a la entidad `Country`.
- Contrato entre módulos: `organization.CountryDirectory` (raíz del módulo organización) con `isActive(long countryId)`. Su implementación vive en `organization/application/DefaultCountryDirectory`. Un país inexistente responde `false` y se reporta como `COUNTRY_INACTIVE`.
- Un impuesto guarda su país como `country_id` (BIGINT). Su país no cambia después de creado (`TAX_COUNTRY_IMMUTABLE`), porque los productos ya lo usan para el país indicado.
- Reglas adicionales a las del plan:
  - `NEGATIVE_QUANTITY`: el stock mínimo no puede ser negativo.
  - Un `SERVICE` con `tracksInventory = true` se guarda con `false` sin error. Un `SERVICE` con `tracksSerials = true` sí responde `SERVICE_WITH_SERIALS`.
  - `TAX_IN_USE` cuenta los productos activos que usan el impuesto y cualquier fila de `product_tax` que lo use (la tabla no tiene `active`).
  - Una categoría no puede quedar inactiva con subcategorías o productos activos (`CATEGORY_IN_USE`), al desactivarla con PUT o con DELETE.
  - Las unidades de medida no tienen DELETE y no tienen comprobación de uso.
- Evento `PRICE_CHANGED` (entidad `product`): solo se publica si cambió `salePrice` o `cost`. Además se publica `PRODUCT_UPDATED` con todos los cambios.
- `DELETE` de categorías y productos responde 200 con la entidad desactivada, en lugar de 204.
- La búsqueda por código de barras o SKU devuelve también productos inactivos, para consultas históricas. El filtrado de venta llegará con el módulo de ventas.
- `PUT /products/{id}/taxes` reemplaza la lista completa. Las filas existentes se actualizan en lugar de borrarse y crearse, para no romper la clave única durante el flush.
- Los indicadores booleanos omitidos en las peticiones toman un valor por defecto: `active = true`, `tracksInventory = true`, `tracksSerials = false`, `requiresExclusiveInvoice = false`, `allowsDecimals = false`, `productType = GOOD`.
- Mensajes: los textos que ve el usuario van en `messages.properties` (inglés) y `messages_es.properties` (español), bajo la clave `error.<CODIGO>`. `GlobalExceptionHandler` los resuelve con el idioma de la petición; sin `Accept-Language`, `spring.web.locale` usa español. Pendiente: los textos de los módulos organización y seguridad siguen escritos en el código y se migrarán cuando se pida. En el paso 4, el mensaje escrito en el código sirve de respaldo si falta la traducción.
- Códigos de error nuevos: `CATEGORY_CYCLE`, `CATEGORY_IN_USE`, `CATEGORY_NOT_FOUND`, `DUPLICATE_UNIT_CODE`, `UNIT_NOT_FOUND`, `UNIT_INACTIVE`, `INVALID_TAX_RATE`, `DUPLICATE_TAX_CODE`, `TAX_NOT_FOUND`, `TAX_INACTIVE`, `TAX_IN_USE`, `TAX_COUNTRY_MISMATCH`, `TAX_COUNTRY_IMMUTABLE`, `DUPLICATE_SKU`, `DUPLICATE_BARCODE`, `SERVICE_WITH_SERIALS`, `SERIALS_REQUIRE_INVENTORY`, `SERIALS_REQUIRE_WHOLE_UNIT`, `NEGATIVE_AMOUNT`, `NEGATIVE_QUANTITY`, `DUPLICATE_PRODUCT_TAX_COUNTRY` (422), y `COUNTRY_INACTIVE` (compartido con organización).

### Decisiones de implementación: paso 5 (precios)

- Migración `V7__pricing.sql`: crea `price_list` y `price_list_item` según el plan, con columnas de auditoría, `version` y las restricciones del plan. Además:
  - `price_list.percent` es obligatorio con `PERCENT_OFF_BASE`, y `valid_to` no puede ser anterior a `valid_from`; la base de datos lo verifica también con `CHECK`.
  - `ALTER TABLE branch ADD CONSTRAINT fk_branch_default_price_list`, la clave foránea que el paso 3 dejó pendiente.
- Vigencia en `DATE`, como indica el plan. `CLAUDE.md` dice que las fechas son `TIMESTAMPTZ`; aquí se sigue el plan porque la vigencia es un día del calendario y no un instante. Pendiente de confirmar.
- Las listas y las escalas guardan `country_id` y `product_id` como ids (`BIGINT`), sin entidades de otros módulos. El módulo de precios no toca las tablas ni las clases internas de catálogo u organización.
- Contratos entre módulos, en la raíz de cada uno, con su implementación en `application`:
  - `catalog.ProductPricingCatalog.find(productId, countryId)` devuelve `ProductPricingInfo`. La tasa es la de `product_tax` para ese país y, si no hay fila, la del producto.
  - `organization.BranchDirectory.find(branchId)` devuelve `BranchPricingInfo` (país y lista por defecto).
- Un porcentaje con `FIXED_PRICE` se descarta (queda en `NULL`) en lugar de rechazarse, igual que `tracksInventory` en un servicio.
- Las escalas solo existen en listas `FIXED_PRICE`. No se puede cambiar una lista con escalas a `PERCENT_OFF_BASE` (`ITEMS_REQUIRE_FIXED_PRICE`).
- Un producto debe existir en el país de la lista para tener una escala (`PRODUCT_NOT_FOUND`). Un producto inactivo no tiene precio de venta (`PRODUCT_INACTIVE`, 422).
- Resolución de precio (`PriceResolver` y `TaxBreakdown` en `domain`, sin acceso a la base de datos):
  - Una cantidad menor que la primera escala usa el precio base, aunque la lista exista.
  - `priceListId` se usa si se indica; si no, la lista por defecto de la sucursal. El país sale de la lista o, sin lista, de la sucursal. Sin ninguna de las dos responde `PRICE_CONTEXT_REQUIRED`.
  - Si se indica `branchId`, la sucursal debe estar entre las del usuario; si no, responde 404, igual que el módulo de organización.
  - Sin `date`, se usa el día actual en la zona horaria del país de la lista (o de la sucursal). Resuelto en el paso 7; ver allí.
  - `priority` se guarda, pero la resolución no lo usa todavía: la lista ya se elige de forma explícita (cliente o sucursal).
- Las sucursales aceptan `defaultPriceListId` en `POST` y `PUT`. Un id que no existe provoca el error de clave foránea al guardar y se responde `PRICE_LIST_NOT_FOUND` (422). Pendiente: no se valida que la lista sea del mismo país que la sucursal, porque el plan no lo pide.
- Los ítems se borran de verdad (`DELETE` responde 204), porque no son catálogo. La bitácora conserva el precio anterior en `PRICE_LIST_ITEM_DELETED`.
- La auditoría guarda las fechas de vigencia como texto ISO (`2026-01-01`) para no depender del formato JSON de la bitácora.
- Códigos de error nuevos: `DUPLICATE_PRICE_LIST_CODE`, `INVALID_PERCENT`, `INVALID_VALIDITY`, `INVALID_MIN_QUANTITY`, `DUPLICATE_PRICE_SCALE`, `ITEMS_REQUIRE_FIXED_PRICE`, `PRODUCT_NOT_FOUND`, `PRODUCT_INACTIVE`, `PRICE_CONTEXT_REQUIRED`, `INVALID_QUANTITY` y `PRICE_LIST_NOT_FOUND` (todos 422). `COUNTRY_INACTIVE` y `NEGATIVE_AMOUNT` se reutilizan.
- Privilegios: los endpoints nuevos se registran solos y ADMIN los recibe. MANAGER empieza sin privilegios, como en el catálogo; el administrador se los asigna.

### Decisiones de implementación: paso 6A (alta de tiendas)

Alcance: registro de tiendas en la central, activación, credenciales de la tienda y rangos de ids. La réplica de datos maestros, el latido y la subida de documentos quedan para los pasos siguientes.

**Base de datos (`V8__store_nodes.sql`)**

- `store_node` sigue el plan, con estas desviaciones:
  - `activation_code_hash` es `VARCHAR(64)` (SHA-256 en hexadecimal) en lugar de `VARCHAR(255)`.
  - Se agrega `credential_hash VARCHAR(64) UNIQUE`: el hash del token permanente de la tienda. El plan no lo nombraba, pero hace falta para autenticar.
  - `status` tiene `CHECK` con `PENDING`, `ACTIVE`, `OUT_OF_SYNC` y `DISABLED`. `OUT_OF_SYNC` no lo asigna ningún código todavía: lo hará el latido.
  - `node_number` tiene `CHECK (>= 1)`, y el rango tiene `CHECK (end > start)`.
  - `branch_id` es `UNIQUE`: una sucursal tiene como máximo una tienda.
- La central no es una fila de `store_node`. El nodo 0 es la propia central, con ids de 1 a 999 999 999, y ese rango está en el código.
- `store_identity` es la tabla local de la tienda, con una sola fila. Sin claves foráneas a `store_node` ni a `branch`, porque esas tablas llegan por replicación en el paso siguiente.
- `store_identity.store_token` se guarda en claro, porque la tienda lo necesita para llamar a la central. La base de datos de la tienda no sale del servidor de la tienda.
- `snapshot_completed_at` y `last_applied_version` quedan en la tabla, sin uso todavía: los usará la carga inicial.

**Rangos de ids**

- Tienda N: inicio `N × 1 000 000 000 + 1`, fin `N × 1 000 000 000 + 999 999 999`. Ejemplo: la tienda 1 va de 1 000 000 001 a 1 999 999 999.
- El número se asigna como `máximo + 1`, empezando en 1. Con dos alta simultáneas, una de las dos choca con la unicidad de `node_number` y responde 500. No se maneja todavía.
- `node_number` es `SMALLINT`, así que el límite es 32 767. Más allá se responde `STORE_NODE_LIMIT_REACHED` (422).
- `IdRangeSequenceAdjuster` recorre todas las tablas del esquema con una columna `id` de identidad (excepto `flyway_schema_history`) y pone cada secuencia en `max(inicio, máximo del rango + 1)`. Puede retroceder una secuencia sobre ids que nunca se confirmaron. Si el nombre de una tabla no pasa la expresión regular `^[a-z_][a-z0-9_]*$`, el proceso falla en lugar de saltarse la tabla. Si el rango se agota, también falla.

**Códigos y tokens**

- Código de activación: 16 caracteres del alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 símbolos, sin I, L, O, 0 ni 1), generados con `SecureRandom`. Vence en 24 horas y se usa una sola vez: al canjearse se borra su hash.
- Token permanente: 32 bytes aleatorios en base64url, sin relleno. La central guarda solo su SHA-256.
- Un código se puede pedir de nuevo para una tienda activa, por ejemplo para reinstalarla. La reactivación reemplaza el token anterior y reinicia `activated_at`.
- Revocar: `credential_revoked_at` se llena, el estado pasa a `DISABLED`, el token se borra y la sucursal se libera (`branch_id` queda en `NULL`), para que otra tienda pueda tomarla. El hash del código, si existe, se conserva: el canje devuelve `STORE_REVOKED`. Revocar dos veces no hace nada. Una tienda revocada no se reactiva.
- Errores de activación `INVALID_ACTIVATION_CODE` y `STORE_REVOKED`: 422 (`BusinessRuleException`). Un código ya usado es un código desconocido.
- Hashes con SHA-256 simple, sin sal. Los secretos son aleatorios y largos, así que no se pueden adivinar a partir del hash.
- Ningún log ni auditoría contiene el código ni el token. La auditoría de la central registra `STORE_NODE_CREATED`, `STORE_NODE_ACTIVATION_CODE_CREATED`, `STORE_NODE_ACTIVATED` y `STORE_NODE_REVOKED`. La tienda registra `STORE_ACTIVATED`.

**Autenticación de la tienda**

- Cabecera `X-Store-Token`. El filtro `StoreTokenAuthenticationFilter` se registra en `SyncWebConfig` para `/api/v1/sync/*`, con orden anterior a Spring Security. Está excluido en `/api/v1/sync/activate` y `/api/v1/sync/status`. Sin token válido responde 401 `STORE_UNAUTHORIZED`.
- Si el token es válido, el filtro guarda el id de la tienda en el atributo `posStoreNodeId`.
- Spring Security deja pasar `/api/v1/sync/**` sin usuario (`permitAll`), salvo `GET /api/v1/sync/status`, que exige un token de usuario. Cualquier ruta nueva bajo `/api/v1/sync/**` queda protegida solo si el filtro la cubre: revisar esa regla al agregar rutas de tienda.
- `EndpointPrivileges` excluye de privilegio solo `/api/v1/sync/activate` y `/api/v1/sync/ping`. `/api/v1/sync/status` sí tiene privilegio ENDPOINT. Esto se aparta de "agregar `/api/v1/sync/**` como las rutas públicas": el estado de la tienda no debe quedar abierto a cualquier usuario autenticado.
- El `ProblemJsonWriter` de seguridad es interno a su módulo. Por eso `sync/web` tiene `StoreProblemWriter`, una copia pequeña del mismo formato.

**Solo central y solo lectura en la tienda**

- Con `@CentralOnly` (403 `READ_ONLY_NODE` en la tienda): todo `/api/v1/store-nodes` (incluida la lectura, porque la lista de tiendas es dato de la central), `POST /api/v1/sync/activate` y `GET /api/v1/sync/ping`.
- `POST /api/v1/me/password` ya no es solo central: el usuario puede cambiar su contraseña en la tienda. Ver "Decisiones de implementación: cambio de contraseña en la tienda" más abajo.
- `GET /api/v1/sync/status`: en la central responde 200 con `node: "CENTRAL"` y el resto de los campos vacíos. En la tienda, `node: "STORE"` y `activated` indica si ya tiene identidad. Nunca incluye el token.

**Activación de la tienda**

- Al arrancar, la tienda activa se limita a ajustar sus secuencias. La que no está activada y tiene `POS_ACTIVATION_CODE` lanza la activación en un hilo virtual, para no bloquear el arranque.
- Reintentos cada 10 segundos, hasta 30 intentos, solo si la central no responde (error de red). Si la central responde 4xx, se deja de intentar y el log lo indica. Si se agotan los intentos, hay que reiniciar la tienda.
- Si el número que asigna la central no coincide con `pos.node.number`, la identidad no se guarda y el log lo registra como error.
- Riesgo sin resolver: si la central acepta el código y luego falla la escritura de `store_identity`, el código queda consumido y el token se pierde. No se maneja todavía.

**Desarrollo local**

- `pos.sync.dev-activation-codes` (`POS_DEV_ACTIVATION_CODES`) hace que la central cree o actualice las tiendas indicadas al arrancar. Solo crea la tienda y prepara el código si la tienda no está activada ni revocada. Renueva el vencimiento en cada arranque. Las tiendas creadas así no tienen sucursal. El log dice solo el número de tienda, nunca el código.
- `docker-compose.yml` y `.env.example` traen los valores de desarrollo. En producción no se usan.

**Control de acceso al iniciar sesión**

- `security.LoginPolicy` (API pública de seguridad) con una implementación por defecto que permite todo (`DefaultLoginPolicy`). La tienda usa `sync.StoreLoginPolicy`, marcada como `@Primary` en el perfil `store`.
- La tienda con `branch_id` solo deja entrar a usuarios que tengan esa sucursal en `user_branch`. Sin `branch_id`, deja entrar a cualquiera. Antes de activarse, no restringe nada, porque todavía no hay usuarios replicados.
- La política se evalúa después de comprobar la contraseña, para que una contraseña errónea no revele las reglas de la tienda. También se evalúa al renovar el token de refresco, para que un cambio de sucursal tenga efecto.
- Denegado: `LoginNotAllowedException("STORE_BRANCH_NOT_ALLOWED")`, con 403 y `code` en el cuerpo.

**Otros cambios**

- `BranchDirectory.exists(branchId)` en la API pública de organización, para validar la sucursal al registrar una tienda (`BRANCH_NOT_FOUND`, 422).
- Respuestas JSON comprimidas con gzip desde 2 KB (`server.compression`), para la sincronización futura.
- Sin cambios en `sync_change_log`: las tiendas no son datos maestros, así que su alta no se replica todavía.
- La sucursal de una tienda se asigna o cambia desde la central (`PUT /store-nodes/{id}/branch`); la tienda la recibe en el siguiente latido. Una sucursal pertenece a una sola tienda y una tienda revocada no se reasigna. Necesario porque las tiendas de desarrollo se registran solas antes de que exista ninguna sucursal.

**Pendiente o no verificado**

- No se compiló ni se ejecutó: el entorno de esta sesión no tiene Maven ni las dependencias. Solo se comprobó la sintaxis con `javac`.
- No hay pruebas, como exige la primera etapa del proyecto.
- Falta la réplica de datos maestros (`sync_change_log`, descarga inicial), el latido (que asignará `OUT_OF_SYNC`) y la subida de documentos.
- Decisión del 24 de noviembre de 2026: la visibilidad de sucursales depende del nodo, no del usuario. La central ve todas; cada tienda solo la suya. `user_branch` solo controla en qué tienda puede entrar cada usuario.

### Decisiones de implementación: paso 6B (réplica)

Alcance: la central registra los cambios de datos maestros, sirve la carga inicial y los cambios, y cada tienda los aplica cada 30 segundos. Las tiendas todavía no suben documentos.

**Captura de cambios (`V9__sync_change_log.sql`)**

- `sync_change_log` sigue el plan. La clave es `version BIGINT GENERATED ALWAYS AS IDENTITY`. Es de solo anexar: no tiene columnas de auditoría.
- Los cambios se capturan con triggers `AFTER INSERT OR UPDATE OR DELETE` (función `sync_log_change()`), no desde el código de la aplicación. Así cada cambio queda en el registro dentro de la misma transacción que lo modifica, incluso si el cambio viene de un `UPDATE` directo en la base.
- Las tablas principales registran su propia fila (`TG_ARGV`: entidad, columna del id, `main`). Las hijas (`user_role`, `user_branch`, `role_privilege`) registran un `UPSERT` de su entidad padre: el cambio se reenvía completo, con todos sus hijos.
- En una tienda, la función no registra nada. La conexión se marca con `SET pos.node_role = 'store'` (`spring.datasource.hikari.connection-init-sql` en `application-store.yml`), y la aplicación de la réplica activa `pos.sync_apply = 'on'`.
- La migración inserta un `UPSERT` por cada fila que ya existe en las tablas principales, para que una tienda que nunca sincronizó reciba también los datos anteriores.
- `branch_id` queda en `NULL` en todas las filas: todo se replica a todas las tiendas. El filtrado por sucursal se hará después, sin cambiar el esquema.

**Registro de tablas (`sync/domain/SyncTables`)**

- Orden de las tablas principales: `country`, `company`, `role`, `privilege`, `app_user`, `branch`, `category`, `unit_of_measure`, `tax`, `product`, `product_tax`, `price_list`, `price_list_item`. Después van las hijas: `user_role`, `user_branch`, `role_privilege`.
- Los nombres son constantes en el código. Lo que llega en una petición solo se compara contra el registro, nunca entra al SQL.

**Formato de las filas**

- Cada fila viaja como texto JSON (`row_to_json` en la central). En las respuestas, la fila es un string dentro del JSON, así que la central no las convierte a tipos Java y la tienda se las pasa a PostgreSQL tal cual.
- La tienda escribe con `json_populate_record` y `json_populate_recordset`: PostgreSQL hace el mapeo de columnas. Los nombres de columnas salen de `information_schema`, y las tablas del registro.

**Aplicación en la tienda (`ReplicaWriter`)**

- Cada escritura corre en una transacción que empieza con `SET LOCAL session_replication_role = replica`. Eso desactiva las comprobaciones de llaves foráneas y los triggers solo en esa transacción, así que el orden de las filas no importa dentro de un lote.
- Requiere que el usuario de la base pueda cambiar `session_replication_role`. En Docker es el propietario de la base, que es superusuario. Si algún día se usa un usuario sin ese permiso, la réplica falla con error explícito.
- Upsert de una fila principal: `INSERT ... SELECT r.* FROM json_populate_recordset(...) ON CONFLICT (id) DO UPDATE SET` con todas las columnas menos `id`.
- Hijos de una entidad: se borran los del id de la entidad y se insertan los que llegaron. Si no llegó ninguno, quedan en cero.
- Borrado de una entidad: se borran primero sus hijos y luego la fila.
- Un cambio y la versión aplicada se guardan en una sola transacción: si algo falla, ni las filas ni la versión quedan a medias.

**Carga inicial (snapshot)**

1. `GET /api/v1/sync/snapshot/version` da la versión `v0`.
2. La tienda vacía todas las tablas replicadas, hijas primero y luego principales en orden inverso. Esto también borra lo que la tienda creó en la 6A (ver abajo).
3. Por cada tabla del registro, en orden, pide páginas de 1000 con `afterId`. Para tablas principales la página es por id. Para hijas, la página es por id padre distinto: se traen los hijos de esos padres y se reemplazan en la tienda.
4. Al terminar, `last_applied_version = v0` y `snapshot_completed_at` se llena. Los cambios posteriores a `v0` se aplican después, así que cualquier cambio hecho durante la carga se recibe de nuevo y no se pierde (las escrituras son idempotentes).
5. Si la carga se interrumpe, `snapshot_completed_at` queda en `NULL` y el siguiente ciclo vuelve a empezar desde el paso 2.

**Ciclo de sincronización (`StoreReplicator`)**

- `@Scheduled(fixedDelay = pos.sync.interval, initialDelay = pos.sync.initial-delay)`, 30 segundos y 15 segundos por defecto. Solo en el perfil `store`; `@EnableScheduling` vive en `SyncSchedulingConfig`, también solo en `store`.
- Cada ciclo: carga inicial si falta; luego latido (`POST /api/v1/sync/heartbeat` con la versión aplicada); luego cambios página por página (`GET /api/v1/sync/changes?sinceVersion=`, 500 por página), aplicación transaccional y `POST /api/v1/sync/ack`, mientras `hasMore` sea verdadero.
- Errores: se capturan, se guardan en `store_identity.last_sync_error` (máximo 500 caracteres) y se registran en el log con solo la clase de excepción y el mensaje. Nunca el token ni la petición. El siguiente ciclo reintenta.
- La tienda nunca llama a la central desde una petición de usuario: las lecturas siguen funcionando si la central está caída.
- El cliente tiene tiempos de espera (5 s para conectar, 30 s para leer), para que una conexión colgada no bloquee el ciclo para siempre.

**Central: endpoints de réplica (`SyncReplicationController`, solo perfil `central`)**

- Todos usan el token de la tienda (`X-Store-Token`, filtro existente) y no requieren privilegio: `EndpointPrivileges` los exime, igual que `/sync/ping`.
- `GET /snapshot/version`, `GET /snapshot?table=&afterId=&limit=` (máximo 2000), `GET /changes?sinceVersion=&limit=` (500 por defecto), `POST /ack`, `POST /heartbeat`.
- `/changes` manda, por cada entidad cambiada, su estado actual y no el historial: una entidad cambiada varias veces viaja una sola vez. Si la fila ya no existe, el cambio se manda como `DELETE`.
- Un nombre de tabla que no está en el registro responde 422 `UNKNOWN_SYNC_TABLE`.
- El heartbeat actualiza `last_heartbeat_at`, `app_version`, `pending_upload_count`, `oldest_pending_at` y `last_download_version` (solo sube). Responde la versión más reciente de la central.

**Panel de tiendas**

- `StoreNodeView` agrega `latestVersion`, `pendingChanges` (versión de la central menos `lastDownloadVersion`, nunca negativa), `connected` (latido en los últimos dos minutos) y `syncState`.
- `syncState` se calcula, no se guarda: `DISABLED` si la tienda fue revocada, `PENDING` si nunca se activó, `OUT_OF_SYNC` si no está conectada, `ACTIVE` en otro caso.

**Registrador de privilegios solo en la central**

- `EndpointPrivilegeRegistrar` pasa a `@Profile("central")`. Los privilegios y roles son datos maestros que llegan por réplica: si una tienda creara los suyos, sus ids no coincidirían con los de la central y las filas replicadas chocarían en el `code` único.
- `AdminBootstrap` y `DevActivationCodeSeeder` ya eran solo de la central. `StoreActivator` solo activa y ajusta secuencias.

**Tienda creada con la 6A**

- La 6A corrió el registrador en la tienda, así que una tienda ya activada tiene privilegios y grants con ids locales. La carga inicial borra las tablas replicadas antes de cargar, y por eso esas filas desaparecen. No hay datos de usuario creados en la tienda que se pierdan, porque en esta etapa la tienda todavía no crea documentos.

**Límites conocidos (6B; los dos primeros se atienden en el paso 7, ver allí)**

- Orden de confirmación (resuelto en el paso 7): un cambio con versión 5 puede confirmarse después de otro con versión 6. Si la tienda pide cambios después de la versión 6, no ve el 5. Lo cubriría una ventana de solape al pedir cambios (volver a pedir unas versiones antes); se deja para cuando la carga de la tienda sea crítica.
- Unicidad durante la aplicación: si en un lote se intercambian valores de una columna única (por ejemplo, el código de dos productos), la aplicación puede chocar con la unicidad aunque el resultado final sea válido. No pasa con los datos actuales.
- Crecimiento del registro (resuelto en el paso 7 con la purga diaria): `sync_change_log` solo borra filas que todas las tiendas activas ya descargaron y que tienen más de 30 días.
- Sin compresión en el cliente: el cliente de la tienda no envía `Accept-Encoding` (`SimpleClientHttpRequestFactory` no lo agrega), así que el servidor responde sin gzip aunque `server.compression` esté activo. Se agregará cuando el volumen lo justifique.

**Pendiente o no verificado**

- No se compiló: esta sesión no tiene Maven ni las dependencias. Solo se comprobó la sintaxis con `javac` (ningún error de sintaxis; los errores de símbolos son las dependencias de Spring que faltan).
- No hay pruebas, como exige la primera etapa del proyecto.
- Falta probar en la VM: el corte de la tienda 2, el cambio de precio y el regreso con `scripts/simulate-outage.sh`.

### Decisiones de implementación: paso 7 (cierre)

Alcance: datos iniciales, fecha de negocio, orden de confirmación y purga del registro de cambios, script de corte, documentación y estado de los criterios. Sin pruebas (primera etapa).

**Datos iniciales (desviación del plan)**

- Se cargan con Java, no con Flyway: `OrganizationSeeder` (orden 10) crea el país Honduras (HN, HNL, America/Tegucigalpa, RTN, HN_SAR) y la empresa "Empresa de ejemplo S. de R.L." (RTN `08019000000000`); `CatalogSeeder` (orden 20) crea las unidades UNIT, KG, LB y LT y los impuestos EXEMPT 0 %, ISV15 15 % e ISV18 18 %. Solo corren en el perfil `central`.
- Motivo: una migración escribiría en la base de cada tienda filas con ids de la central antes de la réplica, y los rangos de ids de las tiendas deben empezar limpios.
- Idempotentes: cada fila se crea solo si falta (país y empresa por ISO y por existencia de empresa; unidades y el par país-código de impuesto por código). Se crean con los servicios de cada módulo, así que se aplican sus reglas y su auditoría. El administrador inicial sigue en `AdminBootstrap` y los roles, privilegios y menús siguen en las migraciones V2 y V3, como dice el plan.
- No se crean sucursales.
- Los nombres de los datos (por ejemplo "Honduras", "Exento", "ISV 15 %") van en español: son datos de negocio, no código ni mensajes.
- Un log por seeder con la forma `Initial data loaded: ...`. Ninguno registra secretos.
- `CountryDirectory` gana `findIdByIsoCode(isoCode)` (para el id de Honduras) y `timeZoneOf(countryId)` (para la fecha de negocio).

**Fecha de negocio**

- Sin `date`, `GET /products/{id}/price` usa el día de hoy en la zona horaria del país de la lista (o, sin lista, de la sucursal). Honduras es UTC-6, así que cerca de medianoche el día UTC ya no coincide con el de la caja. Si el país no existe, se usa UTC.
- La caja puede seguir enviando `date` explícito, que tiene prioridad.

**Orden de confirmación del registro (`V10__sync_commit_order.sql`)**

- Problema: la versión de `sync_change_log` se asigna al insertar, pero la transacción puede confirmarse después. Una versión 5 de una transacción lenta puede aparecer después de una versión 6 ya confirmada; si la tienda pidió cambios desde 6, la 5 se pierde.
- Primera medida: cada fila guarda `tx_id xid8` (la transacción que la escribió) y las lecturas de la réplica solo usan filas con `tx_id < pg_snapshot_xmin(pg_current_snapshot())`, es decir, transacciones ya terminadas. La misma regla aplica a la versión de la carga inicial.
- Esa medida no basta por sí sola: la transacción de la versión 5 puede seguir abierta mientras la de la versión 6 (con un xid menor) ya confirmó y pasa el filtro. Por eso se agrega una segunda medida: el trigger toma un candado de transacción (`pg_advisory_xact_lock(4242001)`) antes de registrar el cambio. Así los cambios de datos maestros se registran de uno en uno, y las versiones se confirman en el mismo orden en que se asignan.
- Costo: dos transacciones que escriben datos maestros en orden cruzado pueden interbloquearse; PostgreSQL aborta una y el usuario la repite. Con el volumen de datos maestros del MVP es aceptable. **Decisión tomada en el cierre; pendiente de confirmar con el responsable porque cambia el diseño de sincronización.**
- Las tiendas no toman el candado (los triggers salen antes).

**Purga del registro**

- `SyncLogPurger` corre cada día a las 03:00 (`pos.sync.purge-cron`, variable `POS_SYNC_PURGE_CRON`). Solo en la central; `CentralSchedulingConfig` activa `@EnableScheduling` en ese perfil.
- Borra las filas con `version <=` el mínimo de `last_download_version` de las tiendas activas (activadas y no revocadas; un valor `NULL` cuenta como 0, así que no borra nada) **y** con `changed_at` de hace más de 30 días. Sin tiendas activas no borra nada. Registra el número de filas borradas.
- Guarda la versión máxima borrada en `sync_purge_state` (una sola fila, `purged_up_to`).
- Una tienda que pide cambios con `sinceVersion < purged_up_to` recibe `409 SNAPSHOT_REQUIRED` (código de negocio, `BusinessConflictException`). La tienda borra `snapshot_completed_at` y en el siguiente ciclo carga la copia completa.
- **Consecuencia importante:** como el mínimo incluye a toda tienda activa, una tienda activa desconectada durante semanas bloquea la purga desde su última versión. Al volver, sincroniza solo con cambios, sin copia completa. El crecimiento del registro mientras una tienda está apagada es el precio de esa regla. El `409` queda como red de seguridad; en operación normal no debería aparecer.
- Si el plan se quisiera "copia completa a toda tienda que vuelva después de la ventana", habría que purgar sin mirar a las tiendas activas, y eso rompe la garantía de que ninguna tienda pierda cambios. Se dejó la regla más segura.

**Script de corte (`scripts/simulate-outage.sh`) y reinicio (`scripts/reset.sh`)**

- La red `wan` se busca por las etiquetas de Compose (`com.docker.compose.project` y `com.docker.compose.network=wan`), no por el nombre: el filtro `_wan` anterior podía tomar la red de otro proyecto.
- El script comprueba que el contenedor esté en ejecución y que no esté ya en el estado pedido; repetir `down` o `up` no falla.
- `reset.sh` construye la imagen antes de levantar los contenedores (las tres APIs usan `medinasystems/pos:dev`, que solo construye `central-api`) y elimina contenedores huérfanos.

**Documentación**

- La colección Hoppscotch tiene 90 peticiones en 9 carpetas numeradas del 1 al 9. Se agregó "Precio de hoy (sin fecha)" en la carpeta 6, que no envía `date` y comprueba la fecha de negocio.
- El README se reordenó con una guía de inicio a fin; las referencias de la API quedaron debajo.
- El estado de los criterios está en la sub-sección "Estado de los criterios (cierre del MVP)", dentro de "Criterios de aceptación". Ninguna casilla se marca: nada se ha probado todavía.

**No cumplido o no verificado (pendiente)**

- **Criterio 10 (cambio de 10 000 precios aplicado completo):** no se cumple. Cada página de 500 cambios se aplica en su propia transacción, así que una tienda puede mostrar precios mezclados entre dos ciclos. Para cumplirlo habría que aplicar un lote completo en una sola transacción o publicar la versión solo cuando el lote entero esté listo. Es un cambio de diseño de la réplica; no se hizo en el cierre.
- **Criterio 5 en local:** las tiendas de desarrollo se crean sin sucursal, así que el filtro de acceso por sucursal no se ve con la configuración por defecto. Hay que registrar las tiendas con `branchId`.
- **Criterio 8 (tiempo de búsqueda):** no medido; no hay script de carga de 10 000 productos.
- **Pruebas:** no hay pruebas en el repositorio (primera etapa). `./mvnw verify` solo compila. La comprobación de este paso fue solo sintáctica (`javac` sin dependencias, sin errores de sintaxis).
- **Máquina virtual:** `vagrant up`, el script de corte y el reinicio no se han ejecutado en esta sesión.

### Decisiones de implementación: cambio de contraseña en la tienda

**Principio**

- Los usuarios siguen perteneciendo a la central. La contraseña es el único dato de usuario que una tienda puede cambiar; nombre, roles, privilegios y sucursales siguen siendo de solo lectura en la tienda.
- Un usuario es una sola persona y su contraseña es la misma en todos los nodos. Por eso el cambio se aplica en la tienda al momento, se encola y se sube a la central, y la central lo replica a todos los nodos.

**Flujo**

1. `POST /api/v1/me/password` funciona en la central y en la tienda (ya no tiene `@CentralOnly`). Se verifica la contraseña actual, se guarda el hash nuevo, se marca `must_change_password = false`, se fija `password_changed_at` con el reloj del nodo y se revocan los tokens de refresco del usuario. Todo en una transacción.
2. En la misma transacción se publica `PasswordChangedEvent` (paquete raíz de `security`). En una tienda, un listener de `sync` inserta una fila en `sync_outbox` con `event_type = PASSWORD_CHANGED` y el payload `{userId, passwordHash, changedAt}`. Si la transacción falla, no queda ni el cambio ni la fila.
3. El ciclo de sincronización de la tienda empieza subiendo las filas pendientes (`POST /api/v1/sync/uploads`, con `X-Store-Token`). Si la subida falla, el ciclo se detiene: no se pide ningún cambio ni se carga copia completa. Así una contraseña vieja replicada nunca pisa una nueva que la central todavía no conoce.
4. Después: latido (que informa `pendingUploadCount` y `oldestPendingAt`), copia completa si falta, y cambios. El orden es "subir antes de bajar".

**Central**

- Para cada fila se aplica la regla de última escritura gana, comparando `changedAt` con `app_user.password_changed_at` (`AppUser.applyRemotePasswordChange`). Un cambio igual o anterior no se aplica y se responde `ACCEPTED` con código `STALE`: la central ya tiene algo más reciente y la tienda lo recibirá por replicación.
- Se comprueba que el usuario esté asignado a la sucursal de la tienda (`user_branch`). Si la tienda no tiene sucursal, se rechaza con `BRANCH_NOT_ALLOWED`.
- Usuario inexistente: `REJECTED` con `USER_NOT_FOUND`. Tipo desconocido: `REJECTED` con `UNKNOWN_EVENT`. Payload incompleto o mal formado: `REJECTED` con `INVALID_PAYLOAD`.
- Un cambio aplicado revoca los tokens de refresco del usuario y registra en la bitácora `PASSWORD_CHANGED_AT_STORE` (sin hash). Guardar el usuario dispara `sync_change_log`, así que la contraseña llega a todas las tiendas por replicación.
- Cada fila se procesa en su propia transacción junto con su recibo en `sync_upload_receipt` (clave `store_node_id, outbox_id`). Si llega otra vez, se devuelve el resultado guardado, sin aplicar nada de nuevo. El recibo guarda solo ACCEPTED o REJECTED: un reenvío devuelve `ACCEPTED` sin el código `STALE`, que solo viene en la primera respuesta.

**Réplica en la tienda**

- Además del orden de ciclo, `ReplicaWriter` no sobrescribe `password_hash`, `must_change_password` ni `password_changed_at` de `app_user` cuando la fila que llega tiene un `password_changed_at` anterior al local (la cláusula `ON CONFLICT` usa `CASE` solo en esas columnas; el resto de la fila se actualiza siempre).

**Qué viaja y qué no**

- Solo viaja el hash BCrypt. Nunca la contraseña en claro. Ni el hash ni la contraseña se escriben en logs; `PasswordChangedEvent.toString()` omite el hash.

**Tablas**

- `app_user.password_changed_at` (V11): decide qué cambio gana. Todo cambio de contraseña y el alta del usuario lo fijan.
- `sync_outbox` (solo tienda): cola de envíos. Es de solo anexar, sin columnas de auditoría: cada fila se marca como respondida (`uploaded_at`, `result`, `last_error` con el código) y nunca se borra. `attempts` cuenta los intentos fallidos.
- `sync_upload_receipt` (solo central): recibos de los envíos. No están en el registro de replicación.

**Pendiente y límites**

- Un rechazo deja la contraseña de la tienda distinta de la de la central, hasta el siguiente cambio de ese usuario en la central. No se revierte la tienda: es raro (el usuario inició sesión en esa tienda, así que está asignado a su sucursal) y pide decidir la política de reconciliación.
- Las fechas de la tienda y de la central se comparan tal cual. Un reloj de tienda adelantado puede ganar a un cambio posterior hecha en la central. Con NTP en ambos nodos la diferencia es de segundos.
- Esta cola es la base de las subidas futuras (por ejemplo, las ventas): mismo `sync_outbox`, mismo endpoint `/sync/uploads` con un `eventType` nuevo, y el mismo recibo para no duplicar.

### Decisión: eventos asíncronos

Decisión del 24 de noviembre de 2026: hoy todos los listeners son síncronos y corren en la misma transacción (auditoría y cola de envíos de la tienda), por eso no se usa el registro de eventos de Spring Modulith. Cuando se agregue el primer listener asíncrono o posterior al commit, se activa antes `spring-modulith-starter-jpa` con su tabla `event_publication` (migración Flyway), para que ningún evento se pierda si el servidor se cae, y se reintenten al arrancar.
