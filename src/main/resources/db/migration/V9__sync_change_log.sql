-- V9: registro de cambios para la réplica de datos maestros (paso 6B).
--
-- Ver docs/plan.md, "Sincronización" y "Decisiones de implementación: paso 6B (réplica)".
--   * sync_change_log (solo en la central) es el registro que las tiendas descargan.
--   * Los cambios se capturan con triggers de PostgreSQL: así cada cambio de un dato maestro queda
--     en el registro dentro de la misma transacción que lo modifica, como exige CLAUDE.md.
--   * El registro es de solo anexar: no tiene columnas de auditoría ni se actualiza ni se borra
--     fila a fila. Las tiendas no escriben en él.
--   * En una tienda, los triggers no hacen nada: la conexión marca pos.node_role = 'store'
--     (application-store.yml), y la aplicación de la réplica usa session_replication_role = replica.

CREATE TABLE sync_change_log (
    -- Número creciente de cambio. Es la columna "version" del plan; no se llama id, así que el ajuste
    -- de rangos de ids (IdRangeSequenceAdjuster) no la toca.
    version     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Nombre de la tabla del dato maestro (country, product, price_list_item...).
    entity      VARCHAR(40)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    operation   VARCHAR(10)  NOT NULL CHECK (operation IN ('UPSERT', 'DELETE')),
    -- Sin uso todavía: NULL significa para todas las tiendas. Se usará al filtrar por sucursal.
    branch_id   BIGINT,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX sync_change_log_entity_idx ON sync_change_log (entity, entity_id);

-- Función de los triggers. Argumentos (TG_ARGV):
--   0: nombre de la entidad que se registra (nombre de la tabla principal).
--   1: columna que contiene el id de esa entidad: "id" en tablas principales, la columna padre en hijas.
--   2: 'main' (la fila es la entidad) o 'child' (la fila pertenece a una entidad; siempre es UPSERT de ella).
CREATE OR REPLACE FUNCTION sync_log_change() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    entity_name TEXT := TG_ARGV[0];
    id_column   TEXT := TG_ARGV[1];
    kind        TEXT := TG_ARGV[2];
    row_data    JSONB;
    entity_key  BIGINT;
    op          TEXT;
BEGIN
    -- Las tiendas no registran cambios, y la aplicación de la réplica no debe generar registros nuevos.
    IF coalesce(current_setting('pos.node_role', true), '') = 'store'
       OR coalesce(current_setting('pos.sync_apply', true), '') = 'on' THEN
        RETURN NULL;
    END IF;

    IF TG_OP = 'DELETE' THEN
        row_data := to_jsonb(OLD);
    ELSE
        row_data := to_jsonb(NEW);
    END IF;

    entity_key := (row_data ->> id_column)::BIGINT;

    IF kind = 'child' THEN
        op := 'UPSERT';
    ELSIF TG_OP = 'DELETE' THEN
        op := 'DELETE';
    ELSE
        op := 'UPSERT';
    END IF;

    INSERT INTO sync_change_log (entity, entity_id, operation) VALUES (entity_name, entity_key, op);
    RETURN NULL;
END;
$$;

-- Tablas principales: cada fila es una entidad y su id está en la columna "id".
CREATE TRIGGER sync_log_country AFTER INSERT OR UPDATE OR DELETE ON country
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('country', 'id', 'main');
CREATE TRIGGER sync_log_company AFTER INSERT OR UPDATE OR DELETE ON company
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('company', 'id', 'main');
CREATE TRIGGER sync_log_role AFTER INSERT OR UPDATE OR DELETE ON role
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('role', 'id', 'main');
CREATE TRIGGER sync_log_privilege AFTER INSERT OR UPDATE OR DELETE ON privilege
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('privilege', 'id', 'main');
CREATE TRIGGER sync_log_app_user AFTER INSERT OR UPDATE OR DELETE ON app_user
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('app_user', 'id', 'main');
CREATE TRIGGER sync_log_branch AFTER INSERT OR UPDATE OR DELETE ON branch
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('branch', 'id', 'main');
CREATE TRIGGER sync_log_category AFTER INSERT OR UPDATE OR DELETE ON category
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('category', 'id', 'main');
CREATE TRIGGER sync_log_unit_of_measure AFTER INSERT OR UPDATE OR DELETE ON unit_of_measure
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('unit_of_measure', 'id', 'main');
CREATE TRIGGER sync_log_tax AFTER INSERT OR UPDATE OR DELETE ON tax
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('tax', 'id', 'main');
CREATE TRIGGER sync_log_product AFTER INSERT OR UPDATE OR DELETE ON product
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('product', 'id', 'main');
CREATE TRIGGER sync_log_product_tax AFTER INSERT OR UPDATE OR DELETE ON product_tax
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('product_tax', 'id', 'main');
CREATE TRIGGER sync_log_price_list AFTER INSERT OR UPDATE OR DELETE ON price_list
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('price_list', 'id', 'main');
CREATE TRIGGER sync_log_price_list_item AFTER INSERT OR UPDATE OR DELETE ON price_list_item
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('price_list_item', 'id', 'main');

-- Tablas hijas: pertenecen a una entidad a través de su columna padre. Un cambio en ellas registra
-- un UPSERT de la entidad padre, que se vuelve a enviar completa con todos sus hijos.
CREATE TRIGGER sync_log_user_role AFTER INSERT OR UPDATE OR DELETE ON user_role
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('app_user', 'user_id', 'child');
CREATE TRIGGER sync_log_user_branch AFTER INSERT OR UPDATE OR DELETE ON user_branch
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('app_user', 'user_id', 'child');
CREATE TRIGGER sync_log_role_privilege AFTER INSERT OR UPDATE OR DELETE ON role_privilege
    FOR EACH ROW EXECUTE FUNCTION sync_log_change('role', 'role_id', 'child');

-- Carga inicial: una fila UPSERT por cada dato maestro que ya existe, para que las tiendas que
-- todavía no han sincronizado reciban también los datos anteriores a este cambio de esquema.
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'country', id, 'UPSERT' FROM country;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'company', id, 'UPSERT' FROM company;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'role', id, 'UPSERT' FROM role;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'privilege', id, 'UPSERT' FROM privilege;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'app_user', id, 'UPSERT' FROM app_user;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'branch', id, 'UPSERT' FROM branch;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'category', id, 'UPSERT' FROM category;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'unit_of_measure', id, 'UPSERT' FROM unit_of_measure;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'tax', id, 'UPSERT' FROM tax;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'product', id, 'UPSERT' FROM product;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'product_tax', id, 'UPSERT' FROM product_tax;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'price_list', id, 'UPSERT' FROM price_list;
INSERT INTO sync_change_log (entity, entity_id, operation) SELECT 'price_list_item', id, 'UPSERT' FROM price_list_item;

-- Estado de la sincronización en la tienda (solo tiene sentido en una tienda).
ALTER TABLE store_identity ADD COLUMN last_sync_at TIMESTAMPTZ;
ALTER TABLE store_identity ADD COLUMN last_sync_error VARCHAR(500);
