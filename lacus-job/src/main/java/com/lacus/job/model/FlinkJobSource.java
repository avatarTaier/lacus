package com.lacus.job.model;

import lombok.Data;

import java.util.List;

@Data
public class FlinkJobSource {
    private String hostname;
    private Integer port;
    private String username;
    private String password;
    private List<String> databaseList;
    private List<String> tableList;
    private String syncType;
    private Long timeStamp;
}
