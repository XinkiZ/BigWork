# Wylie College 学生选课注册系统（Course Registration System）

> 2026 年软件工程课程设计 —— 小组项目
> 技术栈：Vue 3 + Vite + Pinia（前端） / Spring Boot 3.5 + Java 21 + Maven（后端） / MySQL 8.0（数据库）
> 界面规范：Windows 95/98 桌面交互规范（全中文界面）

本系统替代 Wylie 学院基于大型机技术的老旧注册系统，实现学生选课注册、教授选择授课与成绩录入、
注册管理员维护信息与关闭注册；并通过**中间层（遗留目录适配器 + 进程内缓存）**集成运行在 DEC VAX/Ingres
上的遗留课程目录数据库（本机以 `legacy_catalog` 库模拟，只读账号访问）。

---

## 1. 环境要求

| 组件 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 21（LTS） | 后端编译与运行 |
| Maven | 3.9+ | 后端构建（也可使用 IDEA 内置 Maven） |
| MySQL | 8.0.x | 本机安装、服务已启动，默认端口 3306 |
| Node.js | 18+（推荐 20/22/24） | 前端构建与运行 |

本机已验证版本：Temurin JDK 21.0.10、Maven 3.9.14、MySQL 8.0.46、Node v24.16.0。

---

## 2. 三步启动

### 第 1 步：初始化数据库（以 root 身份执行两个脚本）

```powershell
# 在项目根目录 E:\IDEA\BigWork 下执行（PowerShell 不支持 '<' 重定向，改用 source 方式）
cd E:\IDEA\BigWork
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p --default-character-set=utf8mb4 -e "source db/legacy_init.sql"
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p --default-character-set=utf8mb4 -e "source db/init.sql"
```
> 若使用 cmd.exe，也可用重定向方式：`mysql -u root -p < db\legacy_init.sql`。

或使用 MySQL Workbench / Navicat 以 root 连接后，依次打开并执行 `db/legacy_init.sql`、`db/init.sql`。

脚本作用：

- `db/legacy_init.sql`：创建**遗留课程目录库** `legacy_catalog`（模拟 DEC VAX/Ingres 库，老式字段命名、无外键），
  并创建**只读账号** `legacy_ro`（仅授予 SELECT 权限，从物理上保证新系统只读遗留数据）。
- `db/init.sql`：创建主业务库 `bigwork` 与测试库 `bigwork_test`、应用账号 `bigwork_app`、全部表结构与种子数据
  （2 个学期、16 名学生、5 名教授、1 名注册管理员、15 个课程班、上一学期成绩、满员/人数不足/未分配教授等演示场景）。
  脚本可重复执行（会先删除并重建两个库），演示前重新执行即可**恢复初始数据**。

> 脚本内默认口令（如学校要求修改，请同步修改后端配置）：
> 应用账号 `bigwork_app / bigwork123`；只读账号 `legacy_ro / legacy_ro123`。

### 第 2 步：确认后端配置

后端配置文件：`backend/src/main/resources/application.yml`。若上一步修改了口令，请同步修改：

```yaml
spring:
  datasource:
    username: bigwork_app
    password: bigwork123
legacy:
  db:
    username: legacy_ro
    password: legacy_ro123
```

其它可调参数（演示用，一般无需修改）：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `legacy.mode` | `cached` | `direct`=直连遗留库（约 8 秒）；`cached`=中间层缓存（毫秒级） |
| `legacy.available` | `true` | `false`=模拟"课程目录不可用" |
| `legacy.simulated-latency-ms` | `8000` | 模拟遗留库单次全量读取耗时（题目要求 ≤ 10 秒） |
| `legacy.cache-ttl-seconds` | `600` | 中间层缓存有效期（10 分钟） |
| `billing.available` | `true` | `false`=模拟计费系统不可用（账单进入重试队列） |
| `billing.retry-interval-seconds` | `30` | 失败重试周期（秒），由 `@Scheduled` 定时任务执行 |
| `tuition.per-credit` | `500` | 学费单价（元/学分） |

### 第 3 步：启动后端与前端

```powershell
# 3.1 启动后端（端口 8080）
cd backend
mvn spring-boot:run

# 3.2 启动前端（端口 5173，另开一个终端）
cd frontend
npm install          # 首次运行需要
npm run dev
```

浏览器访问 **http://localhost:5173**（前端已配置 `/api` 反向代理到 `http://localhost:8080`）。

后端接口自检：`http://localhost:8080/actuator/health` 应返回 `{"status":"UP"}`。

---

## 3. 演示账号

| 角色 | 账号 | 口令 | 说明 |
|------|------|------|------|
| 学生 | `2026001` | `123456` | 陈晨（已通过 CS101，可选数据结构） |
| 学生 | `2026005` | `123456` | 李思远（CS101 成绩 F，用于演示**先修校验失败**） |
| 学生 | `2026006` | `123456` | 赵雪（备选大学物理，用于演示**补位失败**） |
| 学生 | `2026010` | `123456` | 郑爽（备选高等数学 I，用于演示**补位成功**） |
| 教授 | `P001` | `123456` | 张伟（计算机学院，具备 CS101/CS102/CS201/CS202/CS301 资格） |
| 教授 | `P003` | `123456` | 王强（计算机学院，用于演示**授课时间冲突**） |
| 教授 | `P002` | `123456` | 李娜（数学学院，用于演示**无授课资格**） |
| 注册管理员 | `registrar` | `admin123` | 教务处管理员 |

学生与教授编号由系统自动生成（新增学生为 `2026xxx`，新增教授为 `Pxxx`），新增记录初始口令均为 `123456`。

---

## 4. 目录结构

```
BigWork/
├─ backend/                      Spring Boot 后端（8080）
│  ├─ src/main/java/com/wylie/registration/
│  │  ├─ domain/                 实体与枚举
│  │  ├─ repo/                   Spring Data JPA 仓库
│  │  ├─ service/                业务服务（选课引擎、关闭注册、计费、成绩…）
│  │  ├─ legacy/                 遗留课程目录适配器与中间层缓存
│  │  ├─ security/               JWT 认证与授权
│  │  ├─ web/                    REST 控制器与 DTO
│  │  └─ common/                 统一错误码与异常处理
│  ├─ src/main/resources/application.yml
│  └─ src/test/java/…            JUnit 5 + Spring Boot Test 集成测试（跑在 bigwork_test）
├─ frontend/                     Vue3 + Vite 前端（5173）
│  └─ src/
│     ├─ components/             自研 Win95 主题组件（窗口/按钮/菜单/表格/弹窗/任务栏/学期下拉）
│     ├─ views/                  三端页面（学生 / 教授 / 管理员 + 系统演示面板）
│     ├─ stores/                 Pinia 状态（登录态、全局弹窗、学期列表缓存）
│     ├─ api/                    Axios 封装与接口清单
│     └─ styles/win95.css        Win95/98 主题样式
├─ db/
│  ├─ legacy_init.sql            遗留课程目录库（只读账号）
│  └─ init.sql                   主业务库 + 测试库 + 种子数据
├─ tools/
│  ├─ perf-test.mjs              并发性能测试（50/100/200 并发，P50/P95）
│  └─ legacy-compare.mjs         直连 vs 缓存 访问耗时对比
├─ docs/                         开发计划、需求分析、设计文档、规范、接口、测试计划与报告、个人工作说明
└─ README.md
```

---

## 5. 用例演示剧本（程序运行检查建议顺序）

| 序号 | 用例 | 操作路径 | 预期结果 |
|------|------|----------|----------|
| 1 | 登录（三种角色） | 登录页依次使用学生/教授/管理员账号 | 登录成功并按角色进入对应工作台 |
| 2 | 登录失败 | 输入错误口令 | 提示"用户名或密码错误，请重新输入" |
| 3 | 课程注册（创建/暂存） | 学生 `2026001` → 课程注册 → 勾选课程 → 暂存课表 | 课表状态"暂存"，课程标记"已选择"，不占用名额 |
| 4 | 课程注册（提交） | 同一页面 → 提交注册 | 主选课程标记"已注册"，课程班人数 +1 |
| 5 | 先修校验 | 学生 `2026005` 选择"数据结构（CS102）" | 提示先修课程 CS101 未通过 |
| 6 | 满员实时通知 | 学生 `2026006` 选择"大学物理（已满 10/10）" | 提交前即提示"已满员（10/10）" |
| 7 | 时间冲突 | 学生 `2026001` 同时选择"程序设计基础"与"计算机导论"（均为周一 08:00） | 提示上课时间冲突 |
| 8 | 注册关闭拦截 | 管理员执行关闭注册后，再回学生端选课 | 提示"本学期注册已关闭，暂不能选课" |
| 9 | 课程目录不可用 | 管理员 → 系统面板 → "模拟目录不可用" → 学生端刷新目录 | 提示"课程目录系统暂时不可用"，用例终止 |
| 10 | 选择授课 | 教授 `P003` → 选择授课 → 选择"程序设计基础（周一 08:00）" | 与已选"计算机导论（周一 08:00）"时间冲突，拒绝 |
| 11 | 维护学生信息 | 管理员 → 学生信息 → 新增/修改/删除（删除需二次确认） | 新增自动生成学号；无效 ID 提示错误；取消删除不生效 |
| 12 | 维护教授信息 | 管理员 → 教授信息 → 新增/修改/删除 | 新增自动生成 Pxxx 编号 |
| 13 | 关闭注册 | 管理员 → 关闭注册 → 执行 | 取消 0 人/1 人/无教授的课程班；备选按序补位；生成学期账单 |
| 14 | 计费不可用重试 | 先"模拟计费系统宕机"，再执行关闭注册 | 账单登记成功但状态"失败待重试"；恢复后点"手动重试"或等 30 秒自动重试 → 状态"已发送" |
| 15 | 选课调整期加退选 | 关闭注册后 → 学生端 → 加选/退选 | 生成差额账单（加课补收、退课退还） |
| 16 | 提交成绩 | 教授 `P001` → 成绩录入 → 选择上一学期课程班 → 录入 A/B/C/D/F/I 或留空 | 支持补录与修改；非本人课程班提示无权限 |
| 17 | 查看成绩单 | 学生 `2026001` → 电子成绩单 | 展示上一学期成绩；无成绩学生提示"无成绩数据" |
| 18 | 越权访问 | 用学生令牌访问 `/api/registrar/students` | 返回 403 |
| 19 | 学期管理（迭代 2） | 管理员 → 学期管理 → 新建学期（如 `2027-2028-1`）→ 保存 | 新学期待"注册开放中"，可编辑、可重新开启注册 |
| 20 | 设为当前学期 | 学期管理 → 对目标学期点"设为当前" | 当前学期唯一；学生端选课、教授端选授课作用于新学期；顶栏学期实时刷新 |
| 21 | 重新开启注册 | 学期管理 → 编辑已关闭学期 → 注册状态改为"开放中" | 学生端可再次选课（建议正式关闭仍走"关闭注册"工作流） |
| 22 | 课程班管理（开班） | 管理员 → 课程班管理 → 选择学期 → 新建课程班（课程来自遗留目录） | 自动填充课程名/学分/院系，新班状态"已提出（PROPOSED）" |
| 23 | 跨学期查看（学生） | 我的课表 → 切换学期下拉；学业记录；我的账单（可筛学期） | 历史学期只读展示（无选课计划行的历史学期按已注册课程回退展示）；学业记录含学分与加权 GPA；账单仅本人可见 |
| 24 | 跨学期查看（教授） | 选课名单 / 成绩录入 → 切换学期 | 成绩录入仅列出"已开始且非当前"的学期，支持为更早学期补录与修改 |
| 25 | 只读系统面板 | 学生或教授 → 桌面"系统面板" | 可查看遗留目录/计费实时状态，演示开关仅注册管理员可操作 |

关闭注册对应的演示数据设计（种子数据已内置）：

- 课程班 4（操作系统）：0 人 → 取消；
- 课程班 6（线性代数）：仅 1 人 → 取消，学生郑爽（`2026010`）备选第 1 顺位"高等数学 I"**补位成功**；
- 课程班 10（体育 I）：未分配教授 → 取消（其选课学生的课表条目被移除，需在调整期重新加选）；
- 课程班 9（大学物理）：已满 10/10 → 赵雪、孙浩的备选**补位失败**并给出原因；
- 课程班 1（程序设计基础）与 11（计算机导论）：同为周一 08:00 → 时间冲突演示。

---

## 6. 测试与性能验证

### 6.1 后端自动化测试（JUnit 5，跑在 `bigwork_test`）

```powershell
cd backend
mvn test
```

覆盖：先修/满员/时间冲突校验、备选补位顺序、关闭注册批量逻辑、计费重试状态机、
成绩权限与取值、登录鉴权与越权防护、学生/教授信息维护；迭代 2 新增学期管理（唯一 code、日期与调整期校验、
设为当前学期）、课程班管理（课程目录校验、容量边界、教授资格与时间冲突、引用保护）、
学业记录与加权 GPA、学生账单、跨学期成绩录入口径，共 116 个用例。测试在事务中回滚，不影响演示数据。

### 6.2 性能测试（Node 内置脚本，无需额外依赖）

```powershell
node tools/perf-test.mjs                       # 课程目录接口：50/100/200 并发，输出 P50/P95 → tools/perf-result.json
node tools/perf-test.mjs --endpoint=student/schedule --out=tools/perf-result-schedule.json
node tools/perf-test.mjs --levels=10,25 --rounds=1 --out=tools/perf-result-legacy-direct.json   # 关闭中间层的对照组
node tools/legacy-compare.mjs                  # 直连(约 8s) vs 缓存(<200ms) 对比 → tools/legacy-compare-result.json
```

实测数据与 2000 并发指标的外推分析见 `docs/07-测试报告.md`。

---

## 7. 常见问题

1. **后端启动报 `Access denied for user 'bigwork_app'@'localhost'`**
   说明第 1 步脚本未执行成功，或第 2 步未同步口令。重新以 root 执行 `db/init.sql` 并核对 `application.yml`。
2. **端口被占用**
   后端 8080 或前端 5173 被占用时，可修改 `application.yml` 的 `server.port` 与 `frontend/vite.config.js` 的 `server.port`（同时修改 `proxy` 目标）。
3. **课程目录页面一直转圈（约 8 秒）**
   当前处于 `direct` 直连模式属正常现象（模拟遗留库高延迟），可在系统演示面板切回 `cached` 模式。
4. **数据库是旧版脚本初始化的（缺少第 3 个演示学期）**
   迭代 2 在 `db/init.sql` 中新增了学期 `2026-2027-2`（id=3）作为演示数据。若数据库在此之前初始化，
   可直接在 `bigwork` 与其测试库执行：

   ```sql
   INSERT INTO semester (id, code, name, start_date, end_date, registration_status, adjustment_start, adjustment_end, current_flag)
   VALUES (3, '2026-2027-2', '2026-2027学年第二学期', '2027-02-22', '2027-07-02', 'CLOSED', '2027-03-01', '2027-03-12', 0);
   ```

   或在界面上由注册管理员直接"新建学期"。全新安装无需处理。
5. **演示数据被改乱**
   重新执行 `db/init.sql` 即可恢复初始数据（会重建 `bigwork` 与 `bigwork_test`）。
6. **想重置登录状态**
   浏览器清除站点数据，或点击任务栏"开始 → 注销登录"。

---

## 8. 交付物一览

- `backend/`：Spring Boot 后端服务（含 JUnit 集成测试）
- `frontend/`：Vue3 前端（Win95/98 复古主题）
- `db/`：数据库初始化脚本（主库 + 遗留目录库）
- `tools/`：性能测试与遗留目录访问对比脚本（含 4 份实测结果 JSON：`perf-result.json`、`perf-result-schedule.json`、`perf-result-legacy-direct.json`、`legacy-compare-result.json`）
- `docs/`：开发计划、需求分析、设计文档、程序代码规范、接口文档、测试计划、测试报告（7 份，Markdown + Mermaid 图）
- `docs/成员A～F-个人工作说明.md`：6 名成员个人工作说明（含占位姓名，便于整篇替换）