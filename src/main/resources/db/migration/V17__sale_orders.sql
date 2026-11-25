-- V17: orden y cobro separados (paso 11 del MVP 3A).
--
-- Ver docs/plan.md, "Orden y cobro" y "Decisiones de implementación: 3A paso 11". Desviaciones:
--   * sale.customer_id apunta a customer (V16). Las ventas viejas quedan con el consumidor final (id 1).
--   * order_type solo admite CASH en 3A; CREDIT se rechaza en la aplicación hasta el módulo de crédito (fase 7).
--   * sale_line.list_price se agrega anulable, se rellena con el precio unitario vigente y después queda NOT NULL.
--   * price_authorized_by guarda quién autorizó un cambio de precio. discount_authorized_by sigue para los descuentos.

ALTER TABLE sale ADD COLUMN customer_id BIGINT NOT NULL DEFAULT 1 REFERENCES customer (id);
CREATE INDEX sale_customer_idx ON sale (customer_id);

-- CASH (contado) o CREDIT (crédito, aún sin uso).
ALTER TABLE sale ADD COLUMN order_type VARCHAR(10) NOT NULL DEFAULT 'CASH';
ALTER TABLE sale ADD CONSTRAINT sale_order_type_ck CHECK (order_type IN ('CASH', 'CREDIT'));

-- Quién autorizó un cambio de precio mayor a cero en alguna línea (el propio cajero si tiene el privilegio).
ALTER TABLE sale ADD COLUMN price_authorized_by BIGINT REFERENCES app_user (id);

-- Precio de lista original de la línea. Las líneas existentes no tenían cambios de precio.
ALTER TABLE sale_line ADD COLUMN list_price NUMERIC(14,2);
UPDATE sale_line SET list_price = unit_price;
ALTER TABLE sale_line ALTER COLUMN list_price SET NOT NULL;

-- true cuando el precio de la línea es distinto del precio de lista.
ALTER TABLE sale_line ADD COLUMN price_overridden BOOLEAN NOT NULL DEFAULT FALSE;
