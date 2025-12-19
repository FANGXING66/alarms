package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.manager.AlarmDataManager;
import com.example.myapplication.model.Alarm;

import java.util.Calendar;

/**
 * 编辑闹钟界面 Activity
 * 
 * 功能概述：
 * 1. 添加新闹钟或编辑现有闹钟
 * 2. 时间选择（使用 NumberPicker，小时0-23，分钟0-59）
 * 3. 重复设置（星期一到星期日，使用 Chip 组件）
 * 4. 日期选择（DatePickerDialog）
 * 5. 闹钟名称设置
 * 6. 提醒方式设置（铃声选择器 + 震动开关）
 * 7. 稍后提醒设置（跳转到 SnoozeSettingsActivity）
 * 8. 单个闹钟的特殊设置：
 *    - 跳过节假日开关（覆盖全局设置）
 *    - 关闭后删除此闹钟开关
 *    - 稍后提醒报时开关
 * 
 * 数据加载：
 * - 如果是编辑模式（alarm_id > 0），从 AlarmDataManager 加载现有闹钟数据
 * - 如果是新建模式（alarm_id <= 0），创建新的 Alarm 对象（默认时间为当前时间）
 * 
 * 数据保存：
 * - 点击"完成"按钮时，收集所有设置项
 * - 新建：调用 AlarmDataManager.addAlarm()
 * - 编辑：调用 AlarmDataManager.updateAlarm()
 * - 保存成功后返回 MainActivity，并刷新列表
 * 
 * Activity Result 处理：
 * - requestCode 100: 铃声选择器返回
 * - requestCode 300: 从稍后提醒设置页面返回（更新显示）
 */
public class EditAlarmActivity extends AppCompatActivity {
    private NumberPicker npHour;
    private NumberPicker npMinute;
    private TextView tvRepeatValue;
    private TextView tvDateValue;
    private TextView tvAlarmNameValue;
    private TextView tvReminderMethodValue;
    private TextView tvSnoozeValue;
    private TextView tvCancel;
    private TextView tvDone;
    
    private androidx.appcompat.widget.SwitchCompat switchDeleteAfterDismiss;
    private androidx.appcompat.widget.SwitchCompat switchSnoozeAnnounce;
    private androidx.appcompat.widget.SwitchCompat switchSkipHolidays;
    
    private Alarm alarm;
    private AlarmDataManager alarmDataManager;
    private boolean isNewAlarm;
    private TextView tvTitle;
    private static final String SETTINGS_PREFS_NAME = "settings_prefs";
    private static final String KEY_SNOOZE_ENABLED = "snooze_enabled";
    private static final String KEY_SNOOZE_INTERVAL = "snooze_interval";
    private static final String KEY_SNOOZE_COUNT = "snooze_count";
    private static final int DEFAULT_SNOOZE_INTERVAL = 10;
    private static final int DEFAULT_SNOOZE_COUNT = 5;
    private SharedPreferences settingsPrefs;

    // 仅一次闹钟时选择的具体日期（用于精确到某一天）
    private int selectedYear;
    private int selectedMonth; // 1-12
    private int selectedDay;   // 1-31

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_alarm);

        alarmDataManager = new AlarmDataManager(this);
        settingsPrefs = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE);
        
        Intent intent = getIntent();
        int alarmId = intent.getIntExtra("alarm_id", -1);
        boolean isSleepAlarm = intent.getBooleanExtra("is_sleep_alarm", false);
        
        if (alarmId > 0) {
            // 编辑现有闹钟
            for (Alarm a : alarmDataManager.getAlarms()) {
                if (a.getId() == alarmId) {
                    alarm = a;
                    break;
                }
            }
            isNewAlarm = false;
        } else {
            // 新建闹钟
            alarm = new Alarm();
            alarm.setSleepAlarm(isSleepAlarm);
            isNewAlarm = true;
        }

        initViews();
        setupTimePicker();
        loadAlarmData();
    }

    /**
     * 初始化视图组件并设置点击事件
     */
    private void initViews() {
        npHour = findViewById(R.id.npHour);
        npMinute = findViewById(R.id.npMinute);
        tvRepeatValue = findViewById(R.id.tvRepeatValue);
        tvDateValue = findViewById(R.id.tvDateValue);
        tvAlarmNameValue = findViewById(R.id.tvAlarmNameValue);
        tvReminderMethodValue = findViewById(R.id.tvReminderMethodValue);
        tvSnoozeValue = findViewById(R.id.tvSnoozeValue);
        tvCancel = findViewById(R.id.tvCancel);
        tvDone = findViewById(R.id.tvDone);
        switchDeleteAfterDismiss = findViewById(R.id.switchDeleteAfterDismiss);
        switchSnoozeAnnounce = findViewById(R.id.switchSnoozeAnnounce);
        switchSkipHolidays = findViewById(R.id.switchSkipHolidays);
        
        // 设置标题
        tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setText(isNewAlarm ? getString(R.string.add_alarm) : getString(R.string.edit_alarm));
        }

        tvCancel.setOnClickListener(v -> finish());
        tvDone.setOnClickListener(v -> saveAlarm());

        // 设置所有开关的颜色：使用颜色选择器，开启=橙色，关闭=灰色
        android.content.res.ColorStateList thumbColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_thumb);
        android.content.res.ColorStateList trackColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_track);
        switchDeleteAfterDismiss.setThumbTintList(thumbColor);
        switchDeleteAfterDismiss.setTrackTintList(trackColor);
        switchSnoozeAnnounce.setThumbTintList(thumbColor);
        switchSnoozeAnnounce.setTrackTintList(trackColor);
        switchSkipHolidays.setThumbTintList(thumbColor);
        switchSkipHolidays.setTrackTintList(trackColor);

        findViewById(R.id.llRepeat).setOnClickListener(v -> showRepeatDialog());
        findViewById(R.id.llDate).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.llAlarmName).setOnClickListener(v -> showAlarmNameDialog());
        findViewById(R.id.llReminderMethod).setOnClickListener(v -> showReminderMethodDialog());
        findViewById(R.id.llSnooze).setOnClickListener(v -> showSnoozeDialog());
    }

    /**
     * 设置时间选择器（NumberPicker）
     * 
     * 配置：
     * - 小时：0-23，显示格式为 "02时"
     * - 分钟：0-59，显示格式为 "30分"
     * - 初始值：使用 alarm 对象的 hour 和 minute
     */
    private void setupTimePicker() {
        npHour.setMinValue(0);
        npHour.setMaxValue(23);
        npHour.setValue(alarm.getHour());
        
        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setValue(alarm.getMinute());
        
        // 格式化显示：添加"时"和"分"单位
        npHour.setFormatter(value -> String.format("%02d时", value));
        npMinute.setFormatter(value -> String.format("%02d分", value));
    }

    /**
     * 加载闹钟数据并显示到界面
     */
    private void loadAlarmData() {
        npHour.setValue(alarm.getHour());
        npMinute.setValue(alarm.getMinute());
        
        tvRepeatValue.setText(alarm.getRepeatString());
        // 根据重复模式控制日期是否可编辑
        updateDateEnabledByRepeat();
        
        // 计算并初始化日期显示与 selectedYear/Month/Day
        Calendar calendar = Calendar.getInstance();
        if (alarm.getYear() > 0 && alarm.getMonth() > 0 && alarm.getDay() > 0) {
            // 已经为该闹钟保存过具体日期
            calendar.set(Calendar.YEAR, alarm.getYear());
            calendar.set(Calendar.MONTH, alarm.getMonth() - 1); // Calendar 月份从 0 开始
            calendar.set(Calendar.DAY_OF_MONTH, alarm.getDay());
        } else {
            // 没有保存过具体日期：按照原先逻辑，默认设置为“今天/明天”
            calendar.set(Calendar.HOUR_OF_DAY, alarm.getHour());
            calendar.set(Calendar.MINUTE, alarm.getMinute());
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        // 记录到成员变量，供日期选择器和保存逻辑使用
        selectedYear = calendar.get(Calendar.YEAR);
        selectedMonth = calendar.get(Calendar.MONTH) + 1; // 转换为 1-12
        selectedDay = calendar.get(Calendar.DAY_OF_MONTH);

        updateDateDisplay(calendar);
        
        if (alarm.getLabel() != null && !alarm.getLabel().isEmpty() && !alarm.getLabel().equals("闹钟")) {
            tvAlarmNameValue.setText(alarm.getLabel());
            tvAlarmNameValue.setVisibility(View.VISIBLE);
        }
        
        // 提醒方式
        tvReminderMethodValue.setText(alarm.isVibrate() 
            ? getString(R.string.ring_and_vibrate) 
            : getString(R.string.ring_only));
        
        // 稍后提醒设置
        updateSnoozeDisplay();
        
        // 跳过节假日设置
        switchSkipHolidays.setChecked(alarm.isSkipHolidays());
        
        // 关闭后删除此闹钟设置
        switchDeleteAfterDismiss.setChecked(alarm.isDeleteAfterDismiss());
        
        // 稍后提醒报时设置
        switchSnoozeAnnounce.setChecked(alarm.isSnoozeAnnounce());
    }
    
    /**
     * 更新稍后提醒设置显示文本
     */
    private void updateSnoozeDisplay() {
        boolean snoozeEnabled = settingsPrefs.getBoolean(KEY_SNOOZE_ENABLED, true);
        int interval = settingsPrefs.getInt(KEY_SNOOZE_INTERVAL, DEFAULT_SNOOZE_INTERVAL);
        int count = settingsPrefs.getInt(KEY_SNOOZE_COUNT, DEFAULT_SNOOZE_COUNT);
        
        if (snoozeEnabled) {
            tvSnoozeValue.setText(String.format("%d分钟,%d次", interval, count));
        } else {
            tvSnoozeValue.setText(getString(R.string.snooze_disabled));
        }
    }

    /**
     * 更新日期显示文本
     * 
     * @param calendar 目标日期
     * 
     * 显示规则：
     * - 今天：显示"今天"
     * - 明天：显示"明天"
     * - 后天：显示"后天"
     * - 其他日期：显示"X月X日"格式
     * 
     * 实现：
     * 1. 计算目标日期和今天的差值（天数）
     * 2. 根据差值选择显示文本
     * 3. 注意：Calendar.MONTH 从 0 开始，需要 +1
     */
    private void updateDateDisplay(Calendar calendar) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        
        Calendar target = (Calendar) calendar.clone();
        target.set(Calendar.HOUR_OF_DAY, 0);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        
        long diff = target.getTimeInMillis() - today.getTimeInMillis();
        int days = (int) (diff / (24 * 60 * 60 * 1000));
        
        if (days == 0) {
            tvDateValue.setText(getString(R.string.today));
        } else if (days == 1) {
            tvDateValue.setText(getString(R.string.tomorrow));
        } else if (days == 2) {
            tvDateValue.setText(getString(R.string.day_after_tomorrow));
        } else {
            tvDateValue.setText(String.format("%d月%d日", 
                calendar.get(Calendar.MONTH) + 1, 
                calendar.get(Calendar.DAY_OF_MONTH)));
        }
    }

    /**
     * 显示重复模式选择对话框
     *
     * UI 采用四个单选项，类似系统闹钟：
     * - 仅一次：不勾选任何星期
     * - 每天：周一到周日全部勾选
     * - 法定工作日：周一到周五勾选
     * - 节假日及周末：周六周日勾选（法定节假日由 HolidayUtil 判定）
     *
     * 选择完成后会更新 Alarm.repeatDays，并根据结果控制“日期”行是否可点。
     */
    private void showRepeatDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_repeat_mode, null);

        android.widget.RadioGroup rgRepeat = dialogView.findViewById(R.id.rgRepeat);
        android.widget.RadioButton rbOnce = dialogView.findViewById(R.id.rbOnce);
        android.widget.RadioButton rbEveryday = dialogView.findViewById(R.id.rbEveryday);
        android.widget.RadioButton rbWorkday = dialogView.findViewById(R.id.rbWorkday);
        android.widget.RadioButton rbHolidayWeekend = dialogView.findViewById(R.id.rbHolidayWeekend);

        // 根据当前 repeatDays 预选中对应模式
        boolean[] repeatDays = alarm.getRepeatDays();
        if (repeatDays == null) {
            repeatDays = new boolean[7];
        }

        // 判断当前模式
        boolean allFalse = true;
        boolean allTrue = true;
        boolean workday = repeatDays[0] && repeatDays[1] && repeatDays[2]
                && repeatDays[3] && repeatDays[4]
                && !repeatDays[5] && !repeatDays[6];
        boolean weekend = !repeatDays[0] && !repeatDays[1] && !repeatDays[2]
                && !repeatDays[3] && !repeatDays[4]
                && repeatDays[5] && repeatDays[6];

        for (boolean d : repeatDays) {
            if (d) {
                allFalse = false;
            } else {
                allTrue = false;
            }
        }

        if (allFalse) {
            rbOnce.setChecked(true);
        } else if (allTrue) {
            rbEveryday.setChecked(true);
        } else if (workday) {
            rbWorkday.setChecked(true);
        } else if (weekend) {
            rbHolidayWeekend.setChecked(true);
        } else {
            // 自定义情况：不匹配预设，默认显示为“仅一次”但保留原来的 repeatDays
            rbOnce.setChecked(true);
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.repeat_days))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    boolean[] days = new boolean[7];

                    int checkedId = rgRepeat.getCheckedRadioButtonId();
                    if (checkedId == R.id.rbEveryday) {
                        // 每天：全部 true
                        for (int i = 0; i < 7; i++) {
                            days[i] = true;
                        }
                    } else if (checkedId == R.id.rbWorkday) {
                        // 法定工作日：周一到周五 true
                        for (int i = 0; i < 5; i++) {
                            days[i] = true;
                        }
                    } else if (checkedId == R.id.rbHolidayWeekend) {
                        // 周末：周六周日 true
                        days[5] = true;
                        days[6] = true;
                    } else {
                        // 仅一次：全部 false
                        // 数组默认即为 false，无需额外处理
                    }

                    alarm.setRepeatDays(days);
                    tvRepeatValue.setText(alarm.getRepeatString());
                    // 根据重复模式更新日期是否可编辑
                    updateDateEnabledByRepeat();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 根据重复模式控制“日期”这一行是否可用
     * - 仅一次（所有 repeatDays 都为 false）：日期可选择
     * - 其他模式：日期固定为自动计算的“今天/明天/后天”，不允许手动修改
     */
    private void updateDateEnabledByRepeat() {
        boolean[] repeatDays = alarm.getRepeatDays();
        boolean isOnce = true;
        if (repeatDays != null) {
            for (boolean d : repeatDays) {
                if (d) {
                    isOnce = false;
                    break;
                }
            }
        }

        View llDate = findViewById(R.id.llDate);
        if (llDate == null) return;

        if (isOnce) {
            // 仅一次：允许选择具体日期
            llDate.setEnabled(true);
            llDate.setAlpha(1.0f);
            tvDateValue.setAlpha(1.0f);
        } else {
            // 每天 / 工作日 / 节假日及周末：日期自动计算，不允许手动选
            llDate.setEnabled(false);
            llDate.setAlpha(0.5f);
            tvDateValue.setAlpha(0.5f);
            // 对于重复闹钟，清除精确日期，让调度逻辑只按星期重复
            alarm.setYear(0);
            alarm.setMonth(0);
            alarm.setDay(0);
        }
    }

    /**
     * 显示日期选择器对话框
     */
    private void showDatePicker() {
        // 使用当前已选日期作为初始值，如果还未设置则使用 selectedYear 等默认值
        Calendar calendar = Calendar.getInstance();
        if (selectedYear > 0 && selectedMonth > 0 && selectedDay > 0) {
            calendar.set(Calendar.YEAR, selectedYear);
            calendar.set(Calendar.MONTH, selectedMonth - 1);
            calendar.set(Calendar.DAY_OF_MONTH, selectedDay);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // 保存用户选择的日期（自然月份 1-12）
                    selectedYear = year;
                    selectedMonth = month + 1;
                    selectedDay = dayOfMonth;

                    // 同步到 Alarm 对象，使调度逻辑可以使用具体日期
                    alarm.setYear(selectedYear);
                    alarm.setMonth(selectedMonth);
                    alarm.setDay(selectedDay);

                    // 用于展示的 Calendar（包含日期 + 当前闹钟时间）
                    Calendar displayCal = Calendar.getInstance();
                    displayCal.set(Calendar.YEAR, year);
                    displayCal.set(Calendar.MONTH, month);
                    displayCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    displayCal.set(Calendar.HOUR_OF_DAY, alarm.getHour());
                    displayCal.set(Calendar.MINUTE, alarm.getMinute());
                    displayCal.set(Calendar.SECOND, 0);
                    displayCal.set(Calendar.MILLISECOND, 0);

                    updateDateDisplay(displayCal);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    /**
     * 显示闹钟名称输入对话框
     */
    private void showAlarmNameDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_alarm_name, null);
        com.google.android.material.textfield.TextInputEditText etName = 
                dialogView.findViewById(R.id.etAlarmName);
        etName.setText(alarm.getLabel());
        
        builder.setTitle(getString(R.string.alarm_name))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    String name = etName.getText() != null ? 
                            etName.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        alarm.setLabel(name);
                        tvAlarmNameValue.setText(name);
                        tvAlarmNameValue.setVisibility(View.VISIBLE);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private android.app.AlertDialog reminderMethodDialog;
    private com.google.android.material.textfield.TextInputEditText currentRingtoneEditText;
    
    /**
     * 显示提醒方式设置对话框（铃声选择+震动开关）
     */
    private void showReminderMethodDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reminder_method, null);
        
        androidx.appcompat.widget.SwitchCompat switchVibrate = 
                dialogView.findViewById(R.id.switchVibrate);
        com.google.android.material.textfield.TextInputEditText etRingtone = 
                dialogView.findViewById(R.id.etRingtone);
        
        // 设置震动开关的颜色：使用颜色选择器，开启=橙色，关闭=灰色
        android.content.res.ColorStateList thumbColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_thumb);
        android.content.res.ColorStateList trackColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_track);
        switchVibrate.setThumbTintList(thumbColor);
        switchVibrate.setTrackTintList(trackColor);
        
        switchVibrate.setChecked(alarm.isVibrate());
        etRingtone.setText(alarm.getRingtoneName() != null && !alarm.getRingtoneName().isEmpty() ? 
                alarm.getRingtoneName() : getString(R.string.default_alarm_ringtone));
        
        currentRingtoneEditText = etRingtone;
        
        etRingtone.setOnClickListener(v -> {
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_ringtone));
            if (alarm.getRingtoneUri() != null && !alarm.getRingtoneUri().isEmpty()) {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarm.getRingtoneUri()));
            }
            startActivityForResult(intent, 100);
        });
        
        reminderMethodDialog = new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.reminder_method))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    alarm.setVibrate(switchVibrate.isChecked());
                    tvReminderMethodValue.setText(alarm.isVibrate() 
                        ? getString(R.string.ring_and_vibrate) 
                        : getString(R.string.ring_only));
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();
        
        reminderMethodDialog.show();
    }
    
    /**
     * 处理从其他Activity返回的结果
     * @param requestCode 请求码（100=铃声选择器，300=稍后提醒设置）
     * @param resultCode 结果码
     * @param data 返回的数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri ringtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (ringtoneUri != null && currentRingtoneEditText != null) {
                String ringtoneName = RingtoneManager.getRingtone(this, ringtoneUri)
                        .getTitle(this);
                alarm.setRingtoneUri(ringtoneUri.toString());
                alarm.setRingtoneName(ringtoneName);
                currentRingtoneEditText.setText(ringtoneName);
            }
        } else if (requestCode == 300) {
            // 从稍后提醒设置页面返回，更新显示
            updateSnoozeDisplay();
        }
    }

    /**
     * 跳转到稍后提醒设置页面
     */
    private void showSnoozeDialog() {
        // 跳转到稍后提醒设置页面
        Intent intent = new Intent(this, SnoozeSettingsActivity.class);
        startActivityForResult(intent, 300);
    }

    /**
     * 保存闹钟
     * 
     * 流程：
     * 1. 从 UI 组件收集所有设置（时间、重复、开关状态等）
     * 2. 如果是新闹钟，确保设置为启用状态
     * 3. 根据 isNewAlarm 标志决定调用 addAlarm() 或 updateAlarm()
     * 4. 显示 Toast 提示
     * 5. 设置 RESULT_OK 并关闭 Activity
     * 
     * 注意：
     * - AlarmDataManager.addAlarm() 会自动调度系统闹钟
     * - AlarmDataManager.updateAlarm() 会先取消旧闹钟，再调度新闹钟
     */
    private void saveAlarm() {
        // 收集时间设置
        alarm.setHour(npHour.getValue());
        alarm.setMinute(npMinute.getValue());
        
        // 收集开关设置
        alarm.setSkipHolidays(switchSkipHolidays.isChecked());
        alarm.setDeleteAfterDismiss(switchDeleteAfterDismiss.isChecked());
        alarm.setSnoozeAnnounce(switchSnoozeAnnounce.isChecked());
        
        // 确保日期字段正确：如果是重复闹钟，清除日期；如果是仅一次且有日期，保存日期
        boolean[] repeatDays = alarm.getRepeatDays();
        boolean isOnce = true;
        if (repeatDays != null) {
            for (boolean d : repeatDays) {
                if (d) {
                    isOnce = false;
                    break;
                }
            }
        }
        
        if (isOnce) {
            // 仅一次：如果有选择的日期，保存；否则保持0（表示使用"明天"逻辑）
            if (selectedYear > 0 && selectedMonth > 0 && selectedDay > 0) {
                alarm.setYear(selectedYear);
                alarm.setMonth(selectedMonth);
                alarm.setDay(selectedDay);
            }
        } else {
            // 重复闹钟：清除日期字段，使用星期重复逻辑
            alarm.setYear(0);
            alarm.setMonth(0);
            alarm.setDay(0);
        }
        
        // 确保新创建的闹钟是启用状态
        if (isNewAlarm) {
            alarm.setEnabled(true);
        }
        
        // 保存到数据库并调度系统闹钟
        if (isNewAlarm) {
            alarmDataManager.addAlarm(alarm);
            Toast.makeText(this, R.string.alarm_added, Toast.LENGTH_SHORT).show();
        } else {
            alarmDataManager.updateAlarm(alarm);
            Toast.makeText(this, R.string.alarm_updated, Toast.LENGTH_SHORT).show();
        }
        
        // 返回结果并关闭 Activity
        setResult(RESULT_OK);
        finish();
    }
}

