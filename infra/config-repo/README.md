# config-repo

Filesystem-backed configuration source for `config-service` (Spring Cloud Config Server, `native` profile).

Each file is named `{spring.application.name}.yml` and holds environment-specific settings
(datasource URLs, Kafka/Redis hosts, API keys, JWT secret). Services bootstrap with
`spring.config.import=configserver:…` and load the matching file from here.

For local dev, start `config-service` on port 8888 before other services, or rely on
`optional:configserver:` fallbacks in each service's bootstrap `application.yml`.
