# Spec: MySQL + MyBatis 存储与 String 时间/JSON 建模

Status: ready-for-agent
Slug: mysql-string-modeling
Created: 2026-10-20
决策依据: ADR-0004（MySQL+MyBatis 起步、时间/ID 全 String、JSON 列 String+便捷解析；Redis 仅预留位）；schema 唯一来源 docs/mysql/sql/llm_gateway.sql；术语见根目录 GLOSSARY.md

---

## Problem Statement

Java 版 llm-gateway 需要选定存储方案与数据建模约定：是用 MySQL 还是先上嵌入式库？时间列用 DATETIME 还是字符串？ID 用自增还是 UUID？JSON 列在 Java 实体里用类型化对象还是 String？这些决策影响 schema 设计、Mapper 写法、实体结构与查询性能，必须在写业务代码前固化，避免后期全表重构。

## Solution

存储直接用 MySQL + MyBatis 保证最小可运行，Redis 仅预留扩展位（SemanticCache 端口已设计，暂不接入）。schema 唯一来源为 `docs/mysql/sql/llm_gateway.sql`。所有时间列在 Java 实体与 PO 里统一为 String 类型（ISO-8601 带偏移格式），所有 ID 列统一为 UUID 字符串（36 字符）；JSON 列实体保持原始 String，仅通过便捷方法按需解析，PO 与实体同构免转换层。

## User Stories

1. 作为开发者，我想持久层用 MySQL + MyBatis（MyBatis-Spring-Boot-Starter），以便快速落地、SQL 可控，不引入 JPA 注解复杂度。
2. 作为开发者，我想 schema 唯一来源是 `docs/mysql/sql/llm_gateway.sql`，所有表结构变更先改这个 SQL 文件再 ALTER 库，以便 schema 有单一真相源。
3. 作为开发者，我想所有时间列（created_at / updated_at / expires_at / last_test_at 等）在 Java 实体里都是 String 类型，格式为 ISO-8601 带偏移（如 `2026-08-08T15:33:34.850534+08:00`），由 `TimeUtils.now()` 生成，以便与种子数据格式一致、字符串字典序等价时间序，BETWEEN/ORDER BY 直接命中索引。
4. 作为开发者，我想所有主键 ID 都是 UUID 字符串（36 字符，varchar(64) 富余），用 `UUID.randomUUID()` 生成，以便与 WaLiAPI 的 uuid v4 对齐、分布式下无自增冲突。
5. 作为开发者，我想 JSON 列（models / config / model_mapping / allowed_models / allowed_channels / request_body / response_choices）在实体里保持 String 原始值，提供便捷方法按需解析（如 `activeModelMapping()` 返回映射 Map），PO 与实体同构，免 PO↔实体转换层，以便减少样板代码。
6. 作为开发者，我想 fastjson 只在便捷方法被调用时做 JSON 解析，不在 MyBatis TypeHandler 里自动反序列化，以便解析时机可控、避免全局类型转换复杂度。
7. 作为开发者，Redis 仅预留 `infrastructure/cache` 目录与 SemanticCache 端口位，本期不接入，以便后续需要时平滑扩展。
8. 作为运维方，我想 created_at 上建索引，字符串格式下范围查询直接可用，以便按时间筛选日志高效。

## Implementation Decisions

- **存储选型**：MySQL 8.x + MyBatis 3 + mybatis-spring-boot-starter；连接池用 Spring Boot 默认 HikariCP。
- **schema 唯一来源**：`docs/mysql/sql/llm_gateway.sql` 为基础版（三表：channels 15 列、api_keys 11 列、request_logs 29 列）；Rust 扩展字段后续按需 ALTER 表，不直接改基础 SQL 文件。
- **时间建模**：
  - 列类型：varchar(32)，而非 DATETIME；
  - 格式：ISO-8601 带时区偏移（微秒精度），`TimeUtils.now()` 统一生成；
  - 索引：created_at 等时间列建普通索引，字符串字典序=时间序，BETWEEN 与 ORDER BY 直接命中。
- **ID 建模**：
  - 列类型 varchar(64)（36 字符 UUID 富余预留）；
  - 生成：`UUID.randomUUID().toString()`，不带横线？不，保留标准 36 字符格式。
- **JSON 列建模**：
  - 实体与 PO 中 JSON 列均为 String 类型；
  - 实体类提供便捷方法（如 `activeModels()` 解析 models 数组、`activeModelMapping()` 解析映射 Map），用 fastjson 做按需解析；
  - 不写 MyBatis TypeHandler 做自动 JSON ↔ List/Map 转换，避免复杂度。
- **Redis**：仅在 infrastructure 下预留 cache 包结构与 SemanticCache 端口接口，不引入 spring-data-redis 依赖，本期不接入。
- **PO 与实体**：同构，PO 直接就是领域实体，无额外 DTO 转换层，减少样板代码。

## Testing Decisions

- **测试接缝**：复用项目唯一接缝——@SpringBootTest 起真实容器连本地 MySQL，验证持久化行为。
- **测试位置**：统一放 app/src/test，按仓储/Case 包路径建包。
- **好测试标准**：验证外部行为——写入的数据读回正确、时间字符串可正确排序、JSON 列便捷方法返回正确结构、范围查询走时间索引；不断言 PO 字段类型等内部细节。
- **本 spec 验证点**：
  - 所有时间列写入后读回为标准 ISO-8601 字符串；
  - UUID 主键生成格式正确、全局唯一；
  - JSON 列原始字符串存储正确，便捷方法解析结果与原始值一致；
  - 按 created_at 区间筛选/排序结果正确。

## Out of Scope

- Redis 接入实现（语义缓存、分布式锁）——仅预留位，本期不做。
- 时间聚合统计（Token 趋势、时间窗 stats）——后续 stats 域再评估是否需要把 created_at 迁移为 datetime 列。
- MyBatis TypeHandler 自定义类型映射——本期明确不做，JSON 列保持 String。
- 读写分离、分库分表——单机 MySQL 足够，不做。

## Further Notes

- 字符串时间建模的取舍：换取了与种子数据格式一致、免 TypeHandler、字典序可排序；代价是后续做时间窗口聚合时可能需要转 datetime，届时是 stats 域的独立决策，不影响本期。
- JSON 列保持 String 的取舍：换取了零转换层、字节级保真、无 MyBatis 类型配置；代价是需要写便捷方法做按需解析，复杂度可控。
- 所有时间与 ID 统一 String 是项目硬性约定，新表新列必须遵守，不允许局部出现 LocalDateTime 或 Long 自增 ID。
