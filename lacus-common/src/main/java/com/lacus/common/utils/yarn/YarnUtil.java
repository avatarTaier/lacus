package com.lacus.common.utils.yarn;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.lacus.common.utils.hdfs.HdfsUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.flink.api.common.JobID;
import org.apache.flink.client.cli.CliArgsException;
import org.apache.flink.client.deployment.ClusterDeploymentException;
import org.apache.flink.client.deployment.ClusterSpecification;
import org.apache.flink.client.deployment.application.ApplicationConfiguration;
import org.apache.flink.client.program.ClusterClient;
import org.apache.flink.client.program.ClusterClientProvider;
import org.apache.flink.configuration.*;
import org.apache.flink.core.execution.SavepointFormatType;
import org.apache.flink.core.fs.Path;
import org.apache.flink.runtime.messages.Acknowledge;
import org.apache.flink.util.FlinkException;
import org.apache.flink.yarn.YarnClientYarnClusterInformationRetriever;
import org.apache.flink.yarn.YarnClusterClientFactory;
import org.apache.flink.yarn.YarnClusterDescriptor;
import org.apache.flink.yarn.configuration.YarnConfigOptions;
import org.apache.flink.yarn.configuration.YarnConfigOptionsInternal;
import org.apache.flink.yarn.configuration.YarnDeploymentTarget;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.ConverterUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.apache.flink.configuration.MemorySize.MemoryUnit.MEGA_BYTES;

@Slf4j
public class YarnUtil {

    /**
     * 提交flink任务到yarn
     *
     * @param mainClass     flink 任务主类
     * @param args          调用jar包传入的参数
     * @param flinkParams   flink默认配置
     * @param userJarPath   flink项目jar包路径
     * @param flinkConf     flink 配置
     * @param checkpointUrl checkpoint地址
     */
    public static String deployOnYarn(String mainClass,
                                      String[] args,
                                      String jobName,
                                      FlinkParams flinkParams,
                                      String userJarPath,
                                      String flinkConf,
                                      String checkpointUrl,
                                      String defaultHdfs,
                                      String hadoopUserName,
                                      String flinkLibs,
                                      String flinkDistJar) {
        // 获取flink的配置
        Configuration flinkConfig = ConfigUtil.getFlinkConf(defaultHdfs, hadoopUserName, flinkConf);
        settingLog(defaultHdfs, hadoopUserName, flinkConfig, flinkConf);
        // 设置checkpoint模式为增量
        flinkConfig.set(CheckpointingOptions.INCREMENTAL_CHECKPOINTS, true);
        // 设置用户flink项目jar包
        flinkConfig.set(PipelineOptions.JARS, Collections.singletonList(userJarPath));
        Path remoteLib = new Path(defaultHdfs + flinkLibs);
        // 设置依赖jar包
        flinkConfig.set(YarnConfigOptions.PROVIDED_LIB_DIRS, Collections.singletonList(remoteLib.toString()));
        // 设置flink jar包
        flinkConfig.set(YarnConfigOptions.FLINK_DIST_JAR, defaultHdfs + flinkDistJar);
        //设置为application模式
        flinkConfig.set(DeploymentOptions.TARGET, YarnDeploymentTarget.APPLICATION.getName());
        // yarn application name
        flinkConfig.set(YarnConfigOptions.APPLICATION_NAME, jobName);

        // 设置flink 从checkpoints启动的路径 execution.savepoint.path
        if (StringUtils.isNotBlank(checkpointUrl)) {
            flinkConfig.setString("execution.savepoint.path", checkpointUrl);
        }
        // 设置flink jobManager 和taskManager内存
        flinkConfig.set(JobManagerOptions.TOTAL_PROCESS_MEMORY, MemorySize.parse(flinkParams.getMasterMemoryMB().toString(), MEGA_BYTES));
        flinkConfig.set(TaskManagerOptions.TOTAL_PROCESS_MEMORY, MemorySize.parse(flinkParams.getTaskManagerMemoryMB().toString(), MEGA_BYTES));
        flinkConfig.set(TaskManagerOptions.NUM_TASK_SLOTS, flinkParams.getSlotsPerTaskManager());

        // 设置用户flink任务jar的参数和主类
        ApplicationConfiguration appConfig = new ApplicationConfiguration(args, mainClass);

        YarnClusterDescriptor yarnClusterDescriptor = initYarnClusterDescriptor(defaultHdfs, hadoopUserName, flinkConfig, flinkConf);
        // 用于提交yarn任务的一些默认参数，比如jobManager内存数、taskManager内存数和slot数量
        ClusterSpecification clusterSpecification = new ClusterSpecification.ClusterSpecificationBuilder()
                .setMasterMemoryMB(flinkParams.getMasterMemoryMB())
                .setSlotsPerTaskManager(flinkParams.getSlotsPerTaskManager())
                .setTaskManagerMemoryMB(flinkParams.getTaskManagerMemoryMB())
                .createClusterSpecification();

        ClusterClientProvider<ApplicationId> clusterClientProvider;
        try {
            HdfsUtil.envSetting(hadoopUserName);
            clusterClientProvider = yarnClusterDescriptor.deployApplicationCluster(clusterSpecification, appConfig);
            ClusterClient<ApplicationId> clusterClient = clusterClientProvider.getClusterClient();
            ApplicationId applicationId = clusterClient.getClusterId();
            return applicationId.toString();
        } catch (ClusterDeploymentException e) {
            log.error("部署到Yarn错误", e);
            String rootError = ExceptionUtils.getRootCauseMessage(e);
            String template = "yarn logs -applicationId ";
            String applicationId = null;
            if (rootError.contains(template)) {
                applicationId = rootError.substring(rootError.indexOf(template)).replaceAll(template, "").trim();
            }
            throw new RuntimeException("提交到Yarn错误：" + applicationId);
        }
    }

    /**
     * @param conf:     yarn配置文件
     * @param appTypes: 任务类型 Apache Flink/MAPREDUCE
     * @description: 获取yarn上正在运行的任务
     */
    public static List<ApplicationModel> listYarnRunningJob(String defaultHdfs, String hadoopUserName, String conf, String appTypes) {
        YarnConfiguration yarnConf = new YarnConfiguration();
        ConfigUtil.initConfig(defaultHdfs, hadoopUserName, yarnConf, conf);
        YarnClient yarnClient = YarnClient.createYarnClient();
        yarnClient.init(yarnConf);
        yarnClient.start();
        try {
            List<ApplicationModel> applicationList = Lists.newArrayList();
            List<ApplicationReport> applications = yarnClient.getApplications(Sets.newHashSet(appTypes), EnumSet.of(YarnApplicationState.RUNNING));
            for (ApplicationReport application : applications) {
                ApplicationModel app = new ApplicationModel(application);
                applicationList.add(app);
            }
            return applicationList;
        } catch (Exception e) {
            log.error("获取yarn任务失败:", e);
            throw new RuntimeException("获取yarn任务失败");
        } finally {
            yarnClient.stop();
        }
    }

    /**
     * @param conf: yarn配置文件
     * @description: 根据applicationId 获取Yarn上配置
     */
    public static ApplicationModel yarnJobDetail(String defaultHdfs, String hadoopUserName, String conf, String appId) {
        YarnConfiguration yarnConf = new YarnConfiguration();
        ConfigUtil.initConfig(defaultHdfs, hadoopUserName, yarnConf, conf);
        YarnClient yarnClient = YarnClient.createYarnClient();
        yarnClient.init(yarnConf);
        yarnClient.start();
        try {
            ApplicationId applicationId = ConverterUtils.toApplicationId(appId);
            ApplicationReport applicationReport = yarnClient.getApplicationReport(applicationId);
            return new ApplicationModel(applicationReport);
        } catch (Exception e) {
            log.error("获取yarn任务失败:", e);
            throw new RuntimeException("获取yarn任务失败");
        } finally {
            yarnClient.stop();
        }
    }

    /**
     * @param appId:     yarn applicationId
     * @param jobId:     Flink JobId
     * @param flinkConf: flink配置文件
     * @description: 停止yarn 任务
     */
    public static String stopYarnJob(String defaultHdfs, String hadoopUserName, String appId, String jobId, String flinkConf) throws Exception {
        Configuration flinkConfig = ConfigUtil.getFlinkConf(defaultHdfs, hadoopUserName, flinkConf);
        flinkConfig.set(YarnConfigOptions.APPLICATION_ID, appId);
        String savepointDir = flinkConfig.getString(CheckpointingOptions.SAVEPOINT_DIRECTORY);
        if (StringUtils.isBlank(savepointDir)) {
            throw new FlinkException("state.savepoints.dir为空");
        } else {
            String separator = separator(savepointDir);
            savepointDir = savepointDir + separator + jobId;
        }
        YarnClusterClientFactory clusterClientFactory = new YarnClusterClientFactory();
        ApplicationId applicationId = clusterClientFactory.getClusterId(flinkConfig);
        if (applicationId == null) {
            throw new RuntimeException("No cluster id was specified. Please specify a cluster to which you would like to connect.");
        }

        YarnClusterDescriptor clusterDescriptor = initYarnClusterDescriptor(defaultHdfs, hadoopUserName, flinkConfig, flinkConf);
        ClusterClient<ApplicationId> clusterClient = clusterDescriptor.retrieve(applicationId).getClusterClient();
        JobID parseJobId = parseJobId(jobId);
        CompletableFuture<String> completableFuture = clusterClient.stopWithSavepoint(parseJobId, true, savepointDir, SavepointFormatType.DEFAULT);
        String savepointUrl = completableFuture.get();
        log.info("停止任务:{}", appId);
        log.info("savePoint 地址:{}", savepointUrl);
        return savepointUrl;
    }

    /**
     * @param appId:     yarn applicationId
     * @param jobId:     Flink JobId
     * @param flinkConf: flink配置文件
     * @description: 取消yarn 任务
     */
    public static void cancelYarnJob(String defaultHdfs, String hadoopUserName, String appId, String jobId, String flinkConf) throws Exception {
        Configuration flinkConfig = ConfigUtil.getFlinkConf(defaultHdfs, hadoopUserName, flinkConf);
        flinkConfig.set(YarnConfigOptions.APPLICATION_ID, appId);
        String savepointDir = flinkConfig.getString(CheckpointingOptions.SAVEPOINT_DIRECTORY);
        if (StringUtils.isBlank(savepointDir)) {
            throw new FlinkException("state.savepoints.dir为空");
        } else {
            String separator = separator(savepointDir);
            savepointDir = savepointDir + separator + jobId;
        }
        YarnClusterClientFactory clusterClientFactory = new YarnClusterClientFactory();
        ApplicationId applicationId = clusterClientFactory.getClusterId(flinkConfig);
        if (applicationId == null) {
            throw new RuntimeException("No cluster id was specified. Please specify a cluster to which you would like to connect.");
        }

        YarnClusterDescriptor clusterDescriptor = initYarnClusterDescriptor(defaultHdfs, hadoopUserName, flinkConfig, flinkConf);
        ClusterClient<ApplicationId> clusterClient = clusterDescriptor.retrieve(applicationId).getClusterClient();
        JobID parseJobId = parseJobId(jobId);
        CompletableFuture<Acknowledge> cancel = clusterClient.cancel(parseJobId);
        cancel.get();
        log.info("取消任务:{}", appId);
    }

    /**
     * log4j文件只能以file形式扔给flink
     */
    private static void settingLog(String defaultHdfs, String hadoopUserName, Configuration flinkConfig, String flinkConf) {
        final String log4jFileName = "log4j.properties";
        String hdfsLogFile = flinkConf + log4jFileName;
        try {
            // 下载文件到本地
            HdfsUtil.copyToLocalFile(defaultHdfs, hadoopUserName, hdfsLogFile, "../" + log4jFileName);
        } catch (IOException e) {
            throw new RuntimeException("下载log4j.properties失败");
        }
        flinkConfig.set(YarnConfigOptionsInternal.APPLICATION_LOG_CONFIG_FILE, "../" + log4jFileName);
    }

    /**
     * @param flinkConfig: flink configuration
     * @param yarnConfUrl: yarn config
     * @description: 初始化 YarnClusterDescriptor
     */
    private static YarnClusterDescriptor initYarnClusterDescriptor(String defaultHdfs, String hadoopUserName, Configuration flinkConfig, String yarnConfUrl) {
        YarnClient yarnClient = YarnClient.createYarnClient();
        YarnConfiguration yarnConf = new YarnConfiguration();
        ConfigUtil.initConfig(defaultHdfs, hadoopUserName, yarnConf, yarnConfUrl);
        yarnClient.init(yarnConf);
        yarnClient.start();
        return new YarnClusterDescriptor(flinkConfig, yarnConf, yarnClient, YarnClientYarnClusterInformationRetriever.create(yarnClient), false);
    }

    private static JobID parseJobId(String jobIdString) throws CliArgsException {
        if (jobIdString == null) {
            throw new CliArgsException("缺少JobId参数");
        }
        final JobID jobId;
        try {
            jobId = JobID.fromHexString(jobIdString);
        } catch (IllegalArgumentException e) {
            throw new CliArgsException(e.getMessage());
        }
        return jobId;
    }

    public static String separator(String path) {
        String separator = File.separator;
        if (path.endsWith(separator)) {
            separator = "";
        }
        return separator;
    }

    /**
     * 根据文件路径加载配置
     */
    public static Properties loadPropertiesByPath(String filePath) {
        Properties props = new Properties();
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(filePath.getBytes());
            props.load(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return props;
    }
}
