# 基于检索增强生成的智能教务系统

毕业设计项目。把教务业务（课表、选课、成绩、学籍档案）和规章问答放在同一个系统里，
问答部分用 RAG，并且对"能不能信"这件事给了硬约束。

## 这个系统解决什么

学生的规章问题长在场景里（"重修要交钱吗""挂了科还能不能毕业"），手册却按条款组织；
直接问大模型会编数字、会越权、说了也没有出处。所以这里的问答有三条铁律，
每条都有代码落点，不是设计口号：

| 铁律 | 落点 |
| --- | --- |
| 数值不生成 | 学分绩点由代码算（`GradePointCalculator`）；模型回答里的每个数字必须在原文找得到（`NumberGroundingChecker`） |
| 先鉴权后检索 | 权限过滤发生在召回之前（`RetrievalService.allowedDocuments()`），无权内容进不了候选集 |
| 无依据不回答 | 拒答看"相关度分数 + 领域词"两个条件（实测单看分数分不开） |

问明白之后还要能查、能办事，所以系统里有一条完整链路：

**规章问答 → 查课表/考试/成绩 → 办事申请 → 系统预检 → 教务审批 → 全过程留痕**

免听/间听、重新修读、转专业、证明打印四类事项走同一套申请单。
预检只做代码能判准的部分（这门课是否真的冲突、有没有未通过记录、绩点是否达标），
判不了的写进单据备注交给教务，引用的是与问答同一批条款。

考试安排、课表、学分绩点这些"系统里有的实时数据"同样不让模型凭印象说：
问"我什么时候考试""我这学期选了几门课"，走的是数据库查询，模型不参与生成。

数据不是一条条填的。课程库、学生名册、教师名册、**培养方案**都能按模板批量导入
（Excel 或 CSV，先校验后入库，有错不入库，按业务键幂等），
导入培养方案之后，"我还差多少学分能毕业"这句就能按现行方案逐模块算出缺口。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.5、MyBatis-Plus 3.5、Lombok |
| 数据库 | MySQL 8+（中文全文检索用 ngram 分词 + 自然语言模式） |
| 前端 | Vue 3、Vite、TypeScript、Pinia、vue-router，不用组件库 |
| 生成模型 | OpenAI 兼容接口（当前接 DeepSeek）；不可用时自动降级为原文摘录 |

## 目录

```
backend/    Spring Boot 服务（111 个类：common/auth/system/student/teacher/course/
            teaching/enrollment/grade/schedule/exam/application/program/importer/
            knowledge/assistant/governance）
frontend/   Vue 3 前端（24 个页面 + 12 个自建组件）
sql/        schema.sql → seed.sql → knowledge.sql → governance.sql（按序执行）
eval/       问答评测集与最近一次评测报告
scripts/    浏览器验收、问答评测、截图脚本
docs/       PRD
```

## 快速开始

### 1. 数据库

```powershell
cd sql
mysql -u root -p --default-character-set=utf8mb4 < schema.sql
mysql -u root -p --default-character-set=utf8mb4 < seed.sql
mysql -u root -p --default-character-set=utf8mb4 < knowledge.sql
mysql -u root -p --default-character-set=utf8mb4 < governance.sql
```

重跑脚本会把知识库清空，**之后要重启后端**：知识库由启动流程按语料自动灌入
（约 179 片切片），只重置数据库不重启，问答会一直答"知识库里没有相关条款"。

库连接与演示账号在 `backend/src/main/resources/application.yml`。
本地密钥（模型 API Key）放 `backend/config.local.yml`，该文件已 gitignore：

```yaml
iaas:
  assistant:
    llm:
      provider: deepseek
      openai:
        api-key: sk-...
```

### 2. 后端

```powershell
$env:JAVA_HOME='D:\APP\Code\JDK\JDK21'
cd backend
mvn -B clean package -DskipTests
java -jar target\iaas-backend-1.0.0.jar     # 工作目录必须是 backend，否则读不到 config.local.yml
```

启动时会自动把学生手册灌入知识库（首次约 179 片切片），已有生效文档则跳过。

### 3. 前端

```powershell
cd frontend
npm install
npm run dev        # http://127.0.0.1:5173，/api 由 Vite 代理到 8080
```

### 演示账号（口令统一 `123456`）

| 账号 | 角色 | 看点 |
| --- | --- | --- |
| `2022001` | 学生（李思远） | 课表、冲突封条、满员封条、问答、反馈 |
| `2021001` | 学生（陈子豪） | 课表上有一处已存在的冲突（免听批准后录入的历史数据） |
| `2021002` 及 `2021003` `2022002`–`2022005` | 学生 | 学号即账号，八个学生都能登录 |
| `t1001` / `t1002` | 教师（张伟 / 王芳） | 我的教学班与名单 |
| `jw001` | 教务人员 | 档案、开课、申请审批、知识库治理、反馈与缺口 |
| `admin` | 管理员 | 账号管理 |

## 验证与评测

```powershell
cd backend;  mvn test                    # 29 条单元测试
cd frontend; npm run build               # 类型检查 + 打包
node scripts/verify.mjs                  # 58 项浏览器验收（需要前后端都起着）
node scripts/eval-assistant.mjs          # 29 条问答评测
```

最近一次结果：单测 29/29、浏览器验收 58/58、问答评测 29/29（p50 686ms，p95 1436ms）。
截图与明细在 `.impeccable/review/`，评测明细在 `eval/report.json`。

## 文档

- [PRD：基于检索增强生成的智能教务系统 v1.0](docs/PRD-智能教务系统-v1.0.md)
- [界面设计说明](DESIGN.md)

## 说明

系统内所有师生信息、成绩、选课记录均为合成演示数据，不含任何真实个人信息，
不能作为教务办事依据。
