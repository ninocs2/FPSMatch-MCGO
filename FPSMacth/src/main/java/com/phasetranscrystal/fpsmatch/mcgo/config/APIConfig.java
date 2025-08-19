package com.phasetranscrystal.fpsmatch.mcgo.config;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.FPSMatch;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class APIConfig {
    private String apiEndpoint;
    private String saveMatch;
    private String weaponConfigure;
    private String apiAuthHeader;
    private String apiAuthValue;

    // 静态变量和方法
    private static APIConfig instance;
    private static final Gson gson = new Gson();
    private static final String CONFIG_FOLDER = "MCGO";
    private static final String CONFIG_FILE = "api_config.json";

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }

    public String getSaveMatch() {
        return saveMatch;
    }

    public void setSaveMatch(String saveMatch) {
        this.saveMatch = saveMatch;
    }

    public String getWeaponConfigure() {
        return weaponConfigure;
    }

    public void setWeaponConfigure(String weaponConfigure) {
        this.weaponConfigure = weaponConfigure;
    }

    public String getApiAuthHeader() {
        return apiAuthHeader;
    }

    public void setApiAuthHeader(String apiAuthHeader) {
        this.apiAuthHeader = apiAuthHeader;
    }

    public String getApiAuthValue() {
        return apiAuthValue;
    }

    public void setApiAuthValue(String apiAuthValue) {
        this.apiAuthValue = apiAuthValue;
    }

    /**
     * 获取API配置实例
     * @return API配置实例
     */
    public static APIConfig getInstance() {
        if (instance == null) {
            loadApiConfig();
        }
        return instance;
    }

    /**
     * 加载API配置
     * 如果配置文件不存在则创建默认配置
     */
    public static void loadApiConfig() {
        try {
            // 确保配置目录存在
            File configFolder = new File(CONFIG_FOLDER);
            if (!configFolder.exists()) {
                configFolder.mkdirs();
            }

            // 配置文件路径
            File configFile = new File(configFolder, CONFIG_FILE);

            // 如果配置文件不存在，创建默认配置
            if (!configFile.exists()) {
                APIConfig defaultConfig = new APIConfig();
                defaultConfig.setApiEndpoint("https://mygo.ninocs.com");
                defaultConfig.setSaveMatch("API地址");
                defaultConfig.setWeaponConfigure("API地址");
                defaultConfig.setApiAuthHeader("请求头"); // 必须配置认证头
                defaultConfig.setApiAuthValue("Token"); // 默认认证值，需要修改为正确的值

                // 写入默认配置
                try (FileWriter writer = new FileWriter(configFile)) {
                    gson.toJson(defaultConfig, writer);
                }

                FPSMatch.LOGGER.info("已创建默认API配置文件: {}", configFile.getAbsolutePath());
                FPSMatch.LOGGER.warn("请确保修改API配置文件中的认证头值，否则API请求将被拒绝(401)");
            }

            // 读取配置文件
            try (FileReader reader = new FileReader(configFile)) {
                instance = gson.fromJson(reader, APIConfig.class);

                // 检查认证头配置
                if (instance.getApiAuthHeader() == null || instance.getApiAuthHeader().isEmpty() ||
                        instance.getApiAuthValue() == null || instance.getApiAuthValue().isEmpty()) {
                    FPSMatch.LOGGER.error("API认证头配置不完整，请检查配置文件。必须设置apiAuthHeader和apiAuthValue!否则API请求将被拒绝(401)");
                }
            }

        } catch (Exception e) {
            FPSMatch.LOGGER.error("加载API配置失败: ", e);
        }
    }
}