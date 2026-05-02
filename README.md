# 西安吉利京东工具（xianjilijd-job）

针对 `out_order_back`（出库单回传）和 `receipt_note_back`（收货单回传）两张表，提供两个本地 HTTP 接口：查询同步状态、把 `is_sync` 与 `sync_times` 重置为 0。

## 启动方式

需要 JDK 21（或 17+）与 Maven 3.5+。默认连接的数据库是 `172.16.65.69:3306/artemis`，账号 `root`。

### 直接运行

    mvn -DskipTests spring-boot:run

### 打包后运行

    mvn -DskipTests clean package
    java -jar target/xianjilijd-job-1.0.0.jar

启动成功后监听 `http://127.0.0.1:8380`。

## 环境变量（可选）

- `DB_HOST`（默认 `172.16.65.69`）
- `DB_PORT`（默认 `3306`）
- `DB_NAME`（默认 `artemis`）
- `DB_USER`（默认 `root`）
- `DB_PASSWORD`（默认与 spec 一致）

例（cmd）：

    set DB_HOST=192.168.1.10
    java -jar target/xianjilijd-job-1.0.0.jar

例（PowerShell）：

    $env:DB_HOST = "192.168.1.10"
    java -jar target/xianjilijd-job-1.0.0.jar

## 接口

### 1. 查询回传状态

    POST http://127.0.0.1:8380/tools/xianjilijd/queryOrderBack
    Content-Type: application/json

请求体（两种 `code` 写法都可以）：

    { "orderType": "出库单回传", "code": "HKCK6105230603000002,HKCK6105230603000003" }
    { "orderType": "出库单回传", "code": ["HKCK6105230603000002","HKCK6105230603000003"] }

响应：

    { "code":200, "message":"success",
      "data":[{"id":1,"responseCode":"HKCK...","isSync":0,"syncTimes":0,"lastModifyDate":"2026-04-30 10:11:22","orderType":"出库单回传"}] }

查不到：

    { "code":200, "message":"无数据，请人工核实", "data":[] }

### 2. 激活回传（重置 is_sync / sync_times）

    POST http://127.0.0.1:8380/tools/xianjilijd/activateOrderBack
    Content-Type: application/json

请求体（两种 `requestIds` 写法都可以）：

    { "orderType": "入库单回传", "requestIds": "1,2,3,4,5" }
    { "orderType": "入库单回传", "requestIds": [1,2,3,4,5] }

响应：

    { "code":200, "message":"success", "data":{"updated":3} }

## 错误码

- 200：成功
- 400：入参错误（orderType 非法、id 非数字、超出批量上限、空集合）
- 500：服务端错误

## 限制

- 单次 `code` 最多 500 个
- 单次 `requestIds` 最多 1000 个
