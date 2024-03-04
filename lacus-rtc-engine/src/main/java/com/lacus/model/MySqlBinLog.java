package com.lacus.model;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

/**
 * @created by shengyu on 2023/9/7 16:26
 */
@Data
public class MySqlBinLog {
    private String db;
    private String tableName;
    private JSONObject before;
    private JSONObject after;
    private String op;
}