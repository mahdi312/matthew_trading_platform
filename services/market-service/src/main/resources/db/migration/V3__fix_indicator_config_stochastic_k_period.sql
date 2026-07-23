-- Hibernate maps stochasticKPeriod -> stochastickperiod (no underscore before "k").

ALTER TABLE indicator_configs RENAME COLUMN stochastic_k_period TO stochastickperiod;
