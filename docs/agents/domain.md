# 领域文档

工程技能在探查代码库时应如何消费本仓库的领域文档。

## 探查前先读这些

- 仓库根目录的 **`GLOSSARY.md`**，或
- 根目录的 **`GLOSSARY-MAP.md`**（若存在）：它指向每个上下文各自的 `GLOSSARY.md`。读取与主题相关的每一份。
- **`docs/adr/`**：读取将要涉及的领域相关 ADR。多上下文仓库中，还应检查 `src/<context>/docs/adr/` 下上下文级别的决策。

如果这些文件不存在，**静默继续**。不要标记其缺失，也不要建议提前创建。`/domain-modeling` 技能（经 `/grill-with-docs` 和 `/improve-codebase-architecture` 触达）会在术语或决策真正落定时按需创建它们。

## 文件结构

单上下文仓库（大多数仓库）：

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

多上下文仓库（根目录存在 `GLOSSARY-MAP.md`）：

```
/
├── GLOSSARY-MAP.md
├── docs/adr/                          ← 系统级决策
└── src/
    ├── ordering/
    │   ├── GLOSSARY.md
    │   └── docs/adr/                  ← 上下文级决策
    └── billing/
        ├── GLOSSARY.md
        └── docs/adr/
```

## 使用词汇表术语

当你的输出命名领域概念（issue 标题、重构提案、假设、测试名）时，使用 `GLOSSARY.md` 中定义的术语。不要改用词汇表明确回避的同义词。

如果所需概念不在词汇表中，这是一个信号：要么你在发明项目不用的语言（重新考虑），要么存在真实缺口（记录给 `/domain-modeling`）。

## 标记 ADR 冲突

如果你的输出与既有 ADR 冲突，要明确提示而非静默覆盖：

> _与 ADR-0007（event-sourced orders）冲突，但值得重新讨论，因为……_
