package com.phasetranscrystal.fpsmatch.mcgo.util;

import com.phasetranscrystal.fpsmatch.FPSMatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 哈希工具类
 * 主要用于处理SHA256哈希值的提取、计算和比较
 */
public class HashUtils {

    /**
     * 从URL中提取SHA256哈希值
     * 适用于类似 https://minio.mcgo.xin/music/mvp/d120c2746c3a4eedcba4884637de6589ceb60505e570d00d13147af0ec03e285.wav 的URL格式
     *
     * @param url 资源URL
     * @return 提取的SHA256哈希值，如果不符合格式则返回null
     */
    public static String extractSha256FromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // 获取URL的文件名部分
            String fileName = url.substring(url.lastIndexOf('/') + 1);

            // 提取不带扩展名的部分
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex);
            }

            // 验证是否符合SHA256格式（64个十六进制字符）
            if (fileName.matches("[0-9a-fA-F]{64}")) {
                return fileName;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("从URL中提取SHA256值时出错: {}", url, e);
        }

        return null;
    }

    /**
     * 计算文件的SHA256哈希值
     *
     * @param file 需要计算哈希值的文件
     * @return 文件的SHA256哈希值，如果计算失败则返回null
     */
    public static String calculateFileSha256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();

            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            FPSMatch.LOGGER.error("计算文件SHA256哈希值时出错: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 比较两个哈希值是否相同
     *
     * @param hash1 第一个哈希值
     * @param hash2 第二个哈希值
     * @return 如果两个哈希值相同返回true，否则返回false
     */
    public static boolean isSameHash(String hash1, String hash2) {
        return Objects.equals(hash1, hash2);
    }

    /**
     * 检查文件名是否是有效的SHA256哈希值
     *
     * @param fileName 文件名
     * @return 如果文件名是有效的SHA256哈希值返回true，否则返回false
     */
    public static boolean isValidSha256FileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        // 去掉扩展名
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }

        // 验证是否符合SHA256格式（64个十六进制字符）
        return fileName.matches("[0-9a-fA-F]{64}");
    }

    /**
     * 从文件路径中提取SHA256哈希值
     *
     * @param filePath 文件路径
     * @return 提取的SHA256哈希值，如果不符合格式则返回null
     */
    public static String extractSha256FromFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        try {
            // 获取文件名部分
            String fileName = new File(filePath).getName();

            // 提取不带扩展名的部分
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileName = fileName.substring(0, dotIndex);
            }

            // 验证是否符合SHA256格式（64个十六进制字符）
            if (fileName.matches("[0-9a-fA-F]{64}")) {
                return fileName;
            }
        } catch (Exception e) {
            FPSMatch.LOGGER.error("从文件路径中提取SHA256值时出错: {}", filePath, e);
        }

        return null;
    }

    /**
     * 验证文件哈希值是否与给定的哈希值匹配
     *
     * @param file 需要验证的文件
     * @param expectedHash 期望的哈希值
     * @return 如果文件哈希值与期望的哈希值匹配返回true，否则返回false
     */
    public static boolean verifyFileHash(File file, String expectedHash) {
        if (file == null || !file.exists() || expectedHash == null) {
            return false;
        }

        String actualHash = calculateFileSha256(file);
        return isSameHash(actualHash, expectedHash);
    }
}