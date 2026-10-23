# ADR-0004: MySQL + MyBatis 起步与 String 时间/JSON 建模

存储先 MySQL + MyBatis 保证最小运行，Redis 仅作扩展设计；时间/ID 全 String（ISO-8601 + UUID），JSON 列实体保持 String + 便捷解析方法。

## Decision

- **存储**：MySQL + MyBatis（D4）；Redis 仅预留 `infrastructure/cache` 与 `redis` 实现位（SemanticCache 端口已设计，暂不接入）；
- **schema 唯一来源**：`docs/mysql/sql/llm_gateway.sql`（基础版三表 15/11/29 列）。
- **时间字段全 String**：ISO-8601 带偏移（`2026-08-08T15:33:34.850534+08:00`），工具类 `TimeUtils.now()` 生成；列是 varchar(32) 而非 DATETIME，且统一格式下字符串字典序=时间序，`idx_created_at` 与 `ORDER BY`/`BETWEEN` 直接可用，与 SQL 种子数据格式一致；
- **ID 全 String**：`UUID.randomUUID()`（36 字符，varchar(64) 富余）；
- **JSON 列建模**（models/config/model_mapping/allowed_models/allowed_channels/request_body/response_choices）：实体保持 String + 便捷解析方法（如 `Channel.activeModelMapping()` 对齐 Rust `active_model_mapping`）；持久化对象独立为 PO（`cn.qx.infrastructure.dao.po`，类名 `<表名>PO`，字段与库表列一一对应），repository 适配器负责实体 ↔ PO 转换（`BeanUtils.copyProperties`，自增键回填需同步回写实体），fastjson 解析仅发生在调用便捷方法时。

## Considered Options

- LocalDateTime + MyBatis TypeHandler：类型安全但 varchar 列无原生映射、格式不可控，且与种子数据（带偏移/纳秒）不一致，复杂度更高；
- JSON 列实体类型化（List/Map）：需要 PO↔实体两层结构映射，基础阶段收益为负。

## Consequences

时间聚合类需求（stats 阶段的 Token 趋势/时间窗）如在 varchar 上不便，届时评估将 `created_at` 迁移为 datetime 列并换 TypeHandler——那是 stats 域决策，非本期。
