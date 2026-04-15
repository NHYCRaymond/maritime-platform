# platform-common-outbox

Transactional outbox pattern.

## Usage

1. Add dependency:
```xml
<dependency>
    <groupId>com.maritime.platform</groupId>
    <artifactId>platform-common-outbox</artifactId>
</dependency>
```

2. Apply the DB migration (Flyway/Liquibase):

```sql
CREATE TABLE platform_outbox_entry (
    id BIGINT PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    published_at TIMESTAMP NULL,
    next_retry_at TIMESTAMP NULL,
    last_error VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_due ON platform_outbox_entry (status, next_retry_at)
    WHERE deleted_at IS NULL;
```

3. Provide an OutboxEventPublisher bean:

```java
@Component
public class RabbitOutboxPublisher implements OutboxEventPublisher {
    private final RabbitTemplate rabbit;
    public void publish(OutboxEntryDO entry) {
        rabbit.convertAndSend("events", entry.getEventType(), entry.getPayload());
    }
}
```

4. Write to the outbox in your @Transactional business methods:

```java
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    OutboxEntryDO entry = new OutboxEntryDO();
    entry.setAggregateType("Order");
    entry.setAggregateId(order.getId());
    entry.setEventType("OrderCreated");
    entry.setPayload(jsonUtils.toJson(event));
    outboxStore.save(entry);
}
```

## Configuration

```yaml
platform:
  outbox:
    enabled: true
    poll-interval: 5s
    batch-size: 100
    max-retries: 10
    base-backoff: 5s
    max-backoff: 10m
```