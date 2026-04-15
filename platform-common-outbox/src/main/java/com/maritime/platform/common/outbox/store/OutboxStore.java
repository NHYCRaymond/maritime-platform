package com.maritime.platform.common.outbox.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maritime.platform.common.outbox.dataobject.OutboxEntryDO;
import com.maritime.platform.common.outbox.mapper.OutboxEntryMapper;
import com.maritime.platform.common.outbox.model.OutboxEntryStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxStore {

    private final OutboxEntryMapper mapper;

    public OutboxStore(OutboxEntryMapper mapper) { this.mapper = mapper; }

    /** Called from a business @Transactional method — piggybacks on its txn. */
    public void save(OutboxEntryDO entry) {
        if (entry.getStatus() == null) entry.setStatus(OutboxEntryStatus.PENDING.name());
        if (entry.getRetryCount() == null) entry.setRetryCount(0);
        mapper.insert(entry);
    }

    public List<OutboxEntryDO> findDue(int limit) {
        LambdaQueryWrapper<OutboxEntryDO> q = new LambdaQueryWrapper<>();
        LocalDateTime now = LocalDateTime.now();
        q.in(OutboxEntryDO::getStatus,
                OutboxEntryStatus.PENDING.name(), OutboxEntryStatus.FAILED.name())
         .and(w -> w.isNull(OutboxEntryDO::getNextRetryAt)
                    .or().le(OutboxEntryDO::getNextRetryAt, now))
         .orderByAsc(OutboxEntryDO::getId)
         .last("LIMIT " + limit);
        return mapper.selectList(q);
    }

    public void markPublished(Long id) {
        OutboxEntryDO update = new OutboxEntryDO();
        update.setId(id);
        update.setStatus(OutboxEntryStatus.PUBLISHED.name());
        update.setPublishedAt(LocalDateTime.now());
        mapper.updateById(update);
    }

    public void markFailed(Long id, int retryCount, LocalDateTime nextRetryAt, String error) {
        OutboxEntryDO update = new OutboxEntryDO();
        update.setId(id);
        update.setStatus(OutboxEntryStatus.FAILED.name());
        update.setRetryCount(retryCount);
        update.setNextRetryAt(nextRetryAt);
        // Truncate error to 512 chars to match typical VARCHAR
        update.setLastError(error == null ? null
                : error.length() > 512 ? error.substring(0, 512) : error);
        mapper.updateById(update);
    }
}