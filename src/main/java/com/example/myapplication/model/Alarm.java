package com.example.myapplication.model;

import java.io.Serializable;
import java.util.Calendar;

/**
 * 闹钟数据模型类
 * 
 * 功能说明：
 * 1. 存储单个闹钟的所有配置信息（时间、标签、重复设置、铃声等）
 * 2. 实现了 Serializable 接口，支持通过 Intent 传递
 * 3. 使用 Gson 序列化保存到 SharedPreferences
 * 
 * 关键设计：
 * - 默认时间使用当前时间（而非固定08:00），避免重装后出现默认闹钟
 * - repeatDays 数组长度为7，分别对应周一到周日
 */
public class Alarm implements Serializable {
    // 闹钟唯一标识符，使用时间戳生成（毫秒级时间戳转换为 int）
    private int id;
    // 闹钟的小时数（0-23，24小时制）
    private int hour;
    // 闹钟的分钟数（0-59）
    private int minute;
    // 闹钟标签/名称（用户自定义的名称，例如："起床"、"工作提醒"等）
    private String label;
    // 闹钟是否启用（true=启用，false=禁用）
    private boolean enabled;
    // 重复星期设置（周一到周日，7个布尔值）
    // 数组索引：0=周一，1=周二，2=周三，3=周四，4=周五，5=周六，6=周日
    // true 表示该天会响铃，false 表示该天不响铃
    private boolean[] repeatDays;
    // 铃声URI（Content URI 字符串，用于标识系统铃声或自定义音频文件）
    // 例如："content://media/internal/audio/media/123"
    private String ringtoneUri;
    // 铃声名称（用于界面显示，例如："默认铃声"、"天空之城"等）
    private String ringtoneName;
    // 是否震动（true=响铃时震动，false=只响铃不震动）
    private boolean vibrate;
    // 音量（0-100，表示音量的百分比）
    private int volume;
    // 是否跳过法定节假日（true=跳过节假日，false=不跳过）
    // 如果启用，在节假日当天不会响铃，会自动延迟到下一个工作日
    private boolean skipHolidays;
    // 是否为睡眠/起床闹钟（已废弃，保留字段用于兼容性）
    private boolean isSleepAlarm;
    // 关闭后是否删除此闹钟（true=关闭后删除，false=保留）
    // 如果启用，在 AlarmRingingActivity 点击"关闭"按钮时会删除此闹钟
    private boolean deleteAfterDismiss;
    // 稍后提醒时是否报时（true=报时，false=不报时）
    // 如果启用，在 AlarmRingingActivity 点击"稍后提醒"按钮时会使用 TextToSpeech 播报当前时间
    private boolean snoozeAnnounce;
    // 仅一次闹钟的具体日期（年、月、日）。
    // - year：完整年份，如 2024；为 0 表示未指定日期（使用“明天”等默认逻辑）。
    // - month：1-12（注意：不是 Calendar 的 0-11）。
    // - day：1-31。
    // 对于“每天 / 工作日 / 节假日及周末”等重复闹钟，此日期字段会被忽略。
    private int year;
    private int month;
    private int day;

    /**
     * 默认构造函数
     * 创建一个新的 Alarm 对象，所有字段使用默认值
     */
    public Alarm() {
        // 使用当前时间戳作为 ID（唯一标识符）
        // System.currentTimeMillis()：返回当前时间的毫秒数（long 类型）
        // (int)：强制转换为 int 类型（注意：可能会溢出，但对于实际使用足够）
        this.id = (int) System.currentTimeMillis();

        // 默认时间改为当前时间，避免固定 08:00 闹钟
        // 这样可以防止重装应用后出现默认的 08:00 闹钟
        // Calendar.getInstance()：获取当前时间的 Calendar 对象（使用系统默认时区）
        Calendar calendar = Calendar.getInstance();
        // get(Calendar.HOUR_OF_DAY)：获取小时（24小时制，0-23）
        this.hour = calendar.get(Calendar.HOUR_OF_DAY);
        // get(Calendar.MINUTE)：获取分钟（0-59）
        this.minute = calendar.get(Calendar.MINUTE);

        // 默认标签为"闹钟"（用户可以后续修改）
        this.label = "闹钟";
        // 默认启用状态为 true（新创建的闹钟默认是启用的）
        this.enabled = true;
        // 创建重复星期数组，长度为 7（周一到周日）
        // 默认所有值都是 false（表示不重复，仅响铃一次）
        this.repeatDays = new boolean[7];
        // 默认铃声 URI 为空字符串（表示使用默认铃声）
        this.ringtoneUri = "";
        // 默认铃声名称为"默认铃声"
        this.ringtoneName = "默认铃声";
        // 默认震动为 true（响铃时震动）
        this.vibrate = true;
        // 默认音量为 80%（100 表示最大音量）
        this.volume = 80;
        // 默认不跳过节假日（false）
        this.skipHolidays = false;
        // 默认不是睡眠闹钟（false）
        this.isSleepAlarm = false;
        // 默认关闭后不删除（false，保留闹钟）
        this.deleteAfterDismiss = false;
        // 默认稍后提醒时不报时（false）
        this.snoozeAnnounce = false;
        // 默认不指定具体日期（0 表示未设置）
        this.year = 0;
        this.month = 0;
        this.day = 0;
    }

    /**
     * 带参数的构造函数
     * 创建一个指定时间的闹钟对象
     * 
     * @param hour 小时（0-23）
     * @param minute 分钟（0-59）
     */
    public Alarm(int hour, int minute) {
        // 先调用默认构造函数，初始化所有字段为默认值
        this();
        // 然后使用传入的参数覆盖默认的小时和分钟
        this.hour = hour;
        this.minute = minute;
    }

    /**
     * 获取闹钟的唯一标识符
     * @return 闹钟 ID（时间戳）
     */
    public int getId() {
        return id;
    }

    /**
     * 设置闹钟的唯一标识符
     * @param id 闹钟 ID
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * 获取闹钟的小时数
     * @return 小时（0-23，24小时制）
     */
    public int getHour() {
        return hour;
    }

    /**
     * 设置闹钟的小时数
     * @param hour 小时（0-23）
     */
    public void setHour(int hour) {
        this.hour = hour;
    }

    /**
     * 获取闹钟的分钟数
     * @return 分钟（0-59）
     */
    public int getMinute() {
        return minute;
    }

    /**
     * 设置闹钟的分钟数
     * @param minute 分钟（0-59）
     */
    public void setMinute(int minute) {
        this.minute = minute;
    }

    /**
     * 获取闹钟标签/名称
     * @return 闹钟标签字符串
     */
    public String getLabel() {
        return label;
    }

    /**
     * 设置闹钟标签/名称
     * @param label 闹钟标签字符串
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * 获取闹钟是否启用
     * @return true=启用，false=禁用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置闹钟启用状态
     * @param enabled true=启用，false=禁用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取重复星期设置数组
     * @return 长度为7的布尔数组，索引0-6分别对应周一到周日
     */
    public boolean[] getRepeatDays() {
        return repeatDays;
    }

    /**
     * 设置重复星期数组
     * @param repeatDays 长度为7的布尔数组，索引0-6分别对应周一到周日
     */
    public void setRepeatDays(boolean[] repeatDays) {
        this.repeatDays = repeatDays;
    }

    /**
     * 获取铃声URI
     * @return 铃声URI字符串（Content URI格式）
     */
    public String getRingtoneUri() {
        return ringtoneUri;
    }

    /**
     * 设置铃声URI
     * @param ringtoneUri 铃声URI字符串（Content URI格式）
     */
    public void setRingtoneUri(String ringtoneUri) {
        this.ringtoneUri = ringtoneUri;
    }

    /**
     * 获取铃声名称
     * @return 铃声显示名称
     */
    public String getRingtoneName() {
        return ringtoneName;
    }

    /**
     * 设置铃声名称
     * @param ringtoneName 铃声显示名称
     */
    public void setRingtoneName(String ringtoneName) {
        this.ringtoneName = ringtoneName;
    }

    /**
     * 获取是否震动
     * @return true=震动，false=不震动
     */
    public boolean isVibrate() {
        return vibrate;
    }

    /**
     * 设置是否震动
     * @param vibrate true=震动，false=不震动
     */
    public void setVibrate(boolean vibrate) {
        this.vibrate = vibrate;
    }

    /**
     * 获取音量
     * @return 音量值（0-100，百分比）
     */
    public int getVolume() {
        return volume;
    }

    /**
     * 设置音量
     * @param volume 音量值（0-100，百分比）
     */
    public void setVolume(int volume) {
        this.volume = volume;
    }

    /**
     * 获取格式化后的时间字符串（HH:mm格式）
     * 例如：08:30
     * 
     * @return 格式化的时间字符串，例如："08:30"、"23:59"
     */
    public String getTimeString() {
        // String.format()：格式化字符串
        // "%02d:%02d"：格式化模式
        //   %02d：表示整数，2 位数字，不足 2 位时前面补 0
        //   :：字面量冒号分隔符
        // hour：小时（0-23）
        // minute：分钟（0-59）
        // 示例：hour=8, minute=5 返回 "08:05"
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * 获取重复设置的显示文本
     * 
     * 返回规则：
     * - 如果没有选择任何重复日：返回"仅一次"
     * - 如果选择了全部7天：返回"每天"
     * - 否则返回具体选择的星期，例如："周一 周二 周三"
     * 
     * @return 重复设置的显示文本
     */
    public String getRepeatString() {
        // 如果 repeatDays 为 null（防御性编程，理论上不应该发生），返回"仅一次"
        if (repeatDays == null) return "仅一次";
        
        // 统计选择了多少个重复日
        int count = 0;
        // 遍历 repeatDays 数组
        // for (boolean day : repeatDays)：增强型 for 循环，day 是数组中的每个元素
        for (boolean day : repeatDays) {
            // 如果该天被选中（day == true），计数器加 1
            if (day) count++;
        }
        
        // 如果没有任何一天被选中（count == 0），返回"仅一次"
        if (count == 0) return "仅一次";
        // 如果全部 7 天都被选中（count == 7），返回"每天"
        if (count == 7) return "每天";
        
        // 定义星期名称数组（对应周一到周日）
        // 数组索引对应 repeatDays 数组的索引：0=周一，1=周二，...，6=周日
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        // StringBuilder：用于高效拼接字符串（避免频繁创建新字符串对象）
        StringBuilder sb = new StringBuilder();
        // 遍历 repeatDays 数组（索引 0 到 6）
        for (int i = 0; i < 7; i++) {
            // 如果该天被选中
            if (repeatDays[i]) {
                // 如果 StringBuilder 中已经有内容（不是第一个被选中的天）
                // sb.length() > 0：检查长度是否大于 0
                if (sb.length() > 0) sb.append(" "); // 添加空格分隔符
                // 将对应的星期名称追加到 StringBuilder
                sb.append(dayNames[i]);
            }
        }
        // 将 StringBuilder 转换为字符串并返回
        // 例如：如果选择了周一、周二、周三，返回 "周一 周二 周三"
        return sb.toString();
    }

    /**
     * 获取是否跳过节假日
     * @return true=跳过节假日，false=不跳过
     */
    public boolean isSkipHolidays() {
        return skipHolidays;
    }

    /**
     * 设置是否跳过节假日
     * @param skipHolidays true=跳过节假日，false=不跳过
     */
    public void setSkipHolidays(boolean skipHolidays) {
        this.skipHolidays = skipHolidays;
    }

    /**
     * 获取是否为睡眠闹钟（已废弃）
     * @return true=是睡眠闹钟，false=不是
     */
    public boolean isSleepAlarm() {
        return isSleepAlarm;
    }

    /**
     * 设置是否为睡眠闹钟（已废弃）
     * @param sleepAlarm true=是睡眠闹钟，false=不是
     */
    public void setSleepAlarm(boolean sleepAlarm) {
        isSleepAlarm = sleepAlarm;
    }

    /**
     * 获取关闭后是否删除此闹钟
     * @return true=关闭后删除，false=保留
     */
    public boolean isDeleteAfterDismiss() {
        return deleteAfterDismiss;
    }

    /**
     * 设置关闭后是否删除此闹钟
     * @param deleteAfterDismiss true=关闭后删除，false=保留
     */
    public void setDeleteAfterDismiss(boolean deleteAfterDismiss) {
        this.deleteAfterDismiss = deleteAfterDismiss;
    }

    /**
     * 获取稍后提醒时是否报时
     * @return true=报时，false=不报时
     */
    public boolean isSnoozeAnnounce() {
        return snoozeAnnounce;
    }

    /**
     * 设置稍后提醒时是否报时
     * @param snoozeAnnounce true=报时，false=不报时
     */
    public void setSnoozeAnnounce(boolean snoozeAnnounce) {
        this.snoozeAnnounce = snoozeAnnounce;
    }

    // ========== 仅一次闹钟的日期字段访问方法 ==========

    /**
     * 获取闹钟的年份（例如：2024）。
     * 为 0 表示未指定具体日期。
     */
    public int getYear() {
        return year;
    }

    /**
     * 设置闹钟的年份（例如：2024）。
     * 传入 0 表示清除日期，让系统使用默认“明天”等逻辑。
     */
    public void setYear(int year) {
        this.year = year;
    }

    /**
     * 获取闹钟的月份（1-12）。
     * 为 0 表示未指定具体日期。
     */
    public int getMonth() {
        return month;
    }

    /**
     * 设置闹钟的月份（1-12）。
     * 注意：这里使用的是自然月份 1-12，而不是 Calendar 的 0-11。
     */
    public void setMonth(int month) {
        this.month = month;
    }

    /**
     * 获取闹钟的日期（1-31）。
     * 为 0 表示未指定具体日期。
     */
    public int getDay() {
        return day;
    }

    /**
     * 设置闹钟的日期（1-31）。
     */
    public void setDay(int day) {
        this.day = day;
    }
}

