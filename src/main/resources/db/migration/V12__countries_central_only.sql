-- V12: los países no se replican a las tiendas (decisión del 24 de noviembre de 2026).
--
-- Ver docs/plan.md, "Decisiones de implementación: paso 6B (réplica)".
--   * Los países solo existen en la central, donde se usan para configurar sucursales, impuestos y listas de precios.
--   * Las tiendas conservan los ids de país en sus filas replicadas (branch.country_id, tax.country_id,
--     price_list.country_id, product_tax.country_id), pero no reciben filas nuevas de country.
--   * Se quita el trigger de country y sus registros del log, para que ninguna tienda vuelva a pedir países.

-- Ya no hay trigger que registre cambios de país.
DROP TRIGGER IF EXISTS sync_log_country ON country;

-- Se borran los registros de país ya escritos en el log. Las versiones son identidades crecientes y no se
-- reutilizan, así que los cursores de las tiendas siguen siendo válidos.
DELETE FROM sync_change_log WHERE entity = 'country';

-- No se borran las filas de country que las tiendas ya recibieron: branch, tax, price_list y product_tax tienen
-- clave foránea a country, y borrar el padre dejaría esas filas huérfanas. Quedan como datos antiguos e inofensivos.
