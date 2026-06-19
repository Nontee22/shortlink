package com.shortlink.util;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User-Agent解析工具类
 */
public class UserAgentParser {

    // 设备类型正则
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(android|iphone|ipad|ipod|mobile|phone)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLET_PATTERN = Pattern.compile(
            "(tablet|ipad)", Pattern.CASE_INSENSITIVE);

    // 浏览器正则
    private static final Pattern CHROME_PATTERN = Pattern.compile("Chrome/([\\d.]+)");
    private static final Pattern FIREFOX_PATTERN = Pattern.compile("Firefox/([\\d.]+)");
    private static final Pattern SAFARI_PATTERN = Pattern.compile("Safari/([\\d.]+)");
    private static final Pattern EDGE_PATTERN = Pattern.compile("Edg/([\\d.]+)");
    private static final Pattern IE_PATTERN = Pattern.compile("MSIE ([\\d.]+)");
    private static final Pattern OPERA_PATTERN = Pattern.compile("OPR/([\\d.]+)");

    // 操作系统正则
    private static final Pattern WINDOWS_PATTERN = Pattern.compile("Windows NT ([\\d.]+)");
    private static final Pattern MAC_PATTERN = Pattern.compile("Mac OS X ([\\d_.]+)");
    private static final Pattern LINUX_PATTERN = Pattern.compile("Linux");
    private static final Pattern ANDROID_PATTERN = Pattern.compile("Android ([\\d.]+)");
    private static final Pattern IOS_PATTERN = Pattern.compile("iPhone OS ([\\d_]+)");
    private static final Pattern HARMONY_PATTERN = Pattern.compile("(OpenHarmony|HarmonyOS) ([\\d.]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 解析User-Agent
     * @param userAgent User-Agent字符串
     * @return 解析结果Map
     */
    public static Map<String, String> parse(String userAgent) {
        Map<String, String> result = new HashMap<>();
        
        if (StringUtils.isBlank(userAgent)) {
            result.put("deviceType", "Unknown");
            result.put("browser", "Unknown");
            result.put("os", "Unknown");
            return result;
        }

        // 解析设备类型
        result.put("deviceType", parseDeviceType(userAgent));
        
        // 解析浏览器
        result.put("browser", parseBrowser(userAgent));
        
        // 解析操作系统
        result.put("os", parseOS(userAgent));
        
        return result;
    }

    /**
     * 解析设备类型
     */
    private static String parseDeviceType(String userAgent) {
        if (TABLET_PATTERN.matcher(userAgent).find()) {
            return "Tablet";
        }
        if (MOBILE_PATTERN.matcher(userAgent).find()) {
            return "Mobile";
        }
        return "PC";
    }

    /**
     * 解析浏览器
     */
    private static String parseBrowser(String userAgent) {
        Matcher matcher;
        
        // Edge必须在Chrome之前判断，因为Edge UA也包含Chrome
        matcher = EDGE_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Edge " + matcher.group(1);
        }
        
        matcher = OPERA_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Opera " + matcher.group(1);
        }
        
        matcher = CHROME_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Chrome " + matcher.group(1);
        }
        
        matcher = FIREFOX_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Firefox " + matcher.group(1);
        }
        
        matcher = SAFARI_PATTERN.matcher(userAgent);
        if (matcher.find() && !userAgent.contains("Chrome")) {
            return "Safari " + matcher.group(1);
        }
        
        matcher = IE_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "IE " + matcher.group(1);
        }
        
        return "Unknown";
    }

    /**
     * 解析操作系统
     */
    private static String parseOS(String userAgent) {
        Matcher matcher;
        
        // 鸿蒙系统（OpenHarmony/HarmonyOS）
        matcher = HARMONY_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1) + " " + matcher.group(2);
        }
        
        matcher = ANDROID_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "Android " + matcher.group(1);
        }
        
        matcher = IOS_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "iOS " + matcher.group(1).replace("_", ".");
        }
        
        matcher = WINDOWS_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            String version = matcher.group(1);
            return "Windows " + getWindowsVersion(version);
        }
        
        matcher = MAC_PATTERN.matcher(userAgent);
        if (matcher.find()) {
            return "macOS " + matcher.group(1).replace("_", ".");
        }
        
        if (LINUX_PATTERN.matcher(userAgent).find()) {
            return "Linux";
        }
        
        return "Unknown";
    }

    /**
     * 获取Windows版本名称
     */
    private static String getWindowsVersion(String ntVersion) {
        return switch (ntVersion) {
            case "10.0" -> "10/11";
            case "6.3" -> "8.1";
            case "6.2" -> "8";
            case "6.1" -> "7";
            case "6.0" -> "Vista";
            case "5.1" -> "XP";
            default -> ntVersion;
        };
    }
}
