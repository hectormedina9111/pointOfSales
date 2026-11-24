-- V10: orden de confirmación y purga del registro de cambios (paso 7).
--
-- Ver docs/plan.md, "Decisiones de implementación: paso 7 (cierre)".
--   * tx_id: transacción que escribió cada fila. Las lecturas de la réplica solo usan filas de
--     transacciones ya terminadas (tx_id < xmin de la instantánea).
--   * Candado de transacción en el trigger: los cambios de datos maestros se registran de uno en uno, así que
--     las versiones se confirman en el mismo orden en que se asignan. Sin el candado, una transacción con
--     versión 5 podía confirmarse después de una con versión 6, y la tienda saltaba la 5.
--   * sync_purge_state: versión hasta la que se purgó el registro (SyncLogPurger). Si una tienda pide
--     cambios anteriores, la central responde SNAPSHOT_REQUIRED.

ALTER TABLE sync_change_log ADD COLUMN tx_id xid8 NOT NULL DEFAULT pg_current_xact_id();

CREATE TABLE sync_purge_state (
    id              SMALLINT   PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    purged_up_to    BIGINT     NOT NULL DEFAULT 0
);

INSERT INTO sync_purge_state (id, purged_up_to) VALUES (1, 0);

-- Misma función que en V9, con el candado de transacción antes de registrar el cambio.
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

    -- Candado de la transacción (4242001: clave fija del registro de cambios). Se libera al confirmar.
    -- Solo lo toman los escritores de datos maestros de la central, y solo mientras escriben.
    PERFORM pg_advisory_xact_lock(4242001);

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
