package com.phasetranscrystal.fpsmatch.mcgo.util;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * SSL工具类，用于处理SSL证书验证相关的配置
 */
public class SSLUtils {
    
    /**
     * 初始化全局SSL配置，信任所有证书
     */
    public static void initGlobalSSL() {
        // 设置主机名验证器，信任所有主机名
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        try {
            // 创建SSL上下文并配置信任管理器
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{
                // 创建自定义的X509TrustManager，信任所有证书
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                }
            }, new SecureRandom());
            // 设置默认的SSL套接字工厂
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            FPSMatch.LOGGER.error("初始化SSL配置时发生错误", e);
        }
    }

    /**
     * 创建配置了SSL信任的HttpClient
     * @return 配置了SSL信任的HttpClient实例
     */
    public static CloseableHttpClient createTrustAllHttpClient() {
        try {
            // 创建SSL上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // 创建一个信任所有证书的TrustManager
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // 创建SSL连接工厂
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                sslContext,
                NoopHostnameVerifier.INSTANCE);
            
            // 注册SSL连接工厂
            return HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        } catch (Exception e) {
            FPSMatch.LOGGER.error("创建HttpClient时发生错误", e);
            return HttpClients.createDefault();
        }
    }
} 