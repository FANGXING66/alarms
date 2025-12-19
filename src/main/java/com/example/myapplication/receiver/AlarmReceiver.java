package com.example.myapplication.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.util.Log;
import androidx.annotation.RequiresApi;

import com.example.myapplication.manager.AlarmDataManager;
import com.example.myapplication.model.Alarm;

/**
 * 闹钟广播接收器
 * 
 * 功能说明：
 * 1. 接收系统闹钟触发的广播（由 AlarmManager 发送）
 * 2. 安全检查：验证闹钟ID是否存在于本地数据（防止旧版本残留的系统闹钟）
 * 3. 播放铃声和震动
 * 4. 启动 AlarmRingingActivity 显示响铃界面
 * 
 * 铃声选择优先级：
 * 1. 闹钟自身设置的 ringtone_uri
 * 2. 设置中保存的默认铃声（从 settings_prefs 读取）
 * 3. 系统默认铃声（RingtoneManager.getDefaultUri）
 * 
 * 重要：在 AndroidManifest.xml 中注册，并设置 directBootAware="true" 以支持开机启动
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String SETTINGS_PREFS_NAME = "settings_prefs";
    private static final String KEY_DEFAULT_RINGTONE_URI = "default_ringtone_uri";
    private static MediaPlayer mediaPlayer;
    private static Ringtone ringtone;

    /**
     * 广播接收器的核心方法，当系统闹钟触发时会被调用
     * 
     * 流程：
     * 1. 从 Intent 中提取闹钟信息（ID、标签、铃声、震动、音量等）
     * 2. 安全检查：验证闹钟是否存在于本地数据（防止旧版本残留）
     * 3. 获取唤醒锁（WakeLock），确保设备唤醒
     * 4. 播放铃声和震动
     * 5. 启动 AlarmRingingActivity 显示响铃界面
     * 
     * @param context 上下文对象（BroadcastReceiver 的上下文）
     * @param intent 包含闹钟信息的 Intent（由 AlarmManager 传递）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 从 Intent 的额外数据中提取闹钟 ID
        // getIntExtra("alarm_id", -1)：获取键名为 "alarm_id" 的整数值
        // -1：如果键不存在时返回的默认值（-1 表示无效 ID）
        int alarmId = intent.getIntExtra("alarm_id", -1);
        // 获取闹钟标签/名称（用于在响铃界面显示）
        // getStringExtra()：获取字符串类型的额外数据，如果不存在返回 null
        String alarmLabel = intent.getStringExtra("alarm_label");
        // 获取铃声 URI（Content URI 字符串，用于播放指定铃声）
        String ringtoneUri = intent.getStringExtra("ringtone_uri");
        // 获取震动设置（true=震动，false=不震动）
        // getBooleanExtra("vibrate", true)：默认值为 true（震动）
        boolean vibrate = intent.getBooleanExtra("vibrate", true);
        // 获取音量设置（0-100，表示音量的百分比）
        // getIntExtra("volume", 80)：默认值为 80（80% 音量）
        int volume = intent.getIntExtra("volume", 80);

        // 记录日志，用于调试（开发时可以在 Logcat 中看到）
        // TAG：日志标签（"AlarmReceiver"）
        // Log.d()：调试级别日志
        Log.d(TAG, "Alarm received: " + alarmLabel);

        // ========== 安全检查：防止旧版本残留的系统闹钟 ==========
        // 如果闹钟已被删除（本地数据中没有），说明是旧版本残留的系统定时任务
        // 直接忽略本次响铃，避免误触发
        // 创建 AlarmDataManager 实例，用于访问本地数据
        AlarmDataManager alarmDataManager = new AlarmDataManager(context);
        // 用于存储找到的闹钟对象（如果存在）
        Alarm found = null;
        // 遍历所有本地存储的闹钟
        for (Alarm a : alarmDataManager.getAlarms()) {
            // 比较 ID，查找匹配的闹钟
            if (a.getId() == alarmId) {
                // 找到匹配的闹钟，保存到 found 变量
                found = a;
                // 找到后立即退出循环（提高效率）
                break;
            }
        }
        // 如果找不到匹配的闹钟（found == null），说明是旧版本残留的系统闹钟
        if (found == null) {
            // 记录警告日志
            // Log.w()：警告级别日志
            Log.w(TAG, "Alarm not found in local data, ignore ring. alarmId=" + alarmId);
            // 确保不播放任何声音（防御性措施）
            stopRingtone();
            // 直接返回，不执行后续的响铃逻辑
            return;
        }
        
        // 检查是否为重复闹钟，如果是，在响铃后重新调度下一个日期
        boolean[] repeatDays = found.getRepeatDays();
        boolean isRepeatingAlarm = false;
        if (repeatDays != null) {
            for (boolean day : repeatDays) {
                if (day) {
                    isRepeatingAlarm = true;
                    break;
                }
            }
        }
        // ====================================================

        // 获取唤醒锁，确保设备唤醒（保持唤醒直到Activity启动）
        // PowerManager：电源管理器，用于控制设备的电源状态
        // getSystemService(Context.POWER_SERVICE)：获取电源管理服务
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        // WakeLock：唤醒锁对象，用于防止设备进入休眠状态
        PowerManager.WakeLock wakeLock = null;
        // 如果 PowerManager 服务可用（不为 null）
        if (powerManager != null) {
            // 创建唤醒锁
            // newWakeLock()：创建新的唤醒锁
            // 参数1（标志位组合）：
            //   PowerManager.SCREEN_BRIGHT_WAKE_LOCK：保持屏幕亮度唤醒（已废弃，但保留兼容性）
            //   PowerManager.ACQUIRE_CAUSES_WAKEUP：获取锁时唤醒设备（即使屏幕是关闭的）
            //   PowerManager.FULL_WAKE_LOCK：完全唤醒锁（屏幕和键盘都点亮，已废弃，但保留兼容性）
            //   |：按位或运算符，组合多个标志位
            // 参数2（标签）：用于调试和日志识别（"AlarmReceiver::WakeLock"）
            wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK,
                    "AlarmReceiver::WakeLock");
            // acquire()：获取唤醒锁（防止设备休眠）
            // 300000：锁的持续时间（毫秒），300000 毫秒 = 5 分钟
            // 这个时间足够 AlarmRingingActivity 启动和用户交互
            wakeLock.acquire(300000);
        }

        // 使用 try-catch 捕获可能出现的异常，确保程序不会崩溃
        try {
            // 注意：不再强制设置音量，尊重用户的系统音量设置
            // 如果用户把系统闹钟音量设为0，闹钟将不会响（符合用户预期）

            // 播放铃声
            // playRingtone()：播放指定的铃声（私有方法）
            playRingtone(context, ringtoneUri, volume);

            // 震动
            // 如果震动设置为 true
            if (vibrate) {
                // 调用震动方法（私有方法）
                vibrate(context);
            }

            // 启动闹钟响铃界面（在播放铃声之后）
            // 创建 Intent，指定目标 Activity 为 AlarmRingingActivity
            Intent alarmIntent = new Intent(context, com.example.myapplication.AlarmRingingActivity.class);
            // 将闹钟的各种信息放入 Intent 的额外数据中，传递给 Activity
            alarmIntent.putExtra("alarm_id", alarmId); // 闹钟 ID
            alarmIntent.putExtra("alarm_label", alarmLabel); // 闹钟标签
            alarmIntent.putExtra("hour", intent.getIntExtra("hour", 8)); // 小时（默认 8）
            alarmIntent.putExtra("minute", intent.getIntExtra("minute", 0)); // 分钟（默认 0）
            alarmIntent.putExtra("ringtone_uri", ringtoneUri); // 传递铃声URI，以便Activity管理
            alarmIntent.putExtra("vibrate", vibrate); // 传递震动设置
            alarmIntent.putExtra("volume", volume); // 传递音量设置
            // 设置 Activity 启动标志位（组合使用）
            // FLAG_ACTIVITY_NEW_TASK：在新任务栈中启动 Activity（从后台启动 Activity 需要此标志）
            // FLAG_ACTIVITY_CLEAR_TASK：清除任务栈（启动新 Activity 前清除当前任务栈）
            // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS：不在最近任务列表中显示（用户按返回键不会看到）
            alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            // 启动 Activity（显示响铃界面）
            // context.startActivity()：从 BroadcastReceiver 启动 Activity 必须使用此方法
            context.startActivity(alarmIntent);
            
            // 如果是重复闹钟，重新调度下一个符合条件的日期
            if (isRepeatingAlarm && found.isEnabled()) {
                // 使用 AlarmDataManager 重新调度，它会自动计算下一个符合条件的日期
                alarmDataManager.updateAlarm(found);
                Log.d(TAG, "Repeating alarm rescheduled for next occurrence");
            }
        } catch (Exception e) {
            // 捕获任何异常（例如：启动 Activity 失败、播放铃声失败等）
            // Log.e()：错误级别日志
            Log.e(TAG, "Error starting alarm", e);
        } finally {
            // finally 块：无论是否发生异常都会执行
            // 注意：不在这里释放 WakeLock，让 Activity 自己管理，或者保持一段时间
            // Activity 会在用户操作后（点击关闭或稍后提醒按钮）释放 WakeLock
        }
    }

    /**
     * 播放闹钟铃声
     * 
     * 铃声选择策略（优先级从高到低）：
     * 1. 使用传入的 ringtoneUri（闹钟自身设置的铃声）
     * 2. 如果为空，调用 getDefaultRingtoneUri() 获取设置中的默认铃声
     * 3. 如果还没有，使用系统默认铃声
     * 
     * 播放方式：
     * - 优先使用 Ringtone（更简单可靠）
     * - 如果失败，回退到 MediaPlayer
     */
    /**
     * 播放闹钟铃声
     * 
     * 铃声选择优先级：
     * 1. 使用传入的 ringtoneUri（闹钟自身设置的铃声）
     * 2. 如果为空，调用 getDefaultRingtoneUri() 获取设置中的默认铃声
     * 3. 如果还没有，使用系统默认铃声
     * 
     * 播放方式：
     * - 优先使用 Ringtone（更简单可靠）
     * - 如果失败，回退到 MediaPlayer
     * 
     * @param context 上下文对象
     * @param ringtoneUri 铃声 URI 字符串（可能为 null 或空字符串）
     * @param volume 音量（0-100，百分比）
     */
    private void playRingtone(Context context, String ringtoneUri, int volume) {
        try {
            // Uri 对象（用于标识铃声资源）
            Uri uri;
            
            // 优先级1：使用闹钟自身设置的铃声
            // 如果 ringtoneUri 不为 null 且不为空字符串
            if (ringtoneUri != null && !ringtoneUri.isEmpty()) {
                // 将字符串 URI 转换为 Uri 对象
                // Uri.parse()：解析 URI 字符串，例如："content://media/internal/audio/media/123"
                uri = Uri.parse(ringtoneUri);
            } else {
                // 优先级2：使用设置中保存的默认铃声
                // getDefaultRingtoneUri()：获取用户在设置中保存的默认铃声，如果没有则返回系统默认铃声
                uri = getDefaultRingtoneUri(context);
            }

            // 记录日志，显示正在播放的铃声 URI
            Log.d(TAG, "Playing ringtone: " + uri.toString());

            // 方法1: 使用 Ringtone (推荐，更简单可靠)
            try {
                // RingtoneManager：铃声管理器，用于管理设备上的铃声
                // getRingtone()：根据 URI 获取 Ringtone 对象
                ringtone = RingtoneManager.getRingtone(context, uri);
                // 如果成功获取 Ringtone 对象（不为 null）
                if (ringtone != null) {
                    // 设置音频流类型为闹钟流
                    // STREAM_ALARM：闹钟音频流（即使设备处于静音模式，闹钟流仍然会播放）
                    ringtone.setStreamType(AudioManager.STREAM_ALARM);
                    // 开始播放铃声
                    ringtone.play();
                    // 记录日志，表示铃声已开始播放
                    Log.d(TAG, "Ringtone started");
                } else {
                    // 如果获取 Ringtone 失败（返回 null）
                    Log.e(TAG, "Failed to get ringtone");
                    // 如果 Ringtone 失败，尝试使用 MediaPlayer 作为备选方案
                    playWithMediaPlayer(context, uri, volume);
                }
            } catch (Exception e) {
                // 捕获播放 Ringtone 时的异常（例如：URI 无效、文件不存在等）
                Log.e(TAG, "Error playing ringtone", e);
                // 如果 Ringtone 失败，尝试使用 MediaPlayer 作为备选方案
                playWithMediaPlayer(context, uri, volume);
            }
        } catch (Exception e) {
            // 捕获其他异常（例如：URI 解析失败等）
            Log.e(TAG, "Error in playRingtone", e);
            // 打印异常堆栈跟踪（用于调试）
            e.printStackTrace();
        }
    }

    /**
     * 使用 MediaPlayer 播放铃声（备选方案）
     * 当 Ringtone 播放失败时，使用此方法作为降级方案
     * 
     * @param context 上下文对象
     * @param uri 铃声 URI
     * @param volume 音量（0-100，百分比）
     */
    private void playWithMediaPlayer(Context context, Uri uri, int volume) {
        try {
            // 停止之前的播放（如果 MediaPlayer 对象已存在）
            if (mediaPlayer != null) {
                try {
                    // stop()：停止播放
                    mediaPlayer.stop();
                    // release()：释放 MediaPlayer 占用的资源（非常重要，避免内存泄漏）
                    mediaPlayer.release();
                } catch (Exception e) {
                    // 捕获停止播放时的异常（例如：MediaPlayer 还未初始化）
                    Log.e(TAG, "Error stopping previous MediaPlayer", e);
                }
                // 将 mediaPlayer 设置为 null，表示已释放
                mediaPlayer = null;
            }

            // 创建新的 MediaPlayer 对象
            mediaPlayer = new MediaPlayer();
            // 设置音频数据源（URI）
            // setDataSource(context, uri)：从 URI 设置音频数据源
            mediaPlayer.setDataSource(context, uri);
            // 设置音频流类型为闹钟流
            // STREAM_ALARM：闹钟音频流（即使设备处于静音模式，闹钟流仍然会播放）
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            // 设置循环播放（true=循环，false=播放一次）
            mediaPlayer.setLooping(true);
            
            // 注意：不再强制设置系统音量，尊重用户的系统音量设置
            // MediaPlayer 的 setVolume() 设置的是相对音量（相对于系统音量的比例）
            // 如果系统音量为0，即使设置 setVolume(1.0f) 也不会有声音（符合用户预期）
            // 设置 MediaPlayer 的音量（相对音量，0.0f 到 1.0f）
            // volume / 100.0f：将百分比（0-100）转换为 0.0-1.0 的浮点数
            float volumeLevel = volume / 100.0f;
            // setVolume(leftVolume, rightVolume)：设置左右声道的音量
            // 这里左右声道使用相同的音量
            mediaPlayer.setVolume(volumeLevel, volumeLevel);
            
            // 准备 MediaPlayer（同步准备，会阻塞当前线程直到准备完成）
            mediaPlayer.prepare();
            // 开始播放
            mediaPlayer.start();
            // 记录日志，表示 MediaPlayer 已开始播放
            Log.d(TAG, "MediaPlayer started");
        } catch (Exception e) {
            // 捕获播放 MediaPlayer 时的异常（例如：文件不存在、格式不支持等）
            Log.e(TAG, "Error playing with MediaPlayer", e);
            // 打印异常堆栈跟踪（用于调试）
            e.printStackTrace();
        }
    }

    /**
     * 震动设备
     * 
     * 震动模式：
     * - 延迟 0 毫秒开始
     * - 震动 500 毫秒
     * - 停止 500 毫秒
     * - 震动 500 毫秒
     * - 停止 500 毫秒
     * - 震动 500 毫秒
     * - 停止 500 毫秒
     * - 震动 500 毫秒
     * - 停止 500 毫秒
     * - 震动 500 毫秒
     * 
     * @param context 上下文对象
     */
    private void vibrate(Context context) {
        // 获取震动器服务
        // Vibrator：震动器，用于控制设备的震动
        // getSystemService(Context.VIBRATOR_SERVICE)：获取震动器服务
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        // 检查震动器是否可用（不为 null 且设备支持震动）
        if (vibrator != null && vibrator.hasVibrator()) {
            // 检查 Android 版本（API 26，Android 8.0）
            // Android 8.0 以上使用新的震动 API（VibrationEffect）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 创建震动效果（新 API）
                // createWaveform()：创建波形震动效果
                // 参数1（时间模式数组）：
                //   new long[]{0, 500, 500, 500, 500, 500}
                //     0：延迟 0 毫秒开始
                //     500：震动 500 毫秒
                //     500：停止 500 毫秒（振幅为 0）
                //     500：震动 500 毫秒
                //     500：停止 500 毫秒
                //     500：震动 500 毫秒（最后的值表示震动时长）
                // 参数2（重复索引）：0 表示不重复（-1 表示无限重复）
                VibrationEffect effect = VibrationEffect.createWaveform(
                        new long[]{0, 500, 500, 500, 500, 500},
                        0
                );
                // 使用新的 API 震动
                vibrator.vibrate(effect);
            } else {
                // Android 8.0 以下使用旧的震动 API
                // vibrate(pattern, repeat)：
                //   pattern：时间模式数组（与上面的格式相同）
                //   repeat：重复索引（0 表示不重复，-1 表示无限重复）
                vibrator.vibrate(new long[]{0, 500, 500, 500, 500, 500}, 0);
            }
        }
    }

    /**
     * 获取用户在设置中保存的默认铃声URI，如果没有设置则返回系统默认铃声
     * 
     * 优先级：
     * 1. 用户在设置中保存的默认铃声（从 settings_prefs 读取）
     * 2. 系统默认铃声（RingtoneManager.getDefaultUri）
     * 
     * @param context 上下文对象
     * @return 铃声 URI 对象
     */
    private Uri getDefaultRingtoneUri(Context context) {
        // 获取设置相关的 SharedPreferences
        // SETTINGS_PREFS_NAME："settings_prefs"（设置数据的文件名）
        SharedPreferences prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE);
        // 从 SharedPreferences 读取默认铃声 URI 字符串
        // KEY_DEFAULT_RINGTONE_URI："default_ringtone_uri"（键名）
        // null：如果键不存在时返回的默认值
        String savedUri = prefs.getString(KEY_DEFAULT_RINGTONE_URI, null);
        // 如果保存的 URI 不为 null 且不为空字符串
        if (savedUri != null && !savedUri.isEmpty()) {
            try {
                // 将字符串 URI 转换为 Uri 对象并返回
                return Uri.parse(savedUri);
            } catch (Exception e) {
                // 捕获 URI 解析异常（例如：格式不正确）
                Log.e(TAG, "Error parsing default ringtone URI", e);
                // 如果解析失败，继续执行下面的代码，使用系统默认铃声
            }
        }
        // 如果用户没有设置默认铃声（savedUri 为 null 或空），使用系统默认铃声
        // RingtoneManager.getDefaultUri()：获取系统默认铃声的 URI
        // TYPE_ALARM：闹钟类型铃声（系统为闹钟提供的默认铃声）
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    /**
     * 停止铃声（可以从Activity调用）
     * 
     * 功能：
     * 1. 停止 Ringtone 播放（如果正在播放）
     * 2. 停止 MediaPlayer 播放（如果正在播放）并释放资源
     * 
     * 注意：
     * - 这是一个静态方法，可以从其他类调用（例如：AlarmRingingActivity）
     * - 需要同时检查 Ringtone 和 MediaPlayer，因为播放方式可能不同
     */
    public static void stopRingtone() {
        try {
            // 停止 Ringtone 播放
            // ringtone != null：检查 Ringtone 对象是否存在（可能使用了 MediaPlayer 而不是 Ringtone）
            // ringtone.isPlaying()：检查是否正在播放（避免在未播放时调用 stop()）
            if (ringtone != null && ringtone.isPlaying()) {
                // 停止播放
                ringtone.stop();
                // 将引用设置为 null，释放资源
                ringtone = null;
            }
            // 停止 MediaPlayer 播放
            // mediaPlayer != null：检查 MediaPlayer 对象是否存在
            // mediaPlayer.isPlaying()：检查是否正在播放
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                // 停止播放
                mediaPlayer.stop();
                // 释放 MediaPlayer 占用的资源（非常重要，避免内存泄漏）
                mediaPlayer.release();
                // 将引用设置为 null
                mediaPlayer = null;
            }
        } catch (Exception e) {
            // 捕获停止播放时的异常（例如：MediaPlayer 状态错误）
            Log.e(TAG, "Error stopping ringtone", e);
        }
    }
}

