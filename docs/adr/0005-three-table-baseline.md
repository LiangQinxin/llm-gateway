# ADR-0005: 三表基础实现范围与测试策略

首批实现 channels/api_keys/request_logs 的完整基础实现（用户明确范围），管理面命令分组（原 Q5）暂缓；测试统一 @SpringBootTest 起真实容器跑整链路，放 app/src/test 按被测包路径建包。

## Decision

- **用例范围**：
  - channel 域：list/get/create/update/toggle/delete + `findEnabledForRoute`（仅 status=1，priority DESC, weight DESC，供 proxy 阶段路由）+ `testChannel` 手动连通测试（legacy 单协议语义：type 决定协议、base_url 即上游地址；探测矩阵 openai/custom → `GET {base_url}/models`(Bearer)、anthropic → `GET {base_url}/v1/models`(x-api-key+anthropic-version)，2xx 即通，写 last_test_at/last_test_ok）；
  - identity 域：list/get/create/update/toggle/delete + `authenticate`（`key=? AND status=1 AND (expires_at IS NULL OR expires_at>now)` + allowed_models/allowed_channels 白名单，空数组=全放行）+ `recordUsage` 配额递增封顶（quota_limit=0 不限；否则 SQL `LEAST(quota_used + ?, quota_limit)` 原子封顶）；
  - audit 域：pageLogs（按 model/api_key/channel/status_code/created_at 区间筛选分页）+ getLogDetail（含 request_body/response_choices）+ deleteLog + deleteBefore（created_at 之前批量清理）+ clearAll（一键清空）；seq 自增保证顺序，只增不改；
- **出站端口**：连通测试走 `domain/channel/adapter/port/IChannelProbePort`，`infrastructure/adapter/port/channel/OkHttpChannelProbe` 实现（okhttp 不进 domain）；
- **管理面路径**：`/admin/api/**`（channels/keys/logs 三个 Controller），基础阶段不加鉴权（Q6-a）；
- **测试策略**：`@SpringBootTest` 起真实容器连本地 MySQL 跑整链路（create→update→query→delete），测试统一放 `app/src/test` 按被测包路径建包（`cn.qx.cases.*`），不做手工装配/不过度 mock，入参出参 log+JSON 打印；**app pom 的 surefire `<skipTests>` 需改为 false**（当前为 true 会导致测试不执行）；
- **时间/ID/JSON 建模**：遵循 ADR-0004（String ISO-8601 + UUID + String 便捷解析）。

## Consequences

proxy 主链路阶段直接复用 findEnabledForRoute / authenticate / recordUsage；三表基础实现的 PO/仓储/Mapper 将成为后续各域 repository 的样板。
