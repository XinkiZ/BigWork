# 个人工作说明 —— 成员F

**姓名**：成员F（请整篇替换为实际姓名与学号）　**角色**：后端开发（管理与集成链路）
**负责范围**：登录认证与权限、学生/教授信息维护、学期与课程班管理、关闭注册工作流、计费与失败重试、遗留目录适配与缓存、系统演示开关、性能脚本
**贡献度**：16.67%（默认均分，最终以工作总结核算为准）

---

## 1 职责概述
我负责注册管理员与系统管理相关的整条链路，以及对外部系统的集成：登录认证与权限框架、
学生/教授信息维护、学期与课程班管理、关闭注册工作流（含备选补位与学费计算）、计费交易与失败重试、
遗留课程目录的适配与缓存中间层、系统演示开关与性能脚本。

## 2 具体工作项

### 2.1 登录认证与权限
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 登录认证 | `AuthService.login` | 支持学生（学号）、教授（P 编号）、管理员（用户名）三种账号；BCrypt 校验口令；失败统一提示"用户名或密码错误"（不泄露账号是否存在） |
| 令牌签发与解析 | `JwtService`、`JwtAuthFilter` | HS256，默认 720 分钟；解析后构造 `AuthUser(id, role, name, username)` 供业务层取当前身份 |
| 安全策略 | `security/SecurityConfig` | 无状态会话；登录与只读状态接口放行；其余需认证；401/403 均返回统一 JSON 错误体 |
| 角色隔离 | 控制器类级 `@PreAuthorize` + 服务层归属校验 | 学生/教授/管理员接口各自绑定角色；越权一律 403；学生接口不接收他人 ID 参数 |
| 全角色接口 | `GET /api/semesters` | 登录即可访问，供顶栏与各页面学期下拉使用（迭代 2） |

### 2.2 学生/教授信息维护
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 学生信息 | `RegistrarService` 学生部分 + `/api/registrar/students` | 新增时自动生成唯一学号（按最大学号递增）；更新/删除校验 ID 存在性；**有关联成绩记录的学生拒绝删除** |
| 教授信息 | `RegistrarService` 教授部分 + `/api/registrar/professors` | 自动生成 `Pxxx` 编号；新增的教授可直接用默认口令登录 |
| 二次确认 | 前端 `uiStore.confirm` + 后端删除接口 | 删除前必须确认，取消则不发起请求（对应用例"取消删除→状态不变"） |

### 2.3 学期与课程班管理（迭代 2）
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 学期管理 | `SemesterService`、`RegistrarSemesterController` | 新建/编辑学期、设为当前学期（单事务保证唯一）、重开注册=改回 OPEN；校验 code 唯一、名称必填、日期与调整期区间；不提供删除 |
| 课程班管理 | `OfferingAdminService`、`RegistrarOfferingController` | 开班（课程来自遗留目录）、容量 1~10（默认 10）、最少人数（默认 3）、教授资格与时间冲突校验；容量不得低于已注册人数；有引用不可删除 |
| 遗留课程供开班 | `GET /api/registrar/courses`（`LegacyCourseView`） | 复用中间层缓存；目录不可用时返回 503，开班下拉给出明确提示 |

### 2.4 关闭注册工作流
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 单事务批量流程 | `CloseRegistrationService`、`POST /api/registrar/close-registration` | 状态校验 → 取消无教授班级 → 取消人数 < 3 班级 → 备选按序补位 → 二次取消（循环至稳定）→ 计算学费并登记计费 → 学期置 CLOSED |
| 备选补位 | 依据 `item_type`/`sort_order`/`promoted`（选课链路由成员D 负责） | 主选退掉后备选按序补位；补位后再次触发取消规则，循环至稳定 |
| 执行报告 | `CloseRegistrationResult` | 取消的课程班与原因、补位成功/失败明细、被移除课表项、计费学生数与总金额 |

### 2.5 计费与失败重试
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 学费计算 | `BillingService.tuitionFor` + `TuitionProperties` | 学费 = Σ已注册课程学分 × 单价（默认 **500 元/学分**，可配置），用 `BigDecimal` 避免浮点误差 |
| 交易模型 | `billing_transaction` 表 | 金额（正数应收 / 负数退费）、`tx_type`(INITIAL / ADJUSTMENT)、`status`(PENDING/SENT/FAILED)、`retry_count`、`last_attempt_at`、`description` |
| 登记与发送解耦 | `BillingService.create` / `attemptSend` / `flushPending` | 关闭注册事务内只登记 `PENDING`，事务提交后立即尝试发送；失败仅标记 `FAILED`，**不阻塞也不回滚业务** |
| 失败自动重试 | `@Scheduled` + `retryNow` | 默认每 30 秒扫描 `PENDING/FAILED` 交易重试直至 `SENT`；逐笔独立事务；提供手动重试入口 |
| 模拟计费系统 | `BillingSystemClient` | 可用性可切换（`billing.available`），不可用时抛 `BILLING_UNAVAILABLE`（503） |
| 监控与学生账单 | `/api/registrar/billing-transactions`（含手动重试、学期筛选）、`BillingService.listForStudent` | 管理员可查看每笔交易状态、重试次数与时间；学生账单仅本人、支持按学期筛选，并为调整期加退选提供差额金额 |

### 2.6 遗留目录集成与缓存中间层
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 适配器 | `LegacyCatalogAdapter` | 模拟经 Unix 服务器开放 SQL 接口访问 VAX 遗留库：一次拉取课程 + 先修 + 资格，组装成 `CatalogSnapshot`；可配置模拟延迟（默认 8000 ms） |
| 中间层 | `CatalogService` | `cached` 模式读 Caffeine 缓存（TTL 600 s），未命中才回源；**启动预热**避免首个用户等 8 秒；提供强制刷新 |
| 模式与可用性开关 | `legacy.mode = direct / cached`、`legacy.available` | 现场即可对比"优化前/优化后"；置 false 时抛 `LegacyUnavailableException`（503 `CATALOG_UNAVAILABLE`），演示"目录不可用则终止用例" |
| 缓存状态可观测 | `CatalogService.status()` + `/api/system/status` | 输出模式、是否已缓存、课程数、快照时间与加载耗时 |

### 2.7 系统演示开关与性能脚本
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 演示开关 | `SystemController`（`/api/system/legacy-mode`、`/api/system/cache/*`、`/api/system/billing-available`） | 目录模式与可用性、缓存刷新、计费可用性；迭代 2 起前端面板对学生/教授只读 |
| 遗留对比脚本 | `tools/legacy-compare.mjs` | 依次测 cached 命中、回源刷新、direct 直连，计算加速比并写 JSON |
| 并发脚本 | `tools/perf-test.mjs` | 无第三方依赖（Node 内置 fetch），按并发梯度跑多轮，输出成功率、P50/P95、平均/最大与吞吐 |

### 2.8 测试
| 工作项 | 产出 | 说明 |
|--------|------|------|
| 认证与权限 | `AuthApiTest`（21 个） | 三种角色登录成败、未认证 401、越权 403、参数与方法误用等 |
| 信息维护 | `RegistrarServiceTest`（9 个） | 唯一 ID、CRUD、不存在 ID、有关联数据禁止删除、检索 |
| 计费与关闭注册 | `BillingServiceTest`（4 个）、`CloseRegistrationServiceTest`（6 个） | 单价计算、发送成功、失败重试状态机、空扫描幂等；补位顺序、补位失败、重复关闭 |
| 迭代 2 新增 | `SemesterServiceTest`（8）、`OfferingAdminServiceTest`（11）、`SemesterApiTest`（16） | 学期日期与唯一性、set-current、开课校验与引用保护、新接口鉴权与冒烟 |

## 3 实测证据（本人负责部分的现场数据）
| 场景 | 实测 |
|------|------|
| 计费不可用时执行关闭注册 | 16 笔交易全部 `FAILED`，`retryCount=1`，`lastAttemptAt` 正确记录；`billingSentNow=0` |
| 恢复计费后手动重试 | `sentNow=16`，交易状态汇总 `SENT=16`（等待 30 秒定时周期效果相同） |
| 学费正确性 | 16 名学生合计 **82,500 元**；与"Σ学分 × 500"逐生核对一致 |
| 遗留目录对比 | direct 直连 8052.3 ms vs cached 命中 28.1 ms，加速比 286.6 倍 |
| 并发对照 | 缓存 50 并发 360.4 QPS vs direct 10 并发 1.2 QPS |

## 4 工时投入
| 工作内容 | 占比 |
|----------|-----:|
| 登录认证与权限框架 | 15% |
| 信息维护与学期/课程班管理 | 15% |
| 关闭注册与备选补位 | 20% |
| 计费与失败重试 | 20% |
| 遗留集成、缓存与演示开关 | 20% |
| 性能脚本与测试 | 10% |

## 5 收获与不足
**收获**：
- "认证（你是谁）"和"授权（你能做什么）"必须分层实现：JWT 解决前者，方法级注解 + 服务层归属校验解决后者；
- 外部系统不可靠是常态：把登记与发送解耦、让失败可重试，业务侧就不会因为对方宕机而失败或长时间卡住；
- 缓存不是"加了就快"：TTL、预热、刷新策略、未命中降级都要设计，否则第一个用户就是受害者；
- 关闭注册的难点不在"删数据"，而在**顺序与收敛**，且整个过程必须原子。

**不足**：
- 最初把缓存放在 Adapter 内部，导致 direct/cached 无法切换；重构为 `CatalogService` 中间层后才达成演示目标；
- 计费重试最初想在请求线程里循环等待，会导致接口长时间挂起，改为"登记 + 后台重试"后才合理；
- 重试周期早期写死在代码里（30 秒），后来改为配置项，否则自动化测试会被定时任务干扰。

## 6 迭代 2 补充工作（V2.0 多学期能力，2026-10-11）

初版交付后评审确认系统把"学期"写死为单学期。本轮迭代在**零 DDL 变更**的前提下补齐多学期能力，
本人在本轮迭代中承担的工作如下：

| 工作项 | 产出 | 说明 |
|--------|------|------|
| 学期管理服务 | `SemesterService`、`RegistrarSemesterController` | 新建/编辑学期、设为当前学期（单事务保证唯一）、重开注册=改回 OPEN；校验代码唯一与日期/调整期区间 |
| 课程班管理服务 | `OfferingAdminService`、`RegistrarOfferingController` | 开课（课程来自遗留目录）、容量 1~10、最少人数、教授资格与时间冲突、引用保护删除 |
| 全角色学期接口 | `GET /api/semesters`（`web/SemesterController`） | 登录即可访问；扩展新接口 401/403 用例（AuthApiTest 新增 5 例、SemesterApiTest 鉴权断言） |
| 遗留课程供开班 | `GET /api/registrar/courses`（`LegacyCourseView`） | 复用中间层缓存；目录不可用时返回 503，开班下拉给出提示 |
| 学生账单查询 | `BillingService.listForStudent` | 仅本人交易、按学期筛选；调整期差额金额由本服务计算 |
| 计费监控学期筛选 | `GET /api/registrar/billing-transactions?semesterId=` | 支持"全部 / 按学期"查看交易与重试状态 |