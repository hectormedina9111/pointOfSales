-- V25: configuración de serie del producto (NONE, MANUAL, LIST). Ver docs/plan.md, "Tres configuraciones de serie del
-- producto" y "Decisiones de implementación: inventario paso 11 (configuraciones de serie)".
--   * serial_mode reemplaza a tracks_serials. Los productos que tenían tracks_serials = TRUE pasan a LIST, que es el
--     comportamiento que ya tenían.
--   * Las reglas CHECK que usaban tracks_serials se recrean con serial_mode. La regla de unidad sin decimales sigue en la
--     aplicación, como antes.
--   * product es un dato maestro replicado: la réplica lee las columnas de information_schema y el JSON sale de
--     row_to_json, así que quitar la columna no requiere cambios en la sincronización.

ALTER TABLE product
    ADD COLUMN serial_mode VARCHAR(10) NOT NULL DEFAULT 'NONE'
        CHECK (serial_mode IN ('NONE', 'MANUAL', 'LIST'));

UPDATE product SET serial_mode = 'LIST' WHERE tracks_serials;

-- Las reglas anteriores se quitan antes de quitar la columna y se vuelven a crear con serial_mode.
ALTER TABLE product DROP CONSTRAINT product_service_rules_ck;
ALTER TABLE product DROP CONSTRAINT product_serials_inventory_ck;

-- Un servicio nunca mueve inventario ni lleva series.
ALTER TABLE product ADD CONSTRAINT product_service_rules_ck CHECK (
    product_type <> 'SERVICE' OR (tracks_inventory = FALSE AND serial_mode = 'NONE'));
-- Cualquier configuración de serie exige inventario.
ALTER TABLE product ADD CONSTRAINT product_serials_inventory_ck CHECK (
    serial_mode = 'NONE' OR tracks_inventory = TRUE);

ALTER TABLE product DROP COLUMN tracks_serials;
