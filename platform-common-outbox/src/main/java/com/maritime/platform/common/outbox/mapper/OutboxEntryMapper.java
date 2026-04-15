package com.maritime.platform.common.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maritime.platform.common.outbox.dataobject.OutboxEntryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEntryMapper extends BaseMapper<OutboxEntryDO> {}