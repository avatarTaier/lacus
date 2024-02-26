package com.lacus.common.constant;


/**
 * 通用常量信息
 */
public class Constants {

    public static final int KB = 1024;

    public static final int MB = KB * 1024;

    public static final int GB = MB * 1024;

    /**
     * http请求
     */
    public static final String HTTP = "http://";

    /**
     * https请求
     */
    public static final String HTTPS = "https://";


    public static class Token {
        /**
         * 令牌
         */
        public static final String TOKEN_FIELD = "token";

        /**
         * 令牌前缀
         */
        public static final String TOKEN_PREFIX = "Bearer ";

        /**
         * 令牌前缀
         */
        public static final String LOGIN_USER_KEY = "login_user_key";

    }

    public static class Captcha {
        /**
         * 令牌
         */
        public static final String MATH_TYPE = "math";

        /**
         * 令牌前缀
         */
        public static final String CHAR_TYPE = "char";

    }

    /**
     * 资源映射路径 前缀
     */
    public static final String RESOURCE_PREFIX = "profile";

    public static class UploadSubDir {

        public static final String IMPORT_PATH = "import";

        public static final String AVATAR_PATH = "avatar";

        public static final String DOWNLOAD_PATH = "download";

        public static final String UPLOAD_PATH = "upload";

    }

    public static final String DEFAULT_HDFS = "hdfs.defaultFS";
    public static final String HADOOP_USER_NAME = "HADOOP_USER_NAME";
    public static final String HADOOP_USER = "hdfs";
    public static final String HDFS_SITE_XML = "hdfs-site.xml";
    public static final String CORE_SITE_XML = "core-site.xml";
    public static final String YARN_SITE_XML = "yarn-site.xml";
    public static final String FLINK_CONF_YAML = "flink-conf.yaml";

}
