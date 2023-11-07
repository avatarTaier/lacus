package com.lacus.service.datasync;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lacus.dao.datasync.entity.DataSyncSourceTableEntity;

import java.util.List;

public interface IDataSyncSourceTableService extends IService<DataSyncSourceTableEntity> {
    void removeByJobId(Long jobId);
    List<DataSyncSourceTableEntity> listByJobIdsAndDbName(List<Long> jobIds, String dbName);
    List<DataSyncSourceTableEntity> listByJobId(Long jobId);
    List<DataSyncSourceTableEntity> listByJobIds(List<Long> jobIds);
}
