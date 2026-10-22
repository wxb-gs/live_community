# Repository implementation guide

## Build and test

- Backend: `mvn clean test` and `mvn package -DskipTests` (Java 17+).
- Frontend: `cd front && npm run build`.
- Local HTTP, JWT, RPC signing and H2 transaction tests do not require infrastructure.
- MySQL/Kafka/MinIO/Nacos/Elasticsearch integration and load tests require separately started infrastructure.

## Current boundaries

Gateway is reactive Spring Cloud Gateway and only routes HTTP through Nacos discovery. Do not introduce Dubbo, business controllers, domain SQL or domain Redis writes in gateway. Business HTTP adapters authenticate independently and call local application services. Business-to-business synchronous communication uses signed Dubbo RPC; asynchronous projections use Kafka and transactional Outbox.

Contracts are split into common-core, note-api, comment-api, interaction-api, upload-api, search-api and leaf-api. Keep contracts free of Web starter dependencies. common is a compatibility aggregate; runtime services depend on specific contracts.

note-service currently contains content, comment and interaction application modules in one deployment. Interaction state and counts are authoritative in MySQL. Domain projections must apply absolute counts only when versions advance. Do not revive Redis toggle + in-memory aggregation. Files and storage signatures belong to upload-service; note-service saves file references only. Confirm uploads before publishing and check authorship in the application layer.

Content and account changes append versioned public projections to separate note_outbox/auth_outbox tables in the same transaction. Search Worker consumes search_documents, uses external ES versions and permanent deletion tombstones. Canal is an isolated opt-in demo.

Leaf clients reserve batches of IDs and never invent fallback IDs. Snowflake is disabled by default pending unique-node provisioning. Leaf debug HTTP endpoints are disabled by default.

## Migration and deployment

Read docs/微服务修复实施与部署.md before starting a changed deployment. Migration tools never run automatically against an existing database. Preserve unrelated uncommitted work. Never reset leaf allocation high-water marks, delete existing user data, or run old cleanup/init scripts against a deployed environment.
