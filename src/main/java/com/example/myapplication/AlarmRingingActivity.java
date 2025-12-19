package com.example.myapplication;

import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.manager.AlarmDataManager;
import com.example.myapplication.model.Alarm;
import com.example.myapplication.receiver.AlarmReceiver;
import com.google.android.material.button.MaterialButton;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.List;

/**
 * 闹钟响铃界面 Activity
 * 
 * 核心功能：
 * 1. 显示闹钟响铃界面（即使在锁屏状态下也能显示）
 * 2. 继续播放铃声和震动（AlarmReceiver 已经启动播放，这里负责持续播放）
 * 3. 处理用户交互：
 *    - "稍后提醒"：重新调度系统闹钟（N分钟后），如果启用报时则播报当前时间
 *    - "关闭"：停止铃声，可选删除闹钟（如果设置了 deleteAfterDismiss）
 * 
 * 特殊处理：
 * - 使用 WakeLock 保持设备唤醒
 * - 设置窗口标志确保在锁屏时也能显示（FLAG_SHOW_WHEN_LOCKED, FLAG_TURN_SCREEN_ON等）
 * - 禁用返回键，必须点击按钮才能关闭
 * - 安全检查：如果闹钟ID不存在，直接关闭（防止旧版本残留）
 * 
 * 稍后提醒逻辑：
 * - 读取全局设置（间隔时间、最大次数）
 * - 每个闹钟独立计数（使用 SharedPreferences，key = "snooze_count_" + alarmId）
 * - 如果启用报时（alarm.isSnoozeAnnounce()），使用 TextToSpeech 播报当前时间
 */
public class AlarmRingingActivity extends AppCompatActivity {
    private static final String TAG = "AlarmRingingActivity";
    private static final String SNOOZE_PREFS_NAME = "snooze_prefs";
    private static final String SETTINGS_PREFS_NAME = "settings_prefs";
    private static final String KEY_SNOOZE_ENABLED = "snooze_enabled";
    private static final String KEY_SNOOZE_INTERVAL = "snooze_interval";
    private static final String KEY_SNOOZE_COUNT = "snooze_count";
    private static final String KEY_DEFAULT_RINGTONE_URI = "default_ringtone_uri";
    private static final int DEFAULT_SNOOZE_INTERVAL = 10;
    private static final int DEFAULT_SNOOZE_COUNT = 5;
    
    private TextView tvAlarmTime;
    private TextView tvAlarmLabel;
    private MaterialButton btnSnooze;
    private MaterialButton btnDismiss;
    
    private int alarmId;
    private String alarmLabel;
    private int hour;
    private int minute;
    private Alarm alarm; // 保存alarm对象
    private AlarmDataManager alarmDataManager;
    private PowerManager.WakeLock wakeLock;
    private MediaPlayer mediaPlayer;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private SharedPreferences snoozePrefs;
    private SharedPreferences settingsPrefs;
    private TextToSpeech textToSpeech;

    /**
     * Activity 的 onCreate 生命周期方法，在 Activity 创建时调用
     * 
     * @param savedInstanceState 保存的实例状态（用于Activity重建时恢复数据）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类的 onCreate 方法，执行 Activity 的基础初始化
        super.onCreate(savedInstanceState);
        
        // 记录日志，表示 Activity 开始创建
        Log.d(TAG, "AlarmRingingActivity onCreate - start");
        
        // 使用 try-catch 捕获可能出现的异常，确保程序不会崩溃
        try {
            // 保持屏幕常亮和唤醒
            // getWindow()：获取当前 Activity 的窗口对象
            // addFlags()：添加窗口标志位（组合使用多个标志）
            // FLAG_SHOW_WHEN_LOCKED：在锁屏状态下也能显示 Activity（即使屏幕锁定）
            // FLAG_DISMISS_KEYGUARD：自动解除键盘锁（Android 5.0+ 已废弃，但保留兼容性）
            // FLAG_KEEP_SCREEN_ON：保持屏幕常亮（防止屏幕自动关闭）
            // FLAG_TURN_SCREEN_ON：唤醒屏幕（即使屏幕是关闭的，也会自动点亮）
            // |：按位或运算符，组合多个标志位
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            
            // 确保窗口完全可见（Android 5.0+）
            // Build.VERSION_CODES.LOLLIPOP：Android 5.0 (API 21)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // getDecorView()：获取窗口的根视图
                // setSystemUiVisibility()：设置系统 UI 的可见性
                // SYSTEM_UI_FLAG_LAYOUT_STABLE：保持布局稳定（避免系统栏变化时布局跳动）
                // SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN：允许内容延伸到状态栏下方（全屏显示）
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
            
            // 记录日志，表示窗口标志已设置
            Log.d(TAG, "Window flags set");
            
            // 获取并保持唤醒锁
            // PowerManager：电源管理器，用于控制设备的电源状态
            // getSystemService(POWER_SERVICE)：获取电源管理服务
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            // 如果 PowerManager 服务可用（不为 null）
            if (powerManager != null) {
                // 创建唤醒锁
                // newWakeLock()：创建新的唤醒锁
                // 参数1（标志位组合）：
                //   SCREEN_BRIGHT_WAKE_LOCK：保持屏幕亮度唤醒（已废弃，但保留兼容性）
                //   ACQUIRE_CAUSES_WAKEUP：获取锁时唤醒设备
                //   FULL_WAKE_LOCK：完全唤醒锁（已废弃，但保留兼容性）
                // 参数2（标签）："AlarmRingingActivity::WakeLock"（用于调试和日志识别）
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK,
                        "AlarmRingingActivity::WakeLock");
                // acquire()：获取唤醒锁（防止设备休眠）
                // 300000：锁的持续时间（毫秒），300000 毫秒 = 5 分钟
                // 这个时间足够用户操作（点击关闭或稍后提醒按钮）
                wakeLock.acquire(300000);
                // 记录日志，表示唤醒锁已获取
                Log.d(TAG, "WakeLock acquired");
            } else {
                // 如果 PowerManager 服务不可用（为 null），记录错误日志
                Log.e(TAG, "PowerManager is null!");
            }
            
            // 确保在设置contentView之前解锁keyguard（键盘锁）
            // KeyguardManager：键盘锁管理器，用于管理设备的键盘锁（锁屏）
            // getSystemService(KEYGUARD_SERVICE)：获取键盘锁管理服务
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            // 如果 KeyguardManager 服务可用，且 Android 版本 >= 8.0
            // Build.VERSION_CODES.O：Android 8.0 (API 26)
            if (keyguardManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // requestDismissKeyguard()：请求解除键盘锁
                // this：当前 Activity
                // null：回调对象（null 表示不需要回调）
                keyguardManager.requestDismissKeyguard(this, null);
            }
            
            // 设置 Activity 的布局文件（activity_alarm_ringing.xml）
            setContentView(R.layout.activity_alarm_ringing);
            // 记录日志，表示布局已设置
            Log.d(TAG, "ContentView set");

            // 获取启动此 Activity 的 Intent（包含闹钟信息）
            Intent intent = getIntent();
            // 如果 Intent 为 null（理论上不应该发生，但防御性编程）
            if (intent == null) {
                // 记录错误日志
                Log.e(TAG, "Intent is null!");
                // 关闭 Activity（因为没有数据无法正常工作）
                finish();
                // 直接返回，不执行后续代码
                return;
            }
            
            // 从 Intent 中提取闹钟 ID
            // getIntExtra("alarm_id", -1)：获取键名为 "alarm_id" 的整数值
            // -1：如果键不存在时返回的默认值（-1 表示无效 ID）
            alarmId = intent.getIntExtra("alarm_id", -1);

            // ========== 从本地数据读取完整的 Alarm 对象 ==========
            // 这样可以确保显示正确的时间，以及获取其他设置（如 deleteAfterDismiss, snoozeAnnounce）
            // 创建 AlarmDataManager 实例，用于访问本地数据
            alarmDataManager = new AlarmDataManager(this);
            // 初始化为 null（表示还未找到）
            alarm = null;
            // 如果 alarmId 有效（不为 -1）
            if (alarmId != -1) {
                // 从 SharedPreferences 读取所有闹钟
                List<Alarm> alarms = alarmDataManager.getAlarms();
                // 遍历所有闹钟，查找 ID 匹配的闹钟
                for (Alarm a : alarms) {
                    // 比较 ID
                    if (a.getId() == alarmId) {
                        // 找到匹配的闹钟，保存到 alarm 变量
                        alarm = a;
                        // 找到后立即退出循环（提高效率）
                        break;
                    }
                }
            }

            // 安全检查：如果找不到对应的闹钟记录，说明是旧版本残留的系统闹钟
            // 直接关闭 Activity，不显示响铃界面
            // 这样可以防止误触发已删除的闹钟
            if (alarm == null) {
                // 记录警告日志
                Log.w(TAG, "Alarm not found for id = " + alarmId + ", ignoring ring.");
                // 关闭 Activity（不显示响铃界面）
                finish();
                // 直接返回，不执行后续代码
                return;
            }
            // ====================================================

            // 从Alarm对象读取时间和标题
            // 从本地数据中读取，而不是从 Intent，确保数据是最新的
            hour = alarm.getHour(); // 获取小时（0-23）
            minute = alarm.getMinute(); // 获取分钟（0-59）
            alarmLabel = alarm.getLabel(); // 获取标签/名称

            // 记录日志，显示读取到的闹钟信息（用于调试）
            Log.d(TAG, "Intent extras read - alarmId: " + alarmId + ", hour: " + hour + ", minute: " + minute + ", label: " + alarmLabel);

            // 获取稍后提醒相关的 SharedPreferences
            // SNOOZE_PREFS_NAME："snooze_prefs"（存储稍后提醒次数的文件）
            snoozePrefs = getSharedPreferences(SNOOZE_PREFS_NAME, MODE_PRIVATE);
            // 获取设置相关的 SharedPreferences
            // SETTINGS_PREFS_NAME："settings_prefs"（存储全局设置的文件）
            settingsPrefs = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE);
            
            // 检查是否是稍后提醒，如果不是，重置稍后提醒计数（新的一天开始）
            // getBooleanExtra("is_snooze", false)：获取是否为稍后提醒的标志
            // false：默认值（如果键不存在，返回 false，表示是正常闹钟触发）
            boolean isSnooze = intent.getBooleanExtra("is_snooze", false);
            // 如果不是稍后提醒（是正常的闹钟触发）
            if (!isSnooze) {
                // 重置稍后提醒计数（因为这是新的一天的第一次响铃）
                // resetSnoozeCount()：清除该闹钟的稍后提醒计数
                resetSnoozeCount();
            }
            
            initViews();
            Log.d(TAG, "Views initialized");
            
            // 禁用返回键，必须点击按钮才能关闭
            // OnBackPressedCallback：返回键回调类（AndroidX 推荐的方式，替代已废弃的 onBackPressed()）
            // new OnBackPressedCallback(true)：创建回调对象，true 表示启用回调（禁用返回键）
            OnBackPressedCallback callback = new OnBackPressedCallback(true) {
                /**
                 * 处理返回键按下事件
                 */
                @Override
                public void handleOnBackPressed() {
                    // 什么都不做，禁用返回键
                    // 用户必须点击"关闭"或"稍后提醒"按钮才能关闭 Activity
                }
            };
            // 将回调添加到返回键调度器
            // getOnBackPressedDispatcher()：获取返回键调度器
            // addCallback(this, callback)：注册回调，this 表示绑定到当前 Activity 的生命周期
            getOnBackPressedDispatcher().addCallback(this, callback);
            
            // 初始化文字转语音（用于稍后提醒报时功能）
            initTextToSpeech();
            
            // 从 Intent 获取震动设置
            // getBooleanExtra("vibrate", true)：获取震动设置，默认值为 true（震动）
            boolean vibrate = intent.getBooleanExtra("vibrate", true);
            // 从 Intent 获取音量设置
            // getIntExtra("volume", 80)：获取音量设置，默认值为 80（80% 音量）
            int volume = intent.getIntExtra("volume", 80);
            
            // 播放铃声和震动
            // startAlarmSound()：播放铃声和震动的方法（私有方法）
            startAlarmSound(vibrate, volume);
            // 记录日志，表示铃声已开始播放
            Log.d(TAG, "Alarm sound started");
            
            Log.d(TAG, "AlarmRingingActivity onCreate - completed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            e.printStackTrace();
            // 即使出错也尝试保持Activity运行
            try {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Exception ex) {
                Log.e(TAG, "Error releasing wakeLock", ex);
            }
        }
    }
    
    /**
     * 播放闹钟铃声和震动
     * 
     * 铃声选择优先级：
     * 1. 使用 Intent 中传递的 ringtone_uri（闹钟自身设置的铃声）
     * 2. 如果为空，使用用户在设置中保存的默认铃声
     * 3. 如果还没有，使用系统默认铃声
     * 
     * 播放方式：
     * - 优先使用 Ringtone（更简单可靠）
     * - 如果失败，回退到 MediaPlayer
     * 
     * @param vibrate 是否震动（true=震动，false=不震动）
     * @param volume 音量（0-100，百分比）
     */
    private void startAlarmSound(boolean vibrate, int volume) {
        try {
            // 从 Intent 中获取铃声 URI 字符串
            // getIntent()：获取启动此 Activity 的 Intent
            // getStringExtra("ringtone_uri")：获取铃声 URI 字符串
            String ringtoneUri = getIntent().getStringExtra("ringtone_uri");
            // Uri 对象（用于标识铃声资源）
            Uri uri;
            // 如果 ringtoneUri 不为 null 且不为空字符串
            if (ringtoneUri != null && !ringtoneUri.isEmpty()) {
                // 将字符串 URI 转换为 Uri 对象
                uri = Uri.parse(ringtoneUri);
            } else {
                // 如果闹钟没有设置铃声，使用用户在设置中保存的默认铃声
                // getDefaultRingtoneUri()：获取默认铃声 URI（私有方法）
                uri = getDefaultRingtoneUri();
            }
            
            // 记录日志，显示正在播放的铃声 URI
            Log.d(TAG, "Starting alarm sound: " + uri);
            
            // 注意：不再强制设置音量，尊重用户的系统音量设置
            // 如果用户把系统闹钟音量设为0，闹钟将不会响（符合用户预期）
            
            // 先尝试使用 Ringtone（推荐方式，更简单可靠）
            try {
                // 根据 URI 获取 Ringtone 对象
                ringtone = RingtoneManager.getRingtone(this, uri);
                // 如果成功获取 Ringtone 对象（不为 null）
                if (ringtone != null) {
                    // 设置音频流类型为闹钟流
                    ringtone.setStreamType(AudioManager.STREAM_ALARM);
                    // 开始播放铃声
                    ringtone.play();
                    // 记录日志，表示 Ringtone 已开始播放
                    Log.d(TAG, "Ringtone started");
                }
            } catch (Exception e) {
                // 捕获播放 Ringtone 时的异常
                Log.e(TAG, "Error with Ringtone, trying MediaPlayer", e);
                // 将 ringtone 设置为 null，表示播放失败
                ringtone = null;
            }
            
            // 如果 Ringtone 失败，使用 MediaPlayer 作为备选方案
            // ringtone == null：Ringtone 获取失败
            // !ringtone.isPlaying()：Ringtone 没有在播放（可能播放失败）
            if (ringtone == null || !ringtone.isPlaying()) {
                try {
                    // 创建新的 MediaPlayer 对象
                    mediaPlayer = new MediaPlayer();
                    // 设置音频数据源（URI）
                    mediaPlayer.setDataSource(this, uri);
                    // 设置音频流类型为闹钟流
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                    // 设置循环播放（true=循环，false=播放一次）
                    mediaPlayer.setLooping(true);
                    // 设置音量（相对音量，0.0f 到 1.0f）
                    // volume / 100.0f：将百分比（0-100）转换为 0.0-1.0 的浮点数
                    // setVolume(leftVolume, rightVolume)：设置左右声道的音量（这里左右声道使用相同音量）
                    mediaPlayer.setVolume(volume / 100.0f, volume / 100.0f);
                    // 准备 MediaPlayer（同步准备）
                    mediaPlayer.prepare();
                    // 开始播放
                    mediaPlayer.start();
                    // 记录日志，表示 MediaPlayer 已开始播放
                    Log.d(TAG, "MediaPlayer started");
                } catch (Exception e) {
                    // 捕获播放 MediaPlayer 时的异常
                    Log.e(TAG, "Error with MediaPlayer", e);
                    // 打印异常堆栈跟踪（用于调试）
                    e.printStackTrace();
                }
            }
            
            // 震动
            // 如果震动设置为 true
            if (vibrate) {
                // 获取震动器服务
                vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                // 检查震动器是否可用（不为 null 且设备支持震动）
                if (vibrator != null && vibrator.hasVibrator()) {
                    // 检查 Android 版本（API 26，Android 8.0）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Android 8.0+ 使用新的震动 API
                        // createWaveform()：创建波形震动效果
                        // 参数1：时间模式数组 [0, 500, 500, 500, 500, 500]（延迟0ms，震动500ms，停止500ms，...）
                        // 参数2：重复索引（0=不重复）
                        VibrationEffect effect = VibrationEffect.createWaveform(
                                new long[]{0, 500, 500, 500, 500, 500},
                                0
                        );
                        // 使用新的 API 震动
                        vibrator.vibrate(effect);
                    } else {
                        // Android 8.0 以下使用旧的震动 API
                        // vibrate(pattern, repeat)：震动方法
                        vibrator.vibrate(new long[]{0, 500, 500, 500, 500, 500}, 0);
                    }
                }
            }
        } catch (Exception e) {
            // 捕获其他异常
            Log.e(TAG, "Error starting alarm sound", e);
            // 打印异常堆栈跟踪（用于调试）
            e.printStackTrace();
        }
    }
    
    /**
     * 停止闹钟铃声和震动
     * 
     * 功能：
     * 1. 停止 Ringtone 播放（如果正在播放）
     * 2. 停止 MediaPlayer 播放（如果正在播放）并释放资源
     * 3. 停止震动
     * 4. 停止 AlarmReceiver 中的铃声（如果还在播放）
     * 
     * 调用时机：
     * - 用户点击"关闭"按钮时
     * - 用户点击"稍后提醒"按钮时
     * - Activity 销毁时（onDestroy）
     */
    private void stopAlarmSound() {
        try {
            // 停止 Ringtone 播放
            // ringtone != null：检查 Ringtone 对象是否存在
            // ringtone.isPlaying()：检查是否正在播放（避免在未播放时调用 stop()）
            if (ringtone != null && ringtone.isPlaying()) {
                // 停止播放
                ringtone.stop();
                // 将引用设置为 null，释放资源
                ringtone = null;
            }
            // 停止 MediaPlayer 播放
            // mediaPlayer != null：检查 MediaPlayer 对象是否存在
            if (mediaPlayer != null) {
                // 如果正在播放
                if (mediaPlayer.isPlaying()) {
                    // 停止播放
                    mediaPlayer.stop();
                }
                // 释放 MediaPlayer 占用的资源（非常重要，避免内存泄漏）
                mediaPlayer.release();
                // 将引用设置为 null
                mediaPlayer = null;
            }
            // 停止震动
            // vibrator != null：检查 Vibrator 对象是否存在
            if (vibrator != null) {
                // cancel()：取消当前震动（如果正在震动）
                vibrator.cancel();
                // 将引用设置为 null
                vibrator = null;
            }
            // 也停止Receiver中的铃声
            // AlarmReceiver.stopRingtone()：静态方法，停止 AlarmReceiver 中可能正在播放的铃声
            // 因为 AlarmReceiver 在启动 Activity 之前就开始播放了，所以也需要停止
            AlarmReceiver.stopRingtone();
        } catch (Exception e) {
            // 捕获停止播放时的异常
            Log.e(TAG, "Error stopping alarm sound", e);
        }
    }

    /**
     * 初始化视图组件
     * 绑定布局文件中的控件，设置按钮的点击事件
     */
    private void initViews() {
        // 根据资源 ID 查找布局文件中的视图组件
        // R.id.tvAlarmTime：显示闹钟时间的 TextView 的 ID
        tvAlarmTime = findViewById(R.id.tvAlarmTime);
        // R.id.tvAlarmLabel：显示闹钟标签的 TextView 的 ID
        tvAlarmLabel = findViewById(R.id.tvAlarmLabel);
        // R.id.btnSnooze：稍后提醒按钮的 ID
        btnSnooze = findViewById(R.id.btnSnooze);
        // R.id.btnDismiss：关闭按钮的 ID
        btnDismiss = findViewById(R.id.btnDismiss);

        // 设置时间显示文本
        // String.format("%02d:%02d", hour, minute)：格式化时间为 "HH:mm" 格式
        // %02d：整数，2 位数字，不足 2 位时前面补 0
        // 例如：hour=8, minute=5 会显示 "08:05"
        tvAlarmTime.setText(String.format("%02d:%02d", hour, minute));
        // 设置标签显示文本
        // alarmLabel != null ? alarmLabel : "闹钟"：如果标签不为 null 则显示标签，否则显示默认文本"闹钟"
        // 三元运算符：condition ? valueIfTrue : valueIfFalse
        tvAlarmLabel.setText(alarmLabel != null ? alarmLabel : "闹钟");

        // ========== "关闭"按钮点击事件 ==========
        // setOnClickListener()：设置点击监听器
        // v -> {...}：Lambda 表达式，v 是点击的视图对象
        btnDismiss.setOnClickListener(v -> {
            // 1. 停止铃声和震动
            // stopAlarmSound()：停止当前正在播放的铃声和震动
            stopAlarmSound();
            // 2. 重置稍后提醒计数（因为用户主动关闭了闹钟）
            // resetSnoozeCount()：清除该闹钟的稍后提醒计数（下次响铃时重新开始计数）
            resetSnoozeCount();
            
            // 3. 如果闹钟设置了"关闭后删除"，则删除闹钟
            // alarm != null：检查 alarm 对象是否存在（防御性编程）
            // alarm.isDeleteAfterDismiss()：检查是否设置了"关闭后删除"选项
            if (alarm != null && alarm.isDeleteAfterDismiss()) {
                // 删除闹钟
                // deleteAlarm() 会：
                //   1. 从 SharedPreferences 中移除闹钟数据
                //   2. 取消系统闹钟（通过 AlarmManager.cancel()）
                alarmDataManager.deleteAlarm(alarm);
            }
            
            // 4. 释放唤醒锁
            // wakeLock != null：检查唤醒锁对象是否存在
            // wakeLock.isHeld()：检查唤醒锁是否正在持有（避免重复释放）
            if (wakeLock != null && wakeLock.isHeld()) {
                // release()：释放唤醒锁（允许设备进入休眠状态）
                wakeLock.release();
            }
            // 5. 关闭 Activity
            // finish()：关闭当前 Activity，返回到上一个界面（通常是主界面）
            finish();
        });
        // ======================================

        // 检查稍后提醒设置
        // 从 SharedPreferences 读取稍后提醒是否启用
        // getBoolean(KEY_SNOOZE_ENABLED, true)：读取键名为 "snooze_enabled" 的布尔值，默认值为 true（启用）
        boolean snoozeEnabled = settingsPrefs.getBoolean(KEY_SNOOZE_ENABLED, true);
        // 获取当前闹钟的稍后提醒次数（该闹钟已经稍后提醒了多少次）
        // getCurrentSnoozeCount()：从 SharedPreferences 读取，key = "snooze_count_" + alarmId
        int currentSnoozeCount = getCurrentSnoozeCount();
        // 获取最大稍后提醒次数（最多可以稍后提醒多少次）
        // getInt(KEY_SNOOZE_COUNT, DEFAULT_SNOOZE_COUNT)：读取键名为 "snooze_count" 的整数值，默认值为 5
        int maxSnoozeCount = settingsPrefs.getInt(KEY_SNOOZE_COUNT, DEFAULT_SNOOZE_COUNT);
        
        // 根据设置和次数限制决定是否显示稍后提醒按钮
        // !snoozeEnabled：如果稍后提醒功能被禁用
        // currentSnoozeCount >= maxSnoozeCount：如果已达到最大稍后提醒次数
        // ||：逻辑或运算符，任一条件满足则不显示按钮
        if (!snoozeEnabled || currentSnoozeCount >= maxSnoozeCount) {
            // 隐藏稍后提醒按钮（不显示）
            // View.GONE：完全隐藏，不占用布局空间
            btnSnooze.setVisibility(View.GONE);
            // 关闭按钮会自动占据全部宽度（因为 layout_weight=1）
        } else {
            // 显示稍后提醒按钮
            // View.VISIBLE：可见
            btnSnooze.setVisibility(View.VISIBLE);
            // ========== "稍后提醒"按钮点击事件 ==========
            // setOnClickListener()：设置点击监听器
            btnSnooze.setOnClickListener(v -> {
                // 1. 停止铃声和震动
                stopAlarmSound();
                
                // 2. 如果闹钟设置了"稍后提醒报时"，使用 TextToSpeech 播报当前时间
                // alarm != null：检查 alarm 对象是否存在
                // alarm.isSnoozeAnnounce()：检查是否启用了"稍后提醒报时"选项
                if (alarm != null && alarm.isSnoozeAnnounce()) {
                    // speakCurrentTime()：使用 TextToSpeech 播报当前时间（私有方法）
                    speakCurrentTime();
                }
                
                // 3. 增加稍后提醒计数（每个闹钟独立计数）
                // incrementSnoozeCount()：将当前闹钟的稍后提醒次数加 1
                incrementSnoozeCount();
                
                // 4. 重新调度系统闹钟（N分钟后再次触发）
                // snoozeAlarm()：创建新的系统闹钟，在设定的间隔时间（如 10 分钟）后再次触发
                snoozeAlarm();
                
                // 5. 延迟关闭 Activity，等待语音播报完成（如果启用了报时）
                // 如果启用了报时，延迟 2 秒关闭（给语音播报留出时间）
                // 如果没有启用报时，立即关闭（延迟 0 毫秒）
                int delay = (alarm != null && alarm.isSnoozeAnnounce()) ? 2000 : 0;
                // Handler：用于在指定时间后执行代码
                // new Handler(Looper.getMainLooper())：创建主线程的 Handler（必须在主线程执行 UI 操作）
                // postDelayed()：延迟执行 Runnable
                // () -> {...}：Lambda 表达式，延迟执行的代码块
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // 释放唤醒锁
                    if (wakeLock != null && wakeLock.isHeld()) {
                        wakeLock.release();
                    }
                    // 关闭 Activity
                    finish();
                }, delay); // delay：延迟时间（毫秒）
            });
            // ==========================================
        }
    }
    
    /**
     * 获取当前闹钟的稍后提醒次数
     * 
     * @return 稍后提醒次数（如果从未稍后提醒过，返回 0）
     */
    private int getCurrentSnoozeCount() {
        // 从 SharedPreferences 读取稍后提醒次数
        // "snooze_count_" + alarmId：键名（每个闹钟使用不同的键，例如："snooze_count_1234567890"）
        // 0：如果键不存在时返回的默认值（表示还未稍后提醒过）
        return snoozePrefs.getInt("snooze_count_" + alarmId, 0);
    }
    
    /**
     * 增加稍后提醒计数
     * 每次点击"稍后提醒"按钮时调用，将计数加 1
     */
    private void incrementSnoozeCount() {
        // 获取当前的稍后提醒次数
        int current = getCurrentSnoozeCount();
        // 将计数加 1 并保存到 SharedPreferences
        // edit()：获取 Editor 对象（用于编辑 SharedPreferences）
        // putInt("snooze_count_" + alarmId, current + 1)：将计数加 1 后存入
        // apply()：异步提交更改（不会阻塞主线程）
        snoozePrefs.edit().putInt("snooze_count_" + alarmId, current + 1).apply();
    }
    
    /**
     * 重置稍后提醒计数
     * 在正常闹钟触发（非稍后提醒）时调用，表示新的一天开始，重置计数
     */
    private void resetSnoozeCount() {
        // 从 SharedPreferences 中移除该闹钟的稍后提醒计数
        // remove("snooze_count_" + alarmId)：删除指定键的数据
        // apply()：异步提交更改
        snoozePrefs.edit().remove("snooze_count_" + alarmId).apply();
    }

    /**
     * 设置稍后提醒
     * 
     * 流程：
     * 1. 从全局设置读取提醒间隔（默认10分钟）
     * 2. 创建 Intent 指向 AlarmReceiver，传递闹钟信息
     * 3. 标记为稍后提醒（is_snooze = true）
     * 4. 使用 AlarmManager.setExactAndAllowWhileIdle() 调度系统闹钟（当前时间 + 间隔分钟）
     * 
     * 注意：
     * - PendingIntent 的 requestCode 使用 alarmId + 10000，避免与原始闹钟冲突
     * - 稍后提醒会携带原始闹钟的所有信息（铃声、震动、音量等）
     */
    /**
     * 设置稍后提醒
     * 重新调度系统闹钟，在指定的间隔时间（如 10 分钟）后再次触发
     * 
     * 流程：
     * 1. 从全局设置读取提醒间隔（默认10分钟）
     * 2. 创建 Intent 指向 AlarmReceiver，传递闹钟信息
     * 3. 标记为稍后提醒（is_snooze = true）
     * 4. 使用 AlarmManager.setExactAndAllowWhileIdle() 调度系统闹钟（当前时间 + 间隔分钟）
     * 
     * 注意：
     * - PendingIntent 的 requestCode 使用 alarmId + 10000，避免与原始闹钟冲突
     * - 稍后提醒会携带原始闹钟的所有信息（铃声、震动、音量等）
     */
    private void snoozeAlarm() {
        // 从全局设置读取提醒间隔（默认10分钟）
        // getInt(KEY_SNOOZE_INTERVAL, DEFAULT_SNOOZE_INTERVAL)：读取键名为 "snooze_interval" 的整数值
        // DEFAULT_SNOOZE_INTERVAL：默认值为 10（10分钟）
        int interval = settingsPrefs.getInt(KEY_SNOOZE_INTERVAL, DEFAULT_SNOOZE_INTERVAL);
        
        // 获取系统的 AlarmManager 服务
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        // 创建 Intent，用于指定当稍后提醒触发时要启动的 BroadcastReceiver
        Intent intent = new Intent(this, com.example.myapplication.receiver.AlarmReceiver.class);
        
        // 获取闹钟的完整信息以便稍后提醒时使用
        // 创建 AlarmDataManager 实例
        AlarmDataManager alarmDataManager = new AlarmDataManager(this);
        // 用于存储找到的闹钟对象
        Alarm alarm = null;
        // 从 SharedPreferences 读取所有闹钟
        List<Alarm> alarms = alarmDataManager.getAlarms();
        // 遍历所有闹钟，查找 ID 匹配的闹钟
        for (Alarm a : alarms) {
            if (a.getId() == alarmId) {
                // 找到匹配的闹钟
                alarm = a;
                // 找到后立即退出循环
                break;
            }
        }
        
        // 如果找到了闹钟对象（优先使用最新的数据）
        if (alarm != null) {
            // 将闹钟的完整信息放入 Intent 的额外数据中
            intent.putExtra("alarm_id", alarm.getId());
            intent.putExtra("alarm_label", alarm.getLabel());
            intent.putExtra("ringtone_uri", alarm.getRingtoneUri());
            intent.putExtra("vibrate", alarm.isVibrate());
            intent.putExtra("volume", alarm.getVolume());
            intent.putExtra("hour", alarm.getHour());
            intent.putExtra("minute", alarm.getMinute());
            intent.putExtra("is_snooze", true); // 标记为稍后提醒（用于区分正常触发和稍后提醒）
        } else {
            // 如果找不到闹钟对象（使用成员变量中的数据）
            intent.putExtra("alarm_id", alarmId);
            intent.putExtra("alarm_label", alarmLabel);
            intent.putExtra("hour", hour);
            intent.putExtra("minute", minute);
            intent.putExtra("is_snooze", true); // 标记为稍后提醒
        }
        
        // 创建 PendingIntent，这是系统闹钟需要的特殊 Intent 包装
        // getBroadcast()：创建一个用于发送广播的 PendingIntent
        // this：上下文对象
        // alarmId + 10000：requestCode（请求码），使用原始 alarmId + 10000 作为新的 ID
        //   这样可以避免与原始闹钟的 PendingIntent 冲突（原始闹钟使用 alarmId 作为 requestCode）
        // intent：要包装的 Intent 对象
        // FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE：标志位（与调度原始闹钟时相同）
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                alarmId + 10000, // 使用不同的ID避免冲突
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // 创建 Calendar 对象，用于计算稍后提醒的触发时间
        // Calendar.getInstance()：获取当前时间的 Calendar 对象
        Calendar calendar = Calendar.getInstance();
        // add(Calendar.MINUTE, interval)：将当前时间加上间隔分钟数
        // 例如：现在是 8:00，interval=10，则设置为 8:10
        calendar.add(Calendar.MINUTE, interval);
        
        // 如果 AlarmManager 服务可用（不为 null）
        if (alarmManager != null) {
            // 使用 setExactAndAllowWhileIdle() 调度精确闹钟（推荐方式）
            // 这个方法的优点：
            // 1. 精确触发（在指定时间精确触发，不延迟）
            // 2. 允许在省电模式下触发（即使系统处于深度休眠）
            // 3. 即使应用被杀死，闹钟仍然会触发
            alarmManager.setExactAndAllowWhileIdle(
                    // RTC_WAKEUP：使用 RTC（实时时钟），并在设备休眠时唤醒设备
                    android.app.AlarmManager.RTC_WAKEUP,
                    // getTimeInMillis()：获取 Calendar 对象对应的毫秒时间戳
                    calendar.getTimeInMillis(),
                    // pendingIntent：要触发的 PendingIntent
                    pendingIntent
            );
        }
    }

    /**
     * Activity 的 onPause 生命周期方法，在 Activity 暂停时调用
     * 即使Activity进入后台，也保持播放（不要停止）
     */
    @Override
    protected void onPause() {
        super.onPause();
        // 即使Activity进入后台，也保持播放（不要停止）
        Log.d(TAG, "onPause - keeping alarm active");
    }
    
    /**
     * Activity 的 onResume 生命周期方法，在 Activity 恢复时调用
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }
    
    /**
     * 初始化文字转语音（TextToSpeech）
     * 用于稍后提醒报时功能
     */
    private void initTextToSpeech() {
        // 创建 TextToSpeech 对象
        // this：上下文对象（Activity）
        // status -> {...}：初始化完成的回调（Lambda 表达式）
        // status：初始化结果（TextToSpeech.SUCCESS 表示成功）
        textToSpeech = new TextToSpeech(this, status -> {
            // 如果初始化成功
            if (status == TextToSpeech.SUCCESS) {
                // 设置语言为中文
                // setLanguage(Locale.CHINESE)：设置语音合成语言为中文
                // 返回结果码（LANG_AVAILABLE=可用，LANG_MISSING_DATA=缺少语言数据，LANG_NOT_SUPPORTED=不支持）
                int result = textToSpeech.setLanguage(java.util.Locale.CHINESE);
                // 如果中文不支持（缺少语言数据或不支持中文）
                // LANG_MISSING_DATA：语言数据缺失（用户可能没有安装中文语音包）
                // LANG_NOT_SUPPORTED：语言不支持
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 记录错误日志
                    Log.e(TAG, "Chinese language is not supported");
                    // 如果中文不支持，使用默认语言（系统语言）
                    // Locale.getDefault()：获取系统默认语言环境
                    textToSpeech.setLanguage(java.util.Locale.getDefault());
                }
            } else {
                // 如果初始化失败
                Log.e(TAG, "TextToSpeech initialization failed");
            }
        });
    }
    
    /**
     * 播报当前时间
     * 使用 TextToSpeech 将当前时间转换为语音并播放
     * 
     * 格式：
     * - 如果是整点（分钟为 0）："现在是X点整"
     * - 如果不是整点："现在是X点X分"
     * 
     * 调用时机：
     * - 用户点击"稍后提醒"按钮时，如果启用了"稍后提醒报时"选项
     */
    private void speakCurrentTime() {
        // 检查 TextToSpeech 对象是否存在且已初始化
        if (textToSpeech != null) {
            // 获取当前时间
            // Calendar.getInstance()：获取当前时间的 Calendar 对象
            Calendar calendar = Calendar.getInstance();
            // 获取当前小时（24小时制，0-23）
            int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
            // 获取当前分钟（0-59）
            int currentMinute = calendar.get(Calendar.MINUTE);
            
            // 格式化时间文本
            String timeText;
            // 如果是整点（分钟为 0）
            if (currentMinute == 0) {
                // 格式："现在是X点整"
                // 例如：8:00 会播报 "现在是8点整"
                timeText = String.format("现在是%d点整", currentHour);
            } else {
                // 如果不是整点，格式："现在是X点X分"
                // 例如：8:30 会播报 "现在是8点30分"
                timeText = String.format("现在是%d点%d分", currentHour, currentMinute);
            }
            
            // 使用 TextToSpeech 播报时间文本
            // speak()：将文本转换为语音并播放
            // timeText：要播报的文本
            // QUEUE_FLUSH：队列模式，QUEUE_FLUSH 表示清空当前队列并立即播放（如果正在播放其他内容会中断）
            // null：播放完成后的回调（null 表示不需要回调）
            // null：语音参数（null 表示使用默认参数）
            textToSpeech.speak(timeText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    
    /**
     * Activity 的 onDestroy 生命周期方法，在 Activity 销毁时调用
     * 用于清理资源，防止内存泄漏
     */
    @Override
    protected void onDestroy() {
        // 调用父类的 onDestroy 方法
        super.onDestroy();
        // 记录日志，表示 Activity 正在销毁
        Log.d(TAG, "onDestroy");
        // 停止所有声音和震动
        // stopAlarmSound()：停止铃声和震动，释放 MediaPlayer 和 Ringtone 资源
        stopAlarmSound();
        // 停止文字转语音
        // textToSpeech != null：检查 TextToSpeech 对象是否存在
        if (textToSpeech != null) {
            // stop()：停止当前正在播放的语音
            textToSpeech.stop();
            // shutdown()：关闭 TextToSpeech 引擎，释放资源
            textToSpeech.shutdown();
            // 将引用设置为 null
            textToSpeech = null;
        }
        // 释放唤醒锁
        // wakeLock != null：检查唤醒锁对象是否存在
        // wakeLock.isHeld()：检查唤醒锁是否正在持有（避免重复释放）
        if (wakeLock != null && wakeLock.isHeld()) {
            // release()：释放唤醒锁（允许设备进入休眠状态）
            wakeLock.release();
            // 将引用设置为 null
            wakeLock = null;
        }
    }
    
    /**
     * 获取用户在设置中保存的默认铃声URI，如果没有设置则返回系统默认铃声
     * 
     * 优先级：
     * 1. 用户在设置中保存的默认铃声（从 settings_prefs 读取）
     * 2. 系统默认铃声（RingtoneManager.getDefaultUri）
     * 
     * @return 铃声 URI 对象
     */
    private Uri getDefaultRingtoneUri() {
        // 从 SharedPreferences 读取默认铃声 URI 字符串
        // getString(KEY_DEFAULT_RINGTONE_URI, null)：读取键名为 "default_ringtone_uri" 的字符串值
        // null：如果键不存在时返回的默认值
        String savedUri = settingsPrefs.getString(KEY_DEFAULT_RINGTONE_URI, null);
        // 如果保存的 URI 不为 null 且不为空字符串
        if (savedUri != null && !savedUri.isEmpty()) {
            try {
                // 将字符串 URI 转换为 Uri 对象并返回
                return Uri.parse(savedUri);
            } catch (Exception e) {
                // 捕获 URI 解析异常（例如：格式不正确）
                android.util.Log.e(TAG, "Error parsing default ringtone URI", e);
                // 如果解析失败，继续执行下面的代码，使用系统默认铃声
            }
        }
        // 如果用户没有设置默认铃声（savedUri 为 null 或空），使用系统默认铃声
        // getDefaultUri(TYPE_ALARM)：获取系统为闹钟提供的默认铃声 URI
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }
    
    // 注意：onBackPressed() 已废弃，使用 OnBackPressedCallback 替代
    // 在 onCreate() 中使用 getOnBackPressedDispatcher().addCallback() 来禁用返回键
}

