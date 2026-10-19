# 问题跟踪器：本地 Markdown

本仓库的问题与规格以 markdown 文件形式存放在 `.scratch/` 下。

## 约定

- 每个功能一个目录：`.scratch/<feature-slug>/`
- 规格文件为 `.scratch/<feature-slug>/spec.md`
- 实现问题按"一个 ticket 一个文件"管理：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 开始编号，不要合并成一个 tickets 文件
- Triage 状态记录在 issue 文件靠上的 `Status:` 行（角色字符串见 `triage-labels.md`）
- 评论与对话历史追加到文件末尾的 `## Comments` 标题下

## 当技能要求"发布到问题跟踪器"

在 `.scratch/<feature-slug>/` 下新建文件（目录不存在则先创建）。

## 当技能要求"获取相关 ticket"

读取所引用路径下的文件。用户通常直接传路径或 issue 编号。

## 探路操作

供 `/wayfinder` 使用。**map** 是一个文件，每个 ticket 对应一个**子**文件。

- **Map**：`.scratch/<effort>/map.md`（Notes / Decisions-so-far / Fog 正文）。
- **子 ticket**：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 开始编号，问题写在正文。`Type:` 行记录 ticket 类型（`research`/`prototype`/`grilling`/`task`）；`Status:` 行记录 `claimed`/`resolved`。
- **阻塞**：靠近文件顶部用 `Blocked by: NN, NN` 行记录。当列出的每个文件都变为 `resolved` 时，该 ticket 解除阻塞。
- **前沿（Frontier）**：扫描 `.scratch/<effort>/issues/` 中打开、未阻塞且未认领的文件；按编号从小到大取第一个。
- **认领（Claim）**：开始工作前将 `Status: claimed` 保存。
- **解决（Resolve）**：在 `## Answer` 标题下追加答案，将 `Status` 改为 `resolved`，然后在 map 的 Decisions-so-far 中追加指向该解答的上下文指针（gist + 链接）。
