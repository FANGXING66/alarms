package com.example.myapplication.util;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * 节假日工具类
 * 
 * 功能说明：
 * 1. 调用外部 API 判断指定日期是否为法定节假日
 * 2. API 地址：http://api.haoshenqi.top/holiday?date=YYYY-MM-DD
 * 3. API 响应格式：{"date": "2019-05-01", "year": 2019, "month": 5, "day": 1, "status": 3}
 * 
 * status 含义：
 * - 0: 普通工作日
 * - 1: 周末双休日
 * - 2: 需要补班的工作日
 * - 3: 法定节假日（这是我们判断的目标）
 * 
 * 实现细节：
 * - 使用 HttpURLConnection 进行同步 HTTP GET 请求
 * - 连接超时和读取超时都设置为 3 秒
 * - 网络异常时返回 null，调用方会回退为 false（不跳过）
 * 
 * 注意：
 * - 当前实现在主线程执行，可能会阻塞 UI（建议改为后台线程）
 * - 网络失败时会返回 false，确保闹钟正常响铃（而不是因为网络问题跳过）
 */
public class HolidayUtil {
    /** 节假日 API 地址 */
    private static final String HOLIDAY_API = "http://api.haoshenqi.top/holiday";

    /**
     * 检查指定日期是否为法定节假日
     * 
     * @param calendar 要检查的日期（Calendar 对象）
     * @return true=是法定节假日，false=不是法定节假日（包括网络失败的情况）
     */
    public static boolean isHoliday(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String dateStr = String.format("%d-%02d-%02d", year, month, day);

        // 调用线上接口；失败时返回 false
        Boolean online = fetchHolidayFromApi(dateStr);
        return online != null && online;
    }

    /**
     * 检查指定日期是否为法定节假日（重载方法，使用年月日参数）
     * 
     * @param year 年份
     * @param month 月份（1-12，注意不是 Calendar.MONTH 的 0-11）
     * @param day 日期（1-31）
     * @return true=是法定节假日，false=不是法定节假日
     */
    public static boolean isHoliday(int year, int month, int day) {
        String dateStr = String.format("%d-%02d-%02d", year, month, day);
        Boolean online = fetchHolidayFromApi(dateStr);
        return online != null && online;
    }

    /**
     * 调用节假日 API 查询指定日期状态
     * 
     * @param dateStr 日期字符串，格式：YYYY-MM-DD（例如："2024-01-01"）
     * @return Boolean 对象：
     *         - true: 是法定节假日（status == 3）
     *         - false: 不是法定节假日（status == 0, 1, 2）
     *         - null: 网络请求失败或 JSON 解析失败
     * 
     * 实现细节：
     * 1. 构建完整的 API URL（添加 date 参数）
     * 2. 创建 HttpURLConnection，设置超时时间（连接和读取都是 3 秒）
     * 3. 发送 GET 请求
     * 4. 读取响应内容（JSON 格式）
     * 5. 解析 JSON，提取 status 字段
     * 6. 判断 status == 3 返回 true，否则返回 false
     * 7. 任何异常都返回 null，确保调用方可以安全处理
     */
    private static Boolean fetchHolidayFromApi(String dateStr) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(HOLIDAY_API + "?date=" + dateStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();

            // 解析 JSON，判断 status 是否为 3（法定节假日）
            JSONObject obj = new JSONObject(body);
            if (obj.has("status")) {
                int status = obj.optInt("status", -1);
                if (status == 3) return true;
                if (status == 0 || status == 1 || status == 2) return false;
            }
        } catch (Exception ignored) {
            // 网络异常时回退本地表
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {}
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}

