package com.lacus.service.datasync.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lacus.dao.datasync.entity.DataSyncColumnMappingEntity;
import com.lacus.dao.datasync.entity.DataSyncSavedColumn;
import com.lacus.dao.datasync.mapper.DataSyncColumnMappingMapper;
import com.lacus.service.datasync.IDataSyncColumnMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataSyncColumnMappingServiceImpl extends ServiceImpl<DataSyncColumnMappingMapper, DataSyncColumnMappingEntity> implements IDataSyncColumnMappingService {

    @Autowired
    private DataSyncColumnMappingMapper columnMappingMapper;

    @Override
    public List<DataSyncSavedColumn> querySavedColumns(DataSyncSavedColumn tpl) {
        return columnMappingMapper.querySavedColumns(tpl);
    }

    @Override
    public void removeByJobId(Long jobId) {
        LambdaQueryWrapper<DataSyncColumnMappingEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataSyncColumnMappingEntity::getJobId, jobId);
        this.remove(wrapper);
    }
}
