# 西安吉利京东工具（xianjilijd-job）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `D:\project\tools\xianjilijd-job` 目录构建一个本地 Spring Boot 服务，提供两个 POST + JSON 接口：查询出库/入库回传记录、把 `is_sync`/`sync_times` 重置为 0。

**Architecture:** 单模块 Spring Boot 应用，端口 8380，三层结构（Controller → Service → MyBatis Plus Mapper）连接 MySQL `artemis` 库。统一 `{code, message, data}` 响应包装，全局异常处理器兜底。本期不写自动化测试，最后一步用 Postman 端到端验证（spec 第 10 节明确）。

**Tech Stack:** Spring Boot 2.7.18 / JDK 21 / Maven 3.5+ / MyBatis Plus 3.5.7 / MySQL Connector 8.0.33 / Lombok / HikariCP（默认）

**仓库根目录（后续所有相对路径以此为准）:** `D:\project\tools\xianjilijd-job`

**关联 spec：** `docs/superpowers/specs/2026-05-02-xianjilijd-job-design.md`

---

## 文件结构

每个文件一个明确职责，避免单文件膨胀：

- `pom.xml` —— Maven 构建定义，固定依赖版本
- `.gitignore` —— 忽略 target/、IDE 文件、日志
- `src/main/java/com/xianjilijd/job/XianjilijdJobApplication.java` —— Spring Boot 启动类，含 `@MapperScan`
- `src/main/resources/application.yml` —— 端口、数据源、MyBatis Plus、Jackson 配置（数据库参数走环境变量，含默认值）
- `src/main/java/com/xianjilijd/job/common/ApiResponse.java` —— 统一响应包装
- `src/main/java/com/xianjilijd/job/common/OrderTypeEnum.java` —— `入库单回传` / `出库单回传` 枚举
- `src/main/java/com/xianjilijd/job/common/BizException.java` —— 业务异常
- `src/main/java/com/xianjilijd/job/exception/GlobalExceptionHandler.java` —— `@RestControllerAdvice` 兜底
- `src/main/java/com/xianjilijd/job/entity/OutOrderBack.java` —— `out_order_back` 表实体（仅映射本期需要的列）
- `src/main/java/com/xianjilijd/job/entity/ReceiptNoteBack.java` —— `receipt_note_back` 表实体
- `src/main/java/com/xianjilijd/job/mapper/OutOrderBackMapper.java` —— BaseMapper
- `src/main/java/com/xianjilijd/job/mapper/ReceiptNoteBackMapper.java` —— BaseMapper
- `src/main/java/com/xianjilijd/job/dto/QueryOrderBackReq.java` —— 查询请求体（含 `code` 字段双格式反序列化）
- `src/main/java/com/xianjilijd/job/dto/QueryOrderBackResp.java` —— 查询响应体
- `src/main/java/com/xianjilijd/job/dto/ActivateOrderBackReq.java` —— 激活请求体（含 `requestIds` 双格式反序列化）
- `src/main/java/com/xianjilijd/job/service/OrderBackService.java` —— 服务接口
- `src/main/java/com/xianjilijd/job/service/impl/OrderBackServiceImpl.java` —— 实现：参数清洗、白名单校验、批量上限、查询/批量更新
- `src/main/java/com/xianjilijd/job/controller/OrderBackController.java` —— 两个 POST 端点
- `README.md` —— 启动、环境变量、Postman 示例

---

## Task 1：pom.xml 与构建骨架

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`

- [ ] **Step 1: 创建 `pom.xml`**

写入下面内容（路径：`D:\project\tools\xianjilijd-job\pom.xml`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.xianjilijd</groupId>
    <artifactId>xianjilijd-job</artifactId>
    <version>1.0.0</version>
    <name>xianjilijd-job</name>
    <description>西安吉利京东工具</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 `.gitignore`**

写入下面内容（路径：`D:\project\tools\xianjilijd-job\.gitignore`）：

```
target/
*.class
*.jar
.idea/
.vscode/
*.iml
.DS_Store
*.log
HELP.md
build/
out/
```

- [ ] **Step 3: 验证 Maven 可用并下载依赖**

运行（在仓库根目录）：
```
mvn -v
mvn -B -DskipTests dependency:resolve
```

期望：`mvn -v` 打印 Maven 版本与 JDK 1.8 路径；`dependency:resolve` 以 `BUILD SUCCESS` 结束。
失败排查：JDK 不是 1.8 → 设置 `JAVA_HOME` 指向 JDK 8；网络问题 → 用 `~/.m2/settings.xml` 配置阿里云镜像。

- [ ] **Step 4: Commit**

```
cd /d D:\project\tools\xianjilijd-job
git add pom.xml .gitignore
git commit -m "build: add maven pom and gitignore for spring boot 2.7"
```

---

## Task 2：启动类与 application.yml

**Files:**
- Create: `src/main/java/com/xianjilijd/job/XianjilijdJobApplication.java`
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: 创建启动类**

路径：`src/main/java/com/xianjilijd/job/XianjilijdJobApplication.java`

```java
package com.xianjilijd.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xianjilijd.job.mapper")
public class XianjilijdJobApplication {
    public static void main(String[] args) {
        SpringApplication.run(XianjilijdJobApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 `application.yml`**

路径：`src/main/resources/application.yml`

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
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto

logging:
  level:
    com.xianjilijd.job: INFO
```

- [ ] **Step 3: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`，目标路径 `target/classes/com/xianjilijd/job/XianjilijdJobApplication.class` 存在。

- [ ] **Step 4: Commit**

```
git add pom.xml src/main/java/com/xianjilijd/job/XianjilijdJobApplication.java src/main/resources/application.yml
git commit -m "feat: scaffold spring boot app with port 8380 and mysql datasource"
```

---

## Task 3：公共组件（统一响应、枚举、异常）

**Files:**
- Create: `src/main/java/com/xianjilijd/job/common/ApiResponse.java`
- Create: `src/main/java/com/xianjilijd/job/common/OrderTypeEnum.java`
- Create: `src/main/java/com/xianjilijd/job/common/BizException.java`
- Create: `src/main/java/com/xianjilijd/job/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 `ApiResponse.java`**

```java
package com.xianjilijd.job.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> badRequest(String message) {
        return new ApiResponse<>(400, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, message, null);
    }
}
```

- [ ] **Step 2: 创建 `OrderTypeEnum.java`**

```java
package com.xianjilijd.job.common;

import java.util.Arrays;

public enum OrderTypeEnum {
    RECEIPT("入库单回传"),
    OUT_ORDER("出库单回传");

    private final String label;

    OrderTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static OrderTypeEnum fromLabel(String label) {
        if (label == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.label.equals(label))
                .findFirst()
                .orElse(null);
    }
}
```

- [ ] **Step 3: 创建 `BizException.java`**

```java
package com.xianjilijd.job.common;

public class BizException extends RuntimeException {
    public BizException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: 创建 `GlobalExceptionHandler.java`**

```java
package com.xianjilijd.job.exception;

import com.xianjilijd.job.common.ApiResponse;
import com.xianjilijd.job.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("biz error: {}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("validation failed: {}", msg);
        return ApiResponse.badRequest(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("request body not readable: {}", e.getMessage());
        return ApiResponse.badRequest("请求体格式错误，请检查 JSON 是否合法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleAll(Exception e) {
        log.error("server error", e);
        return ApiResponse.error("服务器内部错误");
    }
}
```

- [ ] **Step 5: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`。

- [ ] **Step 6: Commit**

```
git add src/main/java/com/xianjilijd/job/common src/main/java/com/xianjilijd/job/exception
git commit -m "feat: add api response, order type enum, biz exception, global handler"
```

---

## Task 4：实体类与 Mapper

**Files:**
- Create: `src/main/java/com/xianjilijd/job/entity/OutOrderBack.java`
- Create: `src/main/java/com/xianjilijd/job/entity/ReceiptNoteBack.java`
- Create: `src/main/java/com/xianjilijd/job/mapper/OutOrderBackMapper.java`
- Create: `src/main/java/com/xianjilijd/job/mapper/ReceiptNoteBackMapper.java`

实体仅映射本期需要的 5 个字段（id、code、is_sync、sync_times、last_modify_date），其他字段不映射，避免无谓的全字段拉取。

- [ ] **Step 1: 创建 `OutOrderBack.java`**

```java
package com.xianjilijd.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("out_order_back")
public class OutOrderBack {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("out_order_code")
    private String outOrderCode;

    @TableField("is_sync")
    private Integer isSync;

    @TableField("sync_times")
    private Integer syncTimes;

    @TableField("last_modify_date")
    private LocalDateTime lastModifyDate;
}
```

- [ ] **Step 2: 创建 `ReceiptNoteBack.java`**

```java
package com.xianjilijd.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("receipt_note_back")
public class ReceiptNoteBack {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("receipt_code")
    private String receiptCode;

    @TableField("is_sync")
    private Integer isSync;

    @TableField("sync_times")
    private Integer syncTimes;

    @TableField("last_modify_date")
    private LocalDateTime lastModifyDate;
}
```

- [ ] **Step 3: 创建 `OutOrderBackMapper.java`**

```java
package com.xianjilijd.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianjilijd.job.entity.OutOrderBack;

public interface OutOrderBackMapper extends BaseMapper<OutOrderBack> {
}
```

- [ ] **Step 4: 创建 `ReceiptNoteBackMapper.java`**

```java
package com.xianjilijd.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianjilijd.job.entity.ReceiptNoteBack;

public interface ReceiptNoteBackMapper extends BaseMapper<ReceiptNoteBack> {
}
```

- [ ] **Step 5: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`。

- [ ] **Step 6: Commit**

```
git add src/main/java/com/xianjilijd/job/entity src/main/java/com/xianjilijd/job/mapper
git commit -m "feat: add OutOrderBack and ReceiptNoteBack entities with mappers"
```

---

## Task 5：DTO 与 Jackson 双格式反序列化

`code` 与 `requestIds` 字段都要支持两种 JSON 写法：
- 字符串："a,b,c" / "1,2,3"
- 数组：["a","b","c"] / [1,2,3]

通过两个内嵌的 `JsonDeserializer` 实现。

**Files:**
- Create: `src/main/java/com/xianjilijd/job/dto/QueryOrderBackReq.java`
- Create: `src/main/java/com/xianjilijd/job/dto/QueryOrderBackResp.java`
- Create: `src/main/java/com/xianjilijd/job/dto/ActivateOrderBackReq.java`

- [ ] **Step 1: 创建 `QueryOrderBackReq.java`**

```java
package com.xianjilijd.job.dto;

import com.fasterxml.jackson.annotation.JsonDeserialize;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class QueryOrderBackReq {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @JsonDeserialize(using = StringOrListDeserializer.class)
    private List<String> code;

    public static class StringOrListDeserializer extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<String> result = new ArrayList<>();
            if (node.isArray()) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    result.add(it.next().asText());
                }
            } else if (node.isTextual()) {
                String s = node.asText();
                if (s != null && !s.isEmpty()) {
                    for (String part : s.split(",")) {
                        result.add(part);
                    }
                }
            }
            return result;
        }
    }
}
```

- [ ] **Step 2: 创建 `QueryOrderBackResp.java`**

```java
package com.xianjilijd.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QueryOrderBackResp {
    private Long id;
    private String responseCode;
    private Integer isSync;
    private Integer syncTimes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModifyDate;

    private String orderType;
}
```

- [ ] **Step 3: 创建 `ActivateOrderBackReq.java`**

```java
package com.xianjilijd.job.dto;

import com.fasterxml.jackson.annotation.JsonDeserialize;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class ActivateOrderBackReq {

    @NotBlank(message = "orderType 不能为空")
    private String orderType;

    @JsonDeserialize(using = LongStringOrListDeserializer.class)
    private List<Long> requestIds;

    public static class LongStringOrListDeserializer extends JsonDeserializer<List<Long>> {
        @Override
        public List<Long> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            List<Long> result = new ArrayList<>();
            if (node.isArray()) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    JsonNode el = it.next();
                    if (el.isNumber()) {
                        result.add(el.asLong());
                    } else {
                        String s = el.asText();
                        if (s != null && !s.trim().isEmpty()) {
                            result.add(Long.parseLong(s.trim()));
                        }
                    }
                }
            } else if (node.isTextual()) {
                String s = node.asText();
                if (s != null && !s.isEmpty()) {
                    for (String part : s.split(",")) {
                        if (!part.trim().isEmpty()) {
                            result.add(Long.parseLong(part.trim()));
                        }
                    }
                }
            } else if (node.isNumber()) {
                result.add(node.asLong());
            }
            return result;
        }
    }
}
```

- [ ] **Step 4: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`。

- [ ] **Step 5: Commit**

```
git add src/main/java/com/xianjilijd/job/dto
git commit -m "feat: add request/response DTOs with dual-format jackson deserializers"
```

---

## Task 6：Service 层（接口 + 实现）

实现要点：
- 入参清洗：trim、去引号、去重、空集合判 400
- 白名单：orderType 只能是两个固定值
- 批量上限：code ≤ 500、requestIds ≤ 1000
- 查询排序：`ORDER BY id DESC`
- 激活：`LambdaUpdateWrapper.set()` 精确控制 SET 列，只更新 `is_sync` 和 `sync_times`
- `@Transactional` 包裹批量更新

**Files:**
- Create: `src/main/java/com/xianjilijd/job/service/OrderBackService.java`
- Create: `src/main/java/com/xianjilijd/job/service/impl/OrderBackServiceImpl.java`

- [ ] **Step 1: 创建 `OrderBackService.java`**

```java
package com.xianjilijd.job.service;

import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;

import java.util.List;

public interface OrderBackService {

    List<QueryOrderBackResp> query(QueryOrderBackReq req);

    int activate(ActivateOrderBackReq req);
}
```

- [ ] **Step 2: 创建 `OrderBackServiceImpl.java`**

```java
package com.xianjilijd.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xianjilijd.job.common.BizException;
import com.xianjilijd.job.common.OrderTypeEnum;
import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;
import com.xianjilijd.job.entity.OutOrderBack;
import com.xianjilijd.job.entity.ReceiptNoteBack;
import com.xianjilijd.job.mapper.OutOrderBackMapper;
import com.xianjilijd.job.mapper.ReceiptNoteBackMapper;
import com.xianjilijd.job.service.OrderBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderBackServiceImpl implements OrderBackService {

    private static final int MAX_QUERY_CODES = 500;
    private static final int MAX_ACTIVATE_IDS = 1000;

    private final OutOrderBackMapper outOrderBackMapper;
    private final ReceiptNoteBackMapper receiptNoteBackMapper;

    @Override
    public List<QueryOrderBackResp> query(QueryOrderBackReq req) {
        OrderTypeEnum type = parseOrderType(req.getOrderType());
        List<String> codes = sanitizeCodes(req.getCode());

        if (type == OrderTypeEnum.RECEIPT) {
            LambdaQueryWrapper<ReceiptNoteBack> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(ReceiptNoteBack::getReceiptCode, codes)
                   .orderByDesc(ReceiptNoteBack::getId);
            return receiptNoteBackMapper.selectList(wrapper).stream()
                    .map(r -> QueryOrderBackResp.builder()
                            .id(r.getId())
                            .responseCode(r.getReceiptCode())
                            .isSync(r.getIsSync())
                            .syncTimes(r.getSyncTimes())
                            .lastModifyDate(r.getLastModifyDate())
                            .orderType(req.getOrderType())
                            .build())
                    .collect(Collectors.toList());
        } else {
            LambdaQueryWrapper<OutOrderBack> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(OutOrderBack::getOutOrderCode, codes)
                   .orderByDesc(OutOrderBack::getId);
            return outOrderBackMapper.selectList(wrapper).stream()
                    .map(r -> QueryOrderBackResp.builder()
                            .id(r.getId())
                            .responseCode(r.getOutOrderCode())
                            .isSync(r.getIsSync())
                            .syncTimes(r.getSyncTimes())
                            .lastModifyDate(r.getLastModifyDate())
                            .orderType(req.getOrderType())
                            .build())
                    .collect(Collectors.toList());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int activate(ActivateOrderBackReq req) {
        OrderTypeEnum type = parseOrderType(req.getOrderType());
        List<Long> ids = sanitizeIds(req.getRequestIds());

        if (type == OrderTypeEnum.RECEIPT) {
            LambdaUpdateWrapper<ReceiptNoteBack> u = new LambdaUpdateWrapper<>();
            u.set(ReceiptNoteBack::getIsSync, 0)
             .set(ReceiptNoteBack::getSyncTimes, 0)
             .in(ReceiptNoteBack::getId, ids);
            return receiptNoteBackMapper.update(null, u);
        } else {
            LambdaUpdateWrapper<OutOrderBack> u = new LambdaUpdateWrapper<>();
            u.set(OutOrderBack::getIsSync, 0)
             .set(OutOrderBack::getSyncTimes, 0)
             .in(OutOrderBack::getId, ids);
            return outOrderBackMapper.update(null, u);
        }
    }

    private OrderTypeEnum parseOrderType(String label) {
        if (label == null) {
            throw new BizException("orderType 不能为空");
        }
        OrderTypeEnum type = OrderTypeEnum.fromLabel(label.trim());
        if (type == null) {
            throw new BizException("orderType 必须是 入库单回传 或 出库单回传");
        }
        return type;
    }

    private List<String> sanitizeCodes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException("code 不能为空");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String c : raw) {
            if (c == null) continue;
            String s = stripQuotes(c.trim());
            if (!s.isEmpty()) seen.add(s);
        }
        if (seen.isEmpty()) {
            throw new BizException("code 不能为空");
        }
        if (seen.size() > MAX_QUERY_CODES) {
            throw new BizException("code 数量超过上限 " + MAX_QUERY_CODES);
        }
        return new ArrayList<>(seen);
    }

    private List<Long> sanitizeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new BizException("requestIds 不能为空");
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null) seen.add(id);
        }
        if (seen.isEmpty()) {
            throw new BizException("requestIds 不能为空");
        }
        if (seen.size() > MAX_ACTIVATE_IDS) {
            throw new BizException("requestIds 数量超过上限 " + MAX_ACTIVATE_IDS);
        }
        return new ArrayList<>(seen);
    }

    private String stripQuotes(String s) {
        if (s.length() < 2) return s;
        char first = s.charAt(0);
        char last = s.charAt(s.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                || (first == '“' && last == '”')) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}
```

- [ ] **Step 3: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```
git add src/main/java/com/xianjilijd/job/service
git commit -m "feat: add OrderBackService with sanitize, whitelist, batch limits, tx"
```

---

## Task 7：Controller 层

**Files:**
- Create: `src/main/java/com/xianjilijd/job/controller/OrderBackController.java`

- [ ] **Step 1: 创建 `OrderBackController.java`**

```java
package com.xianjilijd.job.controller;

import com.xianjilijd.job.common.ApiResponse;
import com.xianjilijd.job.dto.ActivateOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackReq;
import com.xianjilijd.job.dto.QueryOrderBackResp;
import com.xianjilijd.job.service.OrderBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tools/xianjilijd")
@RequiredArgsConstructor
public class OrderBackController {

    private final OrderBackService orderBackService;

    @PostMapping("/queryOrderBack")
    public ApiResponse<List<QueryOrderBackResp>> queryOrderBack(@Valid @RequestBody QueryOrderBackReq req) {
        List<QueryOrderBackResp> rows = orderBackService.query(req);
        if (rows.isEmpty()) {
            return ApiResponse.success("无数据，请人工核实", Collections.emptyList());
        }
        return ApiResponse.success(rows);
    }

    @PostMapping("/activateOrderBack")
    public ApiResponse<Map<String, Integer>> activateOrderBack(@Valid @RequestBody ActivateOrderBackReq req) {
        int updated = orderBackService.activate(req);
        Map<String, Integer> data = new HashMap<>();
        data.put("updated", updated);
        return ApiResponse.success(data);
    }
}
```

- [ ] **Step 2: 编译验证**

运行：`mvn -B -DskipTests compile`
期望：`BUILD SUCCESS`。

- [ ] **Step 3: 完整打包验证**

运行：`mvn -B -DskipTests package`
期望：`BUILD SUCCESS`，生成 `target/xianjilijd-job-1.0.0.jar`（约 30MB+）。

- [ ] **Step 4: Commit**

```
git add src/main/java/com/xianjilijd/job/controller
git commit -m "feat: add OrderBackController with two POST endpoints"
```

---

## Task 8：README 与启动说明

**Files:**
- Create: `README.md`

- [ ] **Step 1: 创建 `README.md`**

```markdown
# 西安吉利京东工具（xianjilijd-job）

针对 `out_order_back`（出库单回传）和 `receipt_note_back`（收货单回传）两张表，提供两个本地 HTTP 接口：查询同步状态、把 `is_sync` 与 `sync_times` 重置为 0。

## 启动方式

需要 JDK 8 与 Maven。默认连接的数据库是 `172.16.65.69:3306/artemis`，账号 `root`。

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

例：
    set DB_HOST=192.168.1.10
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
```

- [ ] **Step 2: Commit**

```
git add README.md
git commit -m "docs: add README with run, env vars, and postman examples"
```

---

## Task 9：启动应用并核对日志

- [ ] **Step 1: 启动应用**

在仓库根目录执行：
    mvn -DskipTests spring-boot:run

- [ ] **Step 2: 核对启动日志**

期望（按顺序至少出现）：
- `Started XianjilijdJobApplication in X.X seconds`
- `Tomcat started on port(s): 8380`
- `HikariPool-1 - Start completed.`
- `MapperScan` 找到 `OutOrderBackMapper`、`ReceiptNoteBackMapper`

失败排查：
- `Communications link failure` → 数据库 IP 不通，检查 VPN 或本地网络可达 `172.16.65.69:3306`
- `Access denied for user 'root'` → 密码错误或账号被锁
- `Unknown database 'artemis'` → 库名不对，调 `DB_NAME`

- [ ] **Step 3: 健康检查**

新开终端运行（保持应用进程运行）：
    curl -i http://127.0.0.1:8380/tools/xianjilijd/queryOrderBack

期望：返回 405 Method Not Allowed（因为 GET 走不通），证明 8380 端口在监听。

- [ ] **Step 4: 应用保持运行**

后续 Task 10、Task 11 都依赖应用持续运行；不要在这一步关闭。

---

## Task 10：Postman 端到端验证 — 接口 1

每个用例填好后在 Postman 点 Send，按下面"期望"对照实际响应。如果不一致，停下来排查（先看应用日志里的 SQL 与异常）。

- [ ] **Step 1: 用例 1.A — 单 code 命中**

请求：
- Method: POST
- URL: `http://127.0.0.1:8380/tools/xianjilijd/queryOrderBack`
- Headers: `Content-Type: application/json`
- Body (raw, JSON)：

      { "orderType": "出库单回传", "code": "HKCK6105230603000002" }

期望（具体 id 与时间因数据而异）：

    {
      "code": 200,
      "message": "success",
      "data": [
        {
          "id": <真实id>,
          "responseCode": "HKCK6105230603000002",
          "isSync": <0或1>,
          "syncTimes": <数字>,
          "lastModifyDate": "yyyy-MM-dd HH:mm:ss",
          "orderType": "出库单回传"
        }
      ]
    }

提示：先在 MySQL 客户端 `SELECT out_order_code FROM out_order_back ORDER BY id DESC LIMIT 5;` 拿一个真实 code 来测。

- [ ] **Step 2: 用例 1.B — 多 code（字符串逗号分隔）**

Body：

    { "orderType": "出库单回传", "code": "<真实code1>,<真实code2>" }

期望：`data` 包含两条记录，按 `id` 倒序。

- [ ] **Step 3: 用例 1.C — 多 code（JSON 数组）**

Body：

    { "orderType": "入库单回传", "code": ["<真实receiptCode1>","<真实receiptCode2>"] }

期望：查 `receipt_note_back` 表，`responseCode` 字段对应 `receipt_code`，`orderType` 回显 `入库单回传`。

- [ ] **Step 4: 用例 1.D — code 不存在**

Body：

    { "orderType": "出库单回传", "code": "DEFINITELY-NOT-EXIST-001" }

期望：

    { "code": 200, "message": "无数据，请人工核实", "data": [] }

- [ ] **Step 5: 用例 1.E — orderType 非法**

Body：

    { "orderType": "随便写的", "code": "HKCK6105230603000002" }

期望：

    { "code": 400, "message": "orderType 必须是 入库单回传 或 出库单回传", "data": null }

- [ ] **Step 6: 用例 1.F — code 为空**

Body：

    { "orderType": "出库单回传", "code": "" }

或：

    { "orderType": "出库单回传", "code": [] }

期望：HTTP 400，`message` 提示 code 不能为空。

---

## Task 11：Postman 端到端验证 — 接口 2

⚠️ 这个接口会真实修改生产/测试数据，先在前面 Task 10 的查询里挑一个**测试用** id 记下原始 `is_sync`/`sync_times`，验证完后人工核对修改是否符合预期。

- [ ] **Step 1: 准备测试 id**

在 MySQL 客户端执行（出库表为例）：

    SELECT id, out_order_code, is_sync, sync_times FROM out_order_back ORDER BY id DESC LIMIT 1;

记下这条记录的 `id` 和原始 `is_sync`、`sync_times`。

- [ ] **Step 2: 用例 2.A — 单 id（字符串）**

请求：
- Method: POST
- URL: `http://127.0.0.1:8380/tools/xianjilijd/activateOrderBack`
- Headers: `Content-Type: application/json`
- Body：

      { "orderType": "出库单回传", "requestIds": "<测试id>" }

期望：

    { "code": 200, "message": "success", "data": { "updated": 1 } }

数据库核对：

    SELECT id, is_sync, sync_times FROM out_order_back WHERE id = <测试id>;

`is_sync` 应为 0、`sync_times` 应为 0；其他字段未变。

- [ ] **Step 3: 用例 2.B — 多 id（字符串逗号分隔）**

Body：

    { "orderType": "出库单回传", "requestIds": "<id1>,<id2>,<id3>" }

期望：`updated` 等于实际匹配到的行数（如果 3 个 id 都存在，则为 3）。

- [ ] **Step 4: 用例 2.C — 多 id（数字数组）**

Body：

    { "orderType": "入库单回传", "requestIds": [<id1>,<id2>] }

期望：操作 `receipt_note_back` 表，`updated` 等于实际匹配行数。

- [ ] **Step 5: 用例 2.D — id 不存在**

Body：

    { "orderType": "出库单回传", "requestIds": [9999999999] }

期望：

    { "code": 200, "message": "success", "data": { "updated": 0 } }

- [ ] **Step 6: 用例 2.E — orderType 非法**

Body：

    { "orderType": "瞎写", "requestIds": [1] }

期望：HTTP 400，`message` 提示 orderType 非法。

- [ ] **Step 7: 用例 2.F — requestIds 为空**

Body：

    { "orderType": "出库单回传", "requestIds": [] }

期望：HTTP 400，`message` 提示 requestIds 不能为空。

- [ ] **Step 8: 用例 2.G — requestIds 含非数字**

Body：

    { "orderType": "出库单回传", "requestIds": "abc,def" }

期望：HTTP 400（`HttpMessageNotReadableException` → "请求体格式错误..."）。

---

## Task 12：停止应用并推送到远程

- [ ] **Step 1: 停止 spring-boot 应用**

在运行 `mvn spring-boot:run` 的终端按 `Ctrl + C`，确认进程退出。

- [ ] **Step 2: 检查工作区干净**

运行：`git status`
期望：`nothing to commit, working tree clean`（如果还有未提交内容，回到对应 task 检查）。

- [ ] **Step 3: 查看待推送提交**

运行：`git log --oneline origin/master..HEAD`
期望：列出 Task 1~8 的若干 commits（顺序倒过来看）。

- [ ] **Step 4: 推送**

运行：`git push origin master`
期望：`To https://github.com/Jax1883/xianjiljd-job.git ... master -> master`。

- [ ] **Step 5: 在 GitHub 上目视确认**

打开 `https://github.com/Jax1883/xianjiljd-job/tree/master`，看到所有源码与 README。

---

## 完成后的状态

- 本地服务可由 `mvn spring-boot:run` 启动，监听 8380
- 两个 POST 接口可用 Postman 调通，5+ 用例符合 spec 描述
- 所有源码已提交并推送至 `origin/master`
- README 包含启动步骤、环境变量、Postman 调用示例
