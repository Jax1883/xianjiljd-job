# 西安吉利京东工具（xianjilijd-job）接口实现设计

- 日期：2026-05-02
- 项目目录：`D:\project\tools\xianjilijd-job`
- 数据库：`artemis`（MySQL，172.16.65.69:3306）
- 服务端口：`8380`

## 1. 背景

针对 `out_order_back`（出库单回传）和 `receipt_note_back`（收货单回传）两张表，提供两个 HTTP 接口用于：

- 查询某个出库/入库单的同步状态
- 把指定 id 的回传记录"激活"——把 `is_sync` 和 `sync_times` 重置为 0，让下游同步任务重新拉取处理

服务在本地运行，使用 Postman 调用。

## 2. 技术栈

- Spring Boot 2.7.x
- JDK 21（Spring Boot 2.7 在 JDK 17+ 已被验证可运行；本机已装 21）
- Maven 3.5+
- MyBatis Plus 3.5.x
- MySQL Connector 8.x
- Lombok（简化实体类）
- Jakarta Validation（入参校验）
- HikariCP（Spring Boot 默认连接池）

## 3. 项目目录结构

```
xianjilijd-job/
├── pom.xml
├── README.md
├── 接口设计文档.txt                          // 原需求文档
├── docs/superpowers/specs/
│   └── 2026-05-02-xianjilijd-job-design.md  // 本设计文档
└── src/main/
    ├── java/com/xianjilijd/job/
    │   ├── XianjilijdJobApplication.java    // 启动类
    │   ├── controller/
    │   │   └── OrderBackController.java
    │   ├── service/
    │   │   ├── OrderBackService.java
    │   │   └── impl/OrderBackServiceImpl.java
    │   ├── mapper/
    │   │   ├── OutOrderBackMapper.java
    │   │   └── ReceiptNoteBackMapper.java
    │   ├── entity/
    │   │   ├── OutOrderBack.java
    │   │   └── ReceiptNoteBack.java
    │   ├── dto/
    │   │   ├── QueryOrderBackReq.java
    │   │   ├── QueryOrderBackResp.java
    │   │   └── ActivateOrderBackReq.java
    │   ├── common/
    │   │   ├── ApiResponse.java
    │   │   ├── OrderTypeEnum.java
    │   │   └── BizException.java
    │   └── exception/
    │       └── GlobalExceptionHandler.java
    └── resources/
        └── application.yml
```

## 4. 接口契约

两个接口均使用 `POST` + `application/json` 请求。

### 4.1 接口 1：查询回传状态

- 方法：`POST`
- 路径：`/tools/xianjilijd/queryOrderBack`
- Content-Type：`application/json`

请求体（两种 `code` 格式都支持）：

```json
{ "orderType": "出库单回传", "code": "HKCK6105230603000002,HKCK6105230603000003" }
```

或：

```json
{ "orderType": "出库单回传", "code": ["HKCK6105230603000002", "HKCK6105230603000003"] }
```

字段说明：

- `orderType`（必填，字符串）：枚举值 `入库单回传` 或 `出库单回传`
- `code`（必填）：单号；可以是英文逗号分隔的字符串，也可以是字符串数组

通过 Jackson 自定义反序列化把字符串和数组都规整成 `List<String>`。

成功响应（查到数据）：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 12345,
      "responseCode": "HKCK6105230603000002",
      "isSync": 0,
      "syncTimes": 0,
      "lastModifyDate": "2026-04-30 10:11:22",
      "orderType": "出库单回传"
    }
  ]
}
```

成功响应（查不到数据）：

```json
{
  "code": 200,
  "message": "无数据，请人工核实",
  "data": []
}
```

字段映射规则：

- 入库单回传查 `receipt_note_back`，把 `receipt_code` 映射为 `responseCode`
- 出库单回传查 `out_order_back`，把 `out_order_code` 映射为 `responseCode`
- 其他字段按文档要求驼峰命名：`id`、`isSync`、`syncTimes`、`lastModifyDate`
- `lastModifyDate` 输出格式：`yyyy-MM-dd HH:mm:ss`
- `orderType` 原样回传调用方传入的值

排序：按 `id DESC`。

### 4.2 接口 2：激活回传

- 方法：`POST`
- 路径：`/tools/xianjilijd/activateOrderBack`
- Content-Type：`application/json`

请求体（两种格式都支持）：

```json
{ "orderType": "入库单回传", "requestIds": "1,2,3,4,5" }
```

或：

```json
{ "orderType": "入库单回传", "requestIds": [1, 2, 3, 4, 5] }
```

通过 Jackson 自定义反序列化，把 `String` 和 `List<Long>` 都规整成 `List<Long>`。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": { "updated": 3 }
}
```

`updated` 是 SQL 实际更新的行数。

业务行为：

- 入库单回传 → UPDATE `receipt_note_back` SET `is_sync`=0, `sync_times`=0 WHERE `id` IN (...)
- 出库单回传 → UPDATE `out_order_back` SET `is_sync`=0, `sync_times`=0 WHERE `id` IN (...)
- 仅修改这两个字段，不动其他字段（用 MyBatis Plus 的 `LambdaUpdateWrapper.set()` 精确控制 SET 列）
- 整个批量更新在一个事务里完成

## 5. 统一响应格式

```java
class ApiResponse<T> {
    int code;          // 200 成功；400 入参错误；500 服务端错误
    String message;
    T data;
}
```

## 6. 入参校验

### 6.1 通用规则

- 全部使用 MyBatis Plus 参数绑定（`#{}`），杜绝字符串拼接 SQL → 防止 SQL 注入
- `orderType` 白名单校验：只能是 `入库单回传` 或 `出库单回传`，否则返回 400
- 字符串参数全部 trim，去掉前后空白和可能误传的引号 `"`、`'`、`""`、`""`

### 6.2 接口 1 特有

- `code` 拆分后逐个 trim、去空、去引号
- 拆分后空集合直接返回 400
- 单次最多 500 个 code，超限 400

### 6.3 接口 2 特有

- `requestIds` 解析后必须全部能转成 `Long`，否则 400
- 单次最多 1000 个 id，超限 400
- 解析后空集合直接返回 400

## 7. 数据库配置

### 7.1 application.yml

```yaml
server:
  port: 8380
  servlet:
    context-path: /

spring:
  application:
    name: xianjilijd-job
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${DB_HOST:172.16.65.69}:${DB_PORT:3306}/${DB_NAME:artemis}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:osr+T4MECNMN}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 5000
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 控制台打印 SQL
  global-config:
    db-config:
      id-type: auto

logging:
  level:
    com.xianjilijd.job: INFO
```

支持环境变量覆盖：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`。

### 7.2 实体类映射

`OutOrderBack` 和 `ReceiptNoteBack` 使用 MyBatis Plus `@TableName` 和 `@TableField` 注解，
表里只映射本次需要用到的字段（id、code、is_sync、sync_times、last_modify_date），
其他字段不映射，避免大表全字段拉取。

## 8. 安全与并发

- 数据库密码做成可配置（环境变量优先）
- 仅暴露两个明确接口；不开放 actuator 等管理端点
- 仅 UPDATE `is_sync` 和 `sync_times` 两列，业务上不会跟其他写入冲突，依赖 MySQL 行锁即可
- 批量 UPDATE 单条 SQL 完成，落入一个事务（`@Transactional`），事务边界在 service 层
- 全局异常处理器兜底：业务异常返回 400，系统异常返回 500，不暴露堆栈给调用方

## 9. 错误码与异常

- 200：成功
- 400：入参错误（orderType 非法、id 非数字、超出批量上限、空集合等）
- 500：服务端错误（数据库连不上、未捕获异常）

通过 `@ControllerAdvice` 全局异常处理：

- `BizException` → 400 + 自定义 message
- `MethodArgumentNotValidException`、`HttpMessageNotReadableException` → 400 + 解析后的 message
- 其他 `Exception` → 500 + 简化 message（堆栈写入服务端日志）

## 10. 测试与验证

### 10.1 启动方式

```
mvn spring-boot:run
# 或
mvn clean package
java -jar target/xianjilijd-job-1.0.0.jar
```

### 10.2 Postman 验证用例

- 接口 1，单 code 命中
- 接口 1，多 code（字符串逗号分隔）部分命中
- 接口 1，多 code（JSON 数组）部分命中
- 接口 1，code 不存在 → 返回"无数据，请人工核实"
- 接口 1，orderType 非法 → 400
- 接口 2，单 id 激活
- 接口 2，多 id（字符串）激活
- 接口 2，多 id（数组）激活
- 接口 2，id 不存在 → updated:0
- 接口 2，orderType 非法 → 400

### 10.3 单元测试

本期不写自动化测试（工具类项目，以 Postman 手动验证为准）。
后续如需扩展可加 `MockMvc` 集成测试。

## 11. README 内容

- 项目简介
- 启动步骤（含数据库连通性检查）
- 环境变量说明
- 两个接口的 Postman 调用示例（含 cURL）
- 端口、context-path 修改方式

## 12. 不在本期范围

- 鉴权（接口暂不加 token / 网关）
- 限流
- 自动化测试套件
- 容器化（Dockerfile）
- 接口 1 的字段是否输出更多原表字段（需求只要求 5 个固定字段）
