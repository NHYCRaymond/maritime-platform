# maritime-platform

Shared platform commons + iam-sdk for maritime microservices.

## Modules

| Module | Description |
|--------|-------------|
| `platform-bom` | BOM for version management — import this |
| `platform-common-core` | Snowflake ID, R, ResultCode, pagination, IAM enums, events |
| `platform-common-web` | Global exception handler, TraceId, request logging, XSS filter |
| `platform-common-security` | JWT signing/verification, HMAC signature, SecurityUser context |
| `platform-common-mybatis` | MyBatis-Plus config, BaseDO, auto-fill, pagination |
| `platform-common-redis` | RedisTemplate config, distributed lock utilities |
| `platform-common-mq` | RabbitMQ topology, publisher-confirm sender |
| `platform-common-metrics` | Micrometer + Prometheus, @BusinessMetric AOP |
| `platform-common-feign` | Feign client interfaces and shared DTOs for IAM intra-cluster calls |
| `iam-sdk` | IAM SDK Spring Boot Starter for business system integration |

## Usage

### 1. Configure GitHub Packages repository in `~/.m2/settings.xml`

```xml
<server>
  <id>github</id>
  <username>YOUR_GITHUB_USERNAME</username>
  <password>YOUR_GITHUB_TOKEN</password>
</server>
```

And in your project `pom.xml` or `settings.xml`:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/NHYCRaymond/maritime-platform</url>
</repository>
```

### 2. Import the BOM

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.maritime.platform</groupId>
      <artifactId>platform-bom</artifactId>
      <version>1.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### 3. Depend on modules without versions

```xml
<dependency>
  <groupId>com.maritime.platform</groupId>
  <artifactId>platform-common-core</artifactId>
</dependency>
<dependency>
  <groupId>com.maritime.platform</groupId>
  <artifactId>platform-common-web</artifactId>
</dependency>
<dependency>
  <groupId>com.maritime.platform</groupId>
  <artifactId>platform-common-security</artifactId>
</dependency>
<dependency>
  <groupId>com.maritime.platform</groupId>
  <artifactId>iam-sdk</artifactId>
</dependency>
```

## Package Namespaces

| Module | Java package |
|--------|-------------|
| platform-common-* | `com.maritime.platform.common.*` |
| iam-sdk | `com.maritime.iam.sdk.*` (IAM-branded by design) |
