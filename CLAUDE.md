# Repository implementation guide

## Build and test

- Backend: `mvn clean test` and `mvn package -DskipTests` (Java 17+).
- Frontend: `cd front && npm run build`.
- Local HTTP, JWT, RPC signing and H2 transaction tests do not require infrastructure.
- MySQL/Kafka/MinIO/Nacos/Elasticsearch integration and load tests require separately started infrastructure.

## Current boundaries

Gateway is reactive Spring Cloud Gateway and only routes HTTP through Nacos discovery. Do not introduce Dubbo, business controllers, domain SQL or domain Redis writes in gateway. Business HTTP adapters authenticate independently and call local application services. Business-to-business synchronous communication uses signed Dubbo RPC; asynchronous projections use Kafka and transactional Outbox.

Business domains live under services/account, services/community, services/media and services/search. Each domain owns its API artifact and deployable application(s). Community note/comment/interaction contracts are released together as community-api. upload-api and search-api stay with their domains; leaf-api and leaf-client belong to platform/id. common-core lives under platform. The retired common compatibility aggregate is removed. Keep API artifacts free of framework dependencies.

note-service currently contains content, comment and interaction application modules in one deployment. Interaction state and counts are authoritative in MySQL. Domain projections must apply absolute counts only when versions advance. Do not revive Redis toggle + in-memory aggregation. Files and storage signatures belong to upload-service; note-service saves file references only. Confirm uploads before publishing and check authorship in the application layer.

Content and account changes append versioned public projections to separate note_outbox/auth_outbox tables in the same transaction. Search Worker consumes search_documents, uses external ES versions and permanent deletion tombstones. Canal is an isolated opt-in demo.

Leaf clients reserve batches of IDs and never invent fallback IDs. Snowflake is disabled by default pending unique-node provisioning. Leaf debug HTTP endpoints are disabled by default.

## Repository layout

- gateway stays at the edge and uses HTTP routes only.
- services contains account, community, media and search domain aggregators.
- platform contains common-core, security, messaging and ID platform modules.
- deploy contains infrastructure images/configuration; docker-compose.yml stays at root as the entrypoint.
- Run python scripts/project_layout.py --check and python -m unittest discover -s scripts/tests after structural changes.
- Select Maven modules by artifactId (for example mvn -pl :note-service -am test), never by an assumed root directory.
- Startup/configuration/migration scripts resolve service locations from the Maven reactor through scripts/project_layout.py. Do not add a parallel hardcoded path registry.
- Application code uses adapter/http, adapter/rpc, application, domain, infrastructure and configuration roles where applicable. Internal package names may change; keep published Dubbo API FQCNs stable.
- See docs/项目结构与服务边界.md for domain ownership and dependency rules.

## Migration and deployment

Read docs/微服务修复实施与部署.md before starting a changed deployment. Migration tools never run automatically against an existing database. Preserve unrelated uncommitted work. Never reset leaf allocation high-water marks, delete existing user data, or run old cleanup/init scripts against a deployed environment.
