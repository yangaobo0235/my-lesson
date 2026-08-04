# 贡献指南

## 开发环境

- JDK 17、Maven 3.9+
- Python 3.12
- Node.js 20+
- 与 `.env.example` 对应的本地或测试基础设施

真实凭据只能写入被 Git 忽略的本地配置文件。

## 分支与提交

- 从 `main` 创建短生命周期功能或修复分支。
- 一个提交只处理一个明确问题，避免混入无关格式化和重构。
- 提交信息使用清晰的动词描述结果，例如 `fix: recover stale agent runs`。
- 不提交 `target/`、`dist/`、虚拟环境、日志、测试报告、本地数据或真实密钥。

推荐使用以下提交类型：

- `feat`：新增功能
- `fix`：修复缺陷
- `refactor`：不改变行为的重构
- `test`：测试变更
- `docs`：文档变更
- `build`：构建或依赖变更
- `ci`：持续集成变更
- `chore`：其他工程维护

## 本地验证

提交前至少执行受影响模块的检查。跨模块或契约变更应执行完整检查：

```powershell
mvn clean verify

Set-Location agent-python
.\.venv\Scripts\python -m ruff check .
.\.venv\Scripts\python -m mypy src
.\.venv\Scripts\python -m pytest

Set-Location ..\frontend\ml-web
npm ci
npm test
npm run build
```

公共 Agent API 发生变化时，还必须更新并验证 `contracts/public-agent.openapi.yaml`。内部工具或知识事件发生变化时，应同步更新对应 OpenAPI 或 JSON Schema。

## 架构约束

- Java 负责身份、权限、业务规则、正式业务数据和 MySQL 事务。
- Python 负责模型、Prompt、LangGraph、RAG、对话运行态和评测。
- Python 不直接修改业务数据库；业务写入必须经过 Java 受控工具。
- Java 不新增模型调用或 Agent 编排逻辑，Relay 仅负责事件运输。
- 新增跨语言调用时优先更新契约，再分别实现调用方与提供方。

## Pull Request

Pull Request 应说明变更目的、行为影响、验证结果、配置或迁移要求。涉及界面变化时附上截图；涉及数据库变更时说明向后兼容和回滚方式。
