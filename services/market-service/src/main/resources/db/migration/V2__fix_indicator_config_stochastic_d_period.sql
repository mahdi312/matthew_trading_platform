-- Hibernate maps stochasticDPeriod -> stochasticdperiod (no underscore before "d").
-- V1 used stochastic_d_period; rename for databases that already applied V1.

ALTER TABLE indicator_configs RENAME COLUMN stochastic_d_period TO stochasticdperiod;
