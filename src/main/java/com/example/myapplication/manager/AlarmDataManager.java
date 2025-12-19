package com.example.myapplication.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.example.myapplication.model.Alarm;
import com.example.myapplication.receiver.AlarmReceiver;
import com.example.myapplication.util.HolidayUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 闹钟数据管理器
 * 
 * 核心职责：
 * 1. 管理闹钟数据的持久化存储（使用 SharedPreferences + Gson）
 * 2. 调度/取消系统闹钟（使用 AlarmManager）
 * 3. 实现节假日跳过逻辑（调用 HolidayUtil 查询API）
 * 
 * 数据存储：
 * - SharedPreferences 名称：alarm_prefs
 * - 存储格式：JSON字符串（通过Gson序列化List<Alarm>）
 * 
 * 系统闹钟调度：
 * - 使用 AlarmManager.setExactAndAllowWhileIdle() 确保精确触发（即使省电模式）
 * - PendingIntent 指向 AlarmReceiver，传递 alarm_id 标识
 * - 重复闹钟使用 setRepeating()，单次闹钟使用 setExact()
 */
public class AlarmDataManager {
    private static final String PREFS_NAME = "alarm_prefs";
    private static final String KEY_ALARMS = "alarms";
    
    private SharedPreferences prefs;
    private Gson gson;
    private Context context;
    private android.app.AlarmManager systemAlarmManager;

    /**
     * 构造函数
     * 初始化数据管理器，创建必要的对象和获取系统服务
     * 
     * @param context 上下文对象（通常是 Activity 或 Application）
     */
    public AlarmDataManager(Context context) {
        // 保存 Context 引用，用于后续访问 SharedPreferences 和系统服务
        this.context = context;
        // 获取 SharedPreferences 对象，用于持久化存储闹钟数据
        // PREFS_NAME：SharedPreferences 文件名（"alarm_prefs"）
        // Context.MODE_PRIVATE：私有模式，只有当前应用可以访问
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 创建 Gson 对象，用于将 Alarm 对象序列化为 JSON 字符串（存储）或反序列化（读取）
        gson = new Gson();
        // 获取系统的 AlarmManager 服务，用于调度系统级别的闹钟
        // Context.ALARM_SERVICE：系统服务类型（闹钟服务）
        // (android.app.AlarmManager)：强制类型转换
        systemAlarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * 添加新闹钟
     * 
     * 流程：
     * 1. 从 SharedPreferences 读取现有闹钟列表
     * 2. 添加新闹钟到列表
     * 3. 保存到 SharedPreferences
     * 4. 调度系统闹钟（如果闹钟是启用状态）
     */
    /**
     * 添加新闹钟
     * 
     * 流程：
     * 1. 从 SharedPreferences 读取现有闹钟列表
     * 2. 添加新闹钟到列表
     * 3. 保存到 SharedPreferences
     * 4. 调度系统闹钟（如果闹钟是启用状态）
     * 
     * @param alarm 要添加的闹钟对象
     */
    public void addAlarm(Alarm alarm) {
        // 从 SharedPreferences 读取所有现有闹钟
        List<Alarm> alarms = getAlarms();
        // 将新闹钟添加到列表末尾
        alarms.add(alarm);
        // 保存更新后的列表到 SharedPreferences（序列化为 JSON 字符串）
        saveAlarms(alarms);
        // 调度系统闹钟（注册到系统的 AlarmManager）
        // scheduleAlarm() 内部会检查 alarm.isEnabled()，如果禁用则不会调度
        scheduleAlarm(alarm);
    }

    /**
     * 更新现有闹钟
     * 
     * 流程：
     * 1. 从 SharedPreferences 读取所有闹钟
     * 2. 根据 ID 找到要更新的闹钟并替换
     * 3. 保存到 SharedPreferences
     * 4. 取消旧的系统闹钟
     * 5. 如果闹钟是启用状态，重新调度系统闹钟
     * 
     * @param alarm 要更新的闹钟对象（必须包含有效的 ID）
     */
    public void updateAlarm(Alarm alarm) {
        // 从 SharedPreferences 读取所有现有闹钟
        List<Alarm> alarms = getAlarms();
        // 遍历列表，查找 ID 匹配的闹钟
        for (int i = 0; i < alarms.size(); i++) {
            // 比较 ID，找到要更新的闹钟
            if (alarms.get(i).getId() == alarm.getId()) {
                // 使用新的 alarm 对象替换旧的对象
                alarms.set(i, alarm);
                // 找到后立即退出循环（提高效率）
                break;
            }
        }
        // 保存更新后的列表到 SharedPreferences
        saveAlarms(alarms);
        // 取消旧的系统闹钟（使用 alarm.getId() 作为 PendingIntent 的 requestCode）
        cancelAlarm(alarm);
        // 如果闹钟是启用状态，重新调度系统闹钟
        if (alarm.isEnabled()) {
            scheduleAlarm(alarm);
        }
        // 如果闹钟是禁用状态，只取消不重新调度
    }

    /**
     * 删除闹钟
     * 
     * 流程：
     * 1. 从 SharedPreferences 读取所有闹钟
     * 2. 根据 ID 从列表中移除闹钟
     * 3. 保存到 SharedPreferences
     * 4. 取消系统闹钟
     * 
     * @param alarm 要删除的闹钟对象（必须包含有效的 ID）
     */
    public void deleteAlarm(Alarm alarm) {
        // 从 SharedPreferences 读取所有现有闹钟
        List<Alarm> alarms = getAlarms();
        // 使用 removeIf() 方法移除 ID 匹配的闹钟
        // Lambda 表达式：a -> a.getId() == alarm.getId()
        // 对于列表中的每个 alarm a，如果 a 的 ID 等于要删除的 alarm 的 ID，则移除
        alarms.removeIf(a -> a.getId() == alarm.getId());
        // 保存更新后的列表到 SharedPreferences
        saveAlarms(alarms);
        // 取消系统闹钟（删除已注册的 PendingIntent）
        cancelAlarm(alarm);
    }

    /**
     * 获取所有闹钟
     * 
     * @return 闹钟列表（如果 SharedPreferences 中没有数据，返回空列表）
     */
    public List<Alarm> getAlarms() {
        // 从 SharedPreferences 读取 JSON 字符串
        // KEY_ALARMS：键名（"alarms"）
        // null：如果键不存在时返回的默认值
        String json = prefs.getString(KEY_ALARMS, null);
        // 如果 JSON 字符串为 null（表示没有保存过闹钟数据）
        if (json == null) {
            // 返回空列表（而不是 null，避免 NullPointerException）
            return new ArrayList<>();
        }
        // 创建 Type 对象，用于告诉 Gson 要反序列化的目标类型
        // TypeToken<List<Alarm>>(){}：匿名内部类，用于获取泛型类型信息
        // .getType()：获取 Type 对象
        Type type = new TypeToken<List<Alarm>>(){}.getType();
        // 使用 Gson 将 JSON 字符串反序列化为 List<Alarm> 对象
        return gson.fromJson(json, type);
    }

    /**
     * 切换闹钟启用/禁用状态
     * 
     * @param alarm 要切换的闹钟对象
     */
    public void toggleAlarm(Alarm alarm) {
        alarm.setEnabled(!alarm.isEnabled());
        updateAlarm(alarm);
    }

    /**
     * 保存闹钟列表到 SharedPreferences
     * 
     * 流程：
     * 1. 使用 Gson 将 List<Alarm> 序列化为 JSON 字符串
     * 2. 将 JSON 字符串保存到 SharedPreferences
     * 
     * @param alarms 要保存的闹钟列表
     */
    private void saveAlarms(List<Alarm> alarms) {
        // 使用 Gson 将 List<Alarm> 对象序列化为 JSON 字符串
        // 例如：[{"id":1234567890,"hour":8,"minute":0,"label":"起床",...}, {...}]
        String json = gson.toJson(alarms);
        // 保存 JSON 字符串到 SharedPreferences
        // prefs.edit()：获取 Editor 对象（用于编辑 SharedPreferences）
        // .putString(KEY_ALARMS, json)：将 JSON 字符串存入，键名为 KEY_ALARMS
        // .apply()：异步提交更改（不会阻塞主线程，适合频繁写入）
        // 注意：也可以使用 commit()，但 commit() 是同步的，会阻塞线程
        prefs.edit().putString(KEY_ALARMS, json).apply();
    }

    /**
     * 调度系统闹钟
     * 
     * 核心逻辑：
     * 1. 创建 PendingIntent 指向 AlarmReceiver，传递闹钟信息
     * 2. 计算触发时间（如果已过当前时间，设置为明天）
     * 3. 节假日跳过：如果启用跳过节假日，循环查找下一个非节假日（最多365天）
     * 4. 重复闹钟：使用 setRepeating()，每天重复
     * 5. 单次闹钟：使用 setExactAndAllowWhileIdle() 确保精确触发
     * 
     * 节假日跳过判断：
     * - 检查 alarm.isSkipHolidays()（单个闹钟设置）
     * - 或 global_skip_holidays（全局设置，从 settings_prefs 读取）
     * - 如果任一为 true，则调用 HolidayUtil.isHoliday() 查询API
     */
    /**
     * 调度系统闹钟
     * 
     * 核心逻辑：
     * 1. 创建 PendingIntent 指向 AlarmReceiver，传递闹钟信息
     * 2. 计算触发时间（如果已过当前时间，设置为明天）
     * 3. 节假日跳过：如果启用跳过节假日，循环查找下一个非节假日（最多365天）
     * 4. 重复闹钟：使用 setRepeating()，每天重复
     * 5. 单次闹钟：使用 setExactAndAllowWhileIdle() 确保精确触发
     * 
     * 节假日跳过判断：
     * - 检查 alarm.isSkipHolidays()（单个闹钟设置）
     * - 或 global_skip_holidays（全局设置，从 settings_prefs 读取）
     * - 如果任一为 true，则调用 HolidayUtil.isHoliday() 查询API
     * 
     * @param alarm 要调度的闹钟对象
     */
    private void scheduleAlarm(Alarm alarm) {
        // 如果闹钟是禁用状态，不进行调度，直接返回
        if (!alarm.isEnabled()) return;

        // 创建 Intent，用于指定当闹钟触发时要启动的 BroadcastReceiver
        // context：上下文对象
        // AlarmReceiver.class：目标接收器类（当闹钟触发时，系统会发送广播到这个接收器）
        Intent intent = new Intent(context, AlarmReceiver.class);
        // 将闹钟的各种信息放入 Intent 的额外数据中，以便 AlarmReceiver 接收
        // putExtra()：添加额外数据，键值对形式
        intent.putExtra("alarm_id", alarm.getId()); // 闹钟 ID（用于标识是哪个闹钟触发）
        intent.putExtra("alarm_label", alarm.getLabel()); // 闹钟标签（显示在响铃界面）
        intent.putExtra("ringtone_uri", alarm.getRingtoneUri()); // 铃声 URI（用于播放指定铃声）
        intent.putExtra("vibrate", alarm.isVibrate()); // 是否震动（true/false）
        intent.putExtra("volume", alarm.getVolume()); // 音量（0-100）
        intent.putExtra("hour", alarm.getHour()); // 小时（用于显示）
        intent.putExtra("minute", alarm.getMinute()); // 分钟（用于显示）

        // 创建 PendingIntent，这是系统闹钟需要的特殊 Intent 包装
        // PendingIntent.getBroadcast()：创建一个用于发送广播的 PendingIntent
        // context：上下文对象
        // alarm.getId()：requestCode（请求码），用于标识这个 PendingIntent
        //   每个闹钟使用不同的 ID，确保 PendingIntent 是唯一的
        // intent：要包装的 Intent 对象
        // PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE：标志位
        //   FLAG_UPDATE_CURRENT：如果已存在相同 requestCode 的 PendingIntent，则更新它的 Intent 数据
        //   FLAG_IMMUTABLE：标记为不可变（Android 12+ 要求，确保安全性）
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 创建 Calendar 对象，用于计算闹钟触发时间
        // Calendar.getInstance()：获取当前时间的 Calendar 对象
        Calendar calendar = Calendar.getInstance();
        // 统一先设置时间（小时和分钟）
        calendar.set(Calendar.HOUR_OF_DAY, alarm.getHour());
        calendar.set(Calendar.MINUTE, alarm.getMinute());
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 判断是否为重复闹钟（有任何重复星期）
        boolean[] repeatDays = alarm.getRepeatDays();
        boolean hasRepeat = false;
        if (repeatDays != null) {
            for (boolean day : repeatDays) {
                if (day) {
                    hasRepeat = true;
                    break;
                }
            }
        }

        // 如果是重复闹钟，找到下一个符合条件的日期
        if (hasRepeat) {
            // 判断是否为"法定工作日"模式（周一到周五都选中，周六周日未选中）
            boolean isWorkdayMode = repeatDays[0] && repeatDays[1] && repeatDays[2] 
                    && repeatDays[3] && repeatDays[4] 
                    && !repeatDays[5] && !repeatDays[6];
            
            // 判断是否为"节假日及周末"模式（周六周日都选中，周一到周五未选中）
            boolean isHolidayWeekendMode = !repeatDays[0] && !repeatDays[1] && !repeatDays[2] 
                    && !repeatDays[3] && !repeatDays[4] 
                    && repeatDays[5] && repeatDays[6];
            
            // 判断是否需要跳过节假日：
            // 1. 用户单独设置了"跳过节假日"选项（单个闹钟或全局设置）
            // 2. 或者选择了"法定工作日"模式（法定工作日本身就应该排除节假日）
            boolean globalSkipHolidays = context
                    .getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .getBoolean("global_skip_holidays", false);
            boolean needSkipHolidays = alarm.isSkipHolidays() || globalSkipHolidays || isWorkdayMode;
            
            // 如果当前时间已过，从明天开始查找
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            // 最多查找一年，防止无限循环
            int maxDays = 365;
            int checkedDays = 0;
            while (checkedDays < maxDays) {
                // 获取当前日期的星期（Calendar.DAY_OF_WEEK: 1=周日, 2=周一, ..., 7=周六）
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                // 转换为数组索引（0=周一, 1=周二, ..., 6=周日）
                int dayIndex = (dayOfWeek == Calendar.SUNDAY) ? 6 : (dayOfWeek - Calendar.MONDAY);
                
                // 检查当前日期是否符合星期要求
                boolean matchesDay = repeatDays[dayIndex];
                
                // 对于"节假日及周末"模式，除了检查星期，还要检查是否为法定节假日
                if (isHolidayWeekendMode) {
                    // 如果是周六或周日，或者通过API判断是法定节假日，都符合条件
                    boolean isHoliday = HolidayUtil.isHoliday(calendar);
                    if (matchesDay || isHoliday) {
                        // 找到符合条件的日期
                        break;
                    }
                } else {
                    // 其他模式：先检查是否符合星期要求
                    if (matchesDay) {
                        // 如果需要跳过节假日，检查是否为节假日
                        if (needSkipHolidays) {
                            boolean isHoliday = HolidayUtil.isHoliday(calendar);
                            // 如果不是节假日，找到目标日期
                            if (!isHoliday) {
                                break;
                            }
                        } else {
                            // 不需要跳过节假日，直接找到目标日期
                            break;
                        }
                    }
                }
                
                // 不符合条件，检查下一天
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                checkedDays++;
            }
            
            if (checkedDays >= maxDays) {
                android.util.Log.e("AlarmDataManager", "Failed to find next valid date for repeating alarm");
                return;
            }

            // 使用单次闹钟调度下一个符合条件的日期
            // 注意：闹钟触发后，AlarmReceiver 会重新调度下一个日期
            android.util.Log.d("AlarmDataManager", "Repeating alarm scheduled for: " + calendar.getTime().toString());
            // 继续执行下面的单次闹钟逻辑，使用 setExactAndAllowWhileIdle 调度
        }

        // ========== 单次闹钟逻辑 ==========
        // 判断是否设置了具体日期
        boolean hasSpecificDate = alarm.getYear() > 0 && alarm.getMonth() > 0 && alarm.getDay() > 0;
        
        if (hasSpecificDate) {
            // 如果设置了具体日期，使用该日期
            calendar.set(Calendar.YEAR, alarm.getYear());
            calendar.set(Calendar.MONTH, alarm.getMonth() - 1); // Calendar 月份从 0 开始
            calendar.set(Calendar.DAY_OF_MONTH, alarm.getDay());
            
            // 检查日期是否已过，如果已过则不调度（避免立即响铃）
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                android.util.Log.d("AlarmDataManager", "Alarm date has passed, skipping schedule: " + calendar.getTime().toString());
                return;
            }
            
            // 对于设置了具体日期的单次闹钟，不应用节假日跳过逻辑
            // 因为用户明确指定了日期，应该按照用户的选择执行
        } else {
            // 如果没有设置具体日期，使用"明天"逻辑
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            // ========== 节假日跳过逻辑（仅对未指定日期的单次闹钟生效）==========
            boolean globalSkipHolidays = context
                    .getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .getBoolean("global_skip_holidays", false);
            if (alarm.isSkipHolidays() || globalSkipHolidays) {
                int maxDays = 365;
                int checkedDays = 0;
                while (HolidayUtil.isHoliday(calendar) && checkedDays < maxDays) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    checkedDays++;
                }
            }
            // ====================================
        }
        // 如果不是重复闹钟，继续执行下面的单次闹钟逻辑

        // 单次闹钟 - 使用 setExactAndAllowWhileIdle 确保即使在省电模式下也能触发
        try {
            // 检查 Android 版本（API 23，Android 6.0）
            // setExactAndAllowWhileIdle() 需要 Android 6.0 以上版本
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // 使用 setExactAndAllowWhileIdle() 方法（推荐，最可靠）
                // 这个方法的优点：
                // 1. 精确触发（在指定时间精确触发，不延迟）
                // 2. 允许在省电模式下触发（即使系统处于深度休眠）
                // 3. 即使应用被杀死，闹钟仍然会触发
                systemAlarmManager.setExactAndAllowWhileIdle(
                        // AlarmManager.RTC_WAKEUP：使用 RTC（实时时钟），并在设备休眠时唤醒设备
                        android.app.AlarmManager.RTC_WAKEUP,
                        // calendar.getTimeInMillis()：触发时间（毫秒时间戳）
                        calendar.getTimeInMillis(),
                        // pendingIntent：要触发的 PendingIntent
                        pendingIntent
                );
            } else {
                // Android 6.0 以下版本，使用 setExact() 方法
                // setExact() 也能精确触发，但不支持省电模式
                systemAlarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
            // 记录日志，用于调试（开发时可以看到闹钟的调度时间）
            android.util.Log.d("AlarmDataManager", "Alarm scheduled for: " + calendar.getTime().toString());
        } catch (SecurityException e) {
            // 捕获安全异常（例如：用户拒绝了精确闹钟权限）
            android.util.Log.e("AlarmDataManager", "Failed to schedule alarm: " + e.getMessage());
            // 如果精确闹钟权限被拒绝，尝试使用不精确的闹钟作为降级方案
            // set()：不精确的闹钟方法（可能会延迟触发，但总比不触发好）
            systemAlarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }

    /**
     * 取消系统闹钟
     * 
     * 流程：
     * 1. 创建与调度时相同参数的 PendingIntent（requestCode 必须相同）
     * 2. 调用 AlarmManager.cancel() 取消闹钟
     * 
     * 注意：
     * - PendingIntent 的 requestCode 必须与调度时使用的相同（使用 alarm.getId()）
     * - 如果参数不匹配，无法取消对应的闹钟
     * 
     * @param alarm 要取消的闹钟对象
     */
    private void cancelAlarm(Alarm alarm) {
        // 创建 Intent（必须与调度时创建的 Intent 一致）
        Intent intent = new Intent(context, AlarmReceiver.class);
        // 创建 PendingIntent（必须与调度时的 requestCode 一致，才能正确取消）
        // alarm.getId()：使用相同的 requestCode（这是关键，必须与调度时一致）
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.getId(), // 使用相同的 requestCode，确保能匹配到要取消的闹钟
                intent,
                // 使用相同的标志位
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        // 取消系统闹钟
        // cancel()：取消与指定 PendingIntent 匹配的闹钟
        // 注意：如果有多个相同 requestCode 的闹钟，都会被打断
        systemAlarmManager.cancel(pendingIntent);
    }

    /**
     * 清空所有闹钟记录，并取消所有已设置的系统闹钟
     * 
     * 流程：
     * 1. 获取所有闹钟
     * 2. 遍历列表，逐个取消系统闹钟
     * 3. 保存空列表到 SharedPreferences（清空数据）
     * 
     * 使用场景：
     * - 用户在设置页面点击"清空数据"按钮时调用
     */
    public void clearAllAlarms() {
        // 从 SharedPreferences 读取所有现有闹钟
        List<Alarm> alarms = getAlarms();
        // 遍历所有闹钟
        for (Alarm alarm : alarms) {
            // 逐个取消系统闹钟（删除已注册的 PendingIntent）
            cancelAlarm(alarm);
        }
        // 保存空列表到 SharedPreferences（清空所有闹钟数据）
        // new ArrayList<>()：创建新的空列表
        saveAlarms(new ArrayList<>());
    }
}

