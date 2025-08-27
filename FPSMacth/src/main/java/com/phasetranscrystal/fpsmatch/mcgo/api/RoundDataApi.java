package com.phasetranscrystal.fpsmatch.mcgo.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.phasetranscrystal.fpsmatch.core.data.RoundData;
import com.phasetranscrystal.fpsmatch.mcgo.config.APIConfig;
import com.phasetranscrystal.fpsmatch.mcgo.util.SSLUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 回合数据API服务类
 * 负责异步上传回合数据到后端服务器
 */
public class RoundDataApi {
    private static final Logger LOGGER = Logger.getLogger(RoundDataApi.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 使用SSLUtils创建支持SSL的HttpClient
    private static final CloseableHttpClient HTTP_CLIENT = SSLUtils.createTrustAllHttpClient();
    
    // 线程池用于异步处理API请求
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(2);
    
    // API配置
    private static boolean enableApiUpload = true;
    
    /**
     * 获取API端点
     * @return API端点地址
     */
    private static String getApiEndpoint() {
        APIConfig config = APIConfig.getInstance();
        String baseUrl = config.getApiEndpoint();
        String roundDataPath = config.getRoundData();
        
        if (baseUrl != null && roundDataPath != null) {
            return baseUrl + "/" + roundDataPath;
        }
        return null;
    }
    
    /**
     * 异步上传回合数据到API
     * @param roundData 回合数据
     * @return CompletableFuture<Boolean> 上传结果
     */
    public static CompletableFuture<Boolean> uploadRoundDataAsync(RoundData roundData) {
        if (!enableApiUpload) {
            LOGGER.info("Round data API upload is disabled, skipping upload");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return uploadRoundData(roundData);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to upload round data", e);
                return false;
            }
        }, EXECUTOR);
    }
    
    /**
     * 同步上传回合数据到API
     * @param roundData 回合数据
     * @return 是否上传成功
     * @throws IOException 网络异常
     */
    private static boolean uploadRoundData(RoundData roundData) throws IOException {
        if (roundData == null) {
            LOGGER.warning("Round data is null, cannot upload");
            return false;
        }
        
        CloseableHttpResponse response = null;
        try {
            // 将回合数据转换为JSON
            String jsonData = GSON.toJson(roundData);
            
            // 获取API配置
            APIConfig config = APIConfig.getInstance();
            String endpoint = getApiEndpoint();
            
            if (endpoint == null) {
                LOGGER.warning("API endpoint is not configured");
                return false;
            }
            
            // 创建HTTP POST请求
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("User-Agent", "FPSMatch-Game-Server/1.0");
            
            // 添加认证头（如果配置了）
            if (config.getApiAuthHeader() != null && !config.getApiAuthHeader().isEmpty() &&
                config.getApiAuthValue() != null && !config.getApiAuthValue().isEmpty()) {
                httpPost.setHeader(config.getApiAuthHeader(), config.getApiAuthValue());
            }
            
            // 设置请求体
            StringEntity entity = new StringEntity(jsonData, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);
            
            // 发送请求
            response = HTTP_CLIENT.execute(httpPost);
            
            // 检查响应状态
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                LOGGER.info(String.format("Successfully uploaded round data for round %d (Map: %s, Winner: %s)", 
                        roundData.getRoundNumber(), roundData.getMapName(), roundData.getWinnerTeam()));
                return true;
            } else {
                HttpEntity responseEntity = response.getEntity();
                String responseBody = responseEntity != null ? EntityUtils.toString(responseEntity) : "";
                LOGGER.warning(String.format("Failed to upload round data. Status: %d, Response: %s", 
                        statusCode, responseBody));
                return false;
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Network error while uploading round data: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error while uploading round data: " + e.getMessage(), e);
            return false;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error closing HTTP response", e);
                }
            }
        }
    }
    
    /**
     * 上传回合数据并处理结果
     * @param roundData 回合数据
     * @param onSuccess 成功回调
     * @param onFailure 失败回调
     */
    public static void uploadRoundDataWithCallback(RoundData roundData, 
                                                   Runnable onSuccess, 
                                                   Runnable onFailure) {
        uploadRoundDataAsync(roundData)
                .thenAccept(success -> {
                    if (success && onSuccess != null) {
                        onSuccess.run();
                    } else if (!success && onFailure != null) {
                        onFailure.run();
                    }
                })
                .exceptionally(throwable -> {
                    LOGGER.log(Level.SEVERE, "Exception in round data upload callback", throwable);
                    if (onFailure != null) {
                        onFailure.run();
                    }
                    return null;
                });
    }
}