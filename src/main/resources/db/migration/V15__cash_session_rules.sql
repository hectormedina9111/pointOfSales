-- V15: reglas de sesiones de caja y prefijos de recibo (paso 4 del MVP 3A).
--
-- Ver docs/plan.md, "Decisiones de implementación: 3A paso 4".
--   * Se quita la regla de una sola sesión abierta por cajero en la base de datos (índice cash_session_open_user_uk,
--     creado en V13). Un cajero asignado a dos sucursales puede tener una sesión abierta en cada tienda, y la central
--     guarda las sesiones de todas. La regla se mantiene en la aplicación de cada tienda: CashSessionService consulta
--     si el cajero ya tiene una sesión abierta antes de abrir otra (USER_HAS_OPEN_SESSION).
--   * Se mantiene la regla de una sola sesión abierta por caja (cash_session_open_register_uk): cada caja pertenece a una
--     sola sucursal y, por tanto, a una sola tienda.
--   * receipt_prefix es único entre todas las cajas: dos cajas con el mismo prefijo darían recibos con el mismo número.
--     ATENCIÓN: una base local con prefijos repetidos debe corregirlos antes de migrar; si no, esta migración falla.

DROP INDEX cash_session_open_user_uk;

ALTER TABLE cash_register ADD CONSTRAINT cash_register_receipt_prefix_uk UNIQUE (receipt_prefix);
