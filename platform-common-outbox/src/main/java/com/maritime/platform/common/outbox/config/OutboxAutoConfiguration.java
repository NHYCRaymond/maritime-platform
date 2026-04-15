package com.maritime.platform.common.outbox.config;

import com.maritime.platform.common.outbox.mapper.OutboxEntryMapper;
import com.maritime.platform.common.outbox.poller.OutboxPoller;
import com.maritime.platform.common.outbox.poller.OutboxProperties;
import com.maritime.platform.common.outbox.spi.OutboxEventPublisher;
import com.maritime.platform.common.outbox.store.OutboxStore;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@ConditionalOnClass({ com.baomidou.mybatisplus.core.mapper.BaseMapper.class })
@ConditionalOnBean(OutboxEventPublisher.class)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
@MapperScan("com.maritime.platform.common.outbox.mapper")
public class OutboxAutoConfiguration {

    @Bean
    public OutboxStore outboxStore(OutboxEntryMapper mapper) {
        return new OutboxStore(mapper);
    }

    @Bean
    public OutboxPoller outboxPoller(OutboxStore store,
                                     OutboxEventPublisher publisher,
                                     OutboxProperties props) {
        return new OutboxPoller(store, publisher, props);
    }
}