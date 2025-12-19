package com.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.card.MaterialCardView;

/**
 * 稍后提醒设置界面 Activity
 * 
 * 功能概述：
 * 1. 稍后提醒总开关：控制是否启用稍后提醒功能
 * 2. 提醒间隔设置：5分钟、10分钟、15分钟、30分钟（单选）
 * 3. 提醒次数设置：2次、3次、5次、10次（单选，表示最多可以稍后提醒多少次）
 * 
 * 数据存储：
 * - 使用 SharedPreferences，名称：settings_prefs
 * - KEY_SNOOZE_ENABLED: 稍后提醒开关（boolean，默认 true）
 * - KEY_SNOOZE_INTERVAL: 提醒间隔（int，分钟，默认 10）
 * - KEY_SNOOZE_COUNT: 最大提醒次数（int，默认 5）
 * 
 * 使用场景：
 * - 在 AlarmRingingActivity 中，用户点击"稍后提醒"按钮时：
 *   1. 检查 KEY_SNOOZE_ENABLED 是否启用
 *   2. 检查当前闹钟的稍后提醒次数是否已达 KEY_SNOOZE_COUNT 上限
 *   3. 如果都通过，使用 KEY_SNOOZE_INTERVAL 作为间隔时间重新调度闹钟
 * 
 * UI 逻辑：
 * - 当稍后提醒开关关闭时，间隔和次数的选项会变为禁用状态（alpha=0.5）
 */
public class SnoozeSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "settings_prefs";
    private static final String KEY_SNOOZE_ENABLED = "snooze_enabled";
    private static final String KEY_SNOOZE_INTERVAL = "snooze_interval";
    private static final String KEY_SNOOZE_COUNT = "snooze_count";
    
    private static final int DEFAULT_INTERVAL = 10; // 默认10分钟
    private static final int DEFAULT_COUNT = 5; // 默认5次
    
    private SwitchCompat switchSnoozeEnabled;
    private RadioGroup rgInterval;
    private RadioGroup rgCount;
    private RadioButton rbInterval5, rbInterval10, rbInterval15, rbInterval30;
    private RadioButton rbCount2, rbCount3, rbCount5, rbCount10;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置状态栏颜色为白色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        }
        
        setContentView(R.layout.activity_snooze_settings);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        initViews();
        loadSettings();
        setupListeners();
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        switchSnoozeEnabled = findViewById(R.id.switchSnoozeEnabled);
        rgInterval = findViewById(R.id.rgInterval);
        rgCount = findViewById(R.id.rgCount);
        
        rbInterval5 = findViewById(R.id.rbInterval5);
        rbInterval10 = findViewById(R.id.rbInterval10);
        rbInterval15 = findViewById(R.id.rbInterval15);
        rbInterval30 = findViewById(R.id.rbInterval30);
        
        rbCount2 = findViewById(R.id.rbCount2);
        rbCount3 = findViewById(R.id.rbCount3);
        rbCount5 = findViewById(R.id.rbCount5);
        rbCount10 = findViewById(R.id.rbCount10);
        
        // 设置开关颜色：使用颜色选择器，开启=橙色，关闭=灰色
        android.content.res.ColorStateList thumbColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_thumb);
        android.content.res.ColorStateList trackColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_track);
        switchSnoozeEnabled.setThumbTintList(thumbColor);
        switchSnoozeEnabled.setTrackTintList(trackColor);
        
        ivBack.setOnClickListener(v -> finish());
    }

    /**
     * 从SharedPreferences加载设置并显示
     */
    private void loadSettings() {
        // 加载稍后提醒开关
        boolean snoozeEnabled = prefs.getBoolean(KEY_SNOOZE_ENABLED, true);
        switchSnoozeEnabled.setChecked(snoozeEnabled);
        
        // 加载提醒间隔
        int interval = prefs.getInt(KEY_SNOOZE_INTERVAL, DEFAULT_INTERVAL);
        switch (interval) {
            case 5:
                rbInterval5.setChecked(true);
                break;
            case 10:
                rbInterval10.setChecked(true);
                break;
            case 15:
                rbInterval15.setChecked(true);
                break;
            case 30:
                rbInterval30.setChecked(true);
                break;
            default:
                rbInterval10.setChecked(true);
                break;
        }
        
        // 加载提醒次数
        int count = prefs.getInt(KEY_SNOOZE_COUNT, DEFAULT_COUNT);
        switch (count) {
            case 2:
                rbCount2.setChecked(true);
                break;
            case 3:
                rbCount3.setChecked(true);
                break;
            case 5:
                rbCount5.setChecked(true);
                break;
            case 10:
                rbCount10.setChecked(true);
                break;
            default:
                rbCount5.setChecked(true);
                break;
        }
        
        // 根据开关状态设置选项可用性
        updateOptionsEnabled(snoozeEnabled);
    }

    /**
     * 设置所有控件的监听器
     */
    private void setupListeners() {
        // 稍后提醒开关
        switchSnoozeEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_SNOOZE_ENABLED, isChecked).apply();
            updateOptionsEnabled(isChecked);
        });
        
        // 提醒间隔
        rgInterval.setOnCheckedChangeListener((group, checkedId) -> {
            int interval = DEFAULT_INTERVAL;
            if (checkedId == R.id.rbInterval5) {
                interval = 5;
            } else if (checkedId == R.id.rbInterval10) {
                interval = 10;
            } else if (checkedId == R.id.rbInterval15) {
                interval = 15;
            } else if (checkedId == R.id.rbInterval30) {
                interval = 30;
            }
            prefs.edit().putInt(KEY_SNOOZE_INTERVAL, interval).apply();
        });
        
        // 提醒次数
        rgCount.setOnCheckedChangeListener((group, checkedId) -> {
            int count = DEFAULT_COUNT;
            if (checkedId == R.id.rbCount2) {
                count = 2;
            } else if (checkedId == R.id.rbCount3) {
                count = 3;
            } else if (checkedId == R.id.rbCount5) {
                count = 5;
            } else if (checkedId == R.id.rbCount10) {
                count = 10;
            }
            prefs.edit().putInt(KEY_SNOOZE_COUNT, count).apply();
        });
    }

    /**
     * 根据稍后提醒开关状态更新选项的启用状态
     * @param enabled true=启用，false=禁用
     */
    private void updateOptionsEnabled(boolean enabled) {
        rgInterval.setEnabled(enabled);
        rgCount.setEnabled(enabled);
        
        rbInterval5.setEnabled(enabled);
        rbInterval10.setEnabled(enabled);
        rbInterval15.setEnabled(enabled);
        rbInterval30.setEnabled(enabled);
        
        rbCount2.setEnabled(enabled);
        rbCount3.setEnabled(enabled);
        rbCount5.setEnabled(enabled);
        rbCount10.setEnabled(enabled);
        
        // 设置alpha值以视觉上表示禁用状态
        float alpha = enabled ? 1.0f : 0.5f;
        rgInterval.setAlpha(alpha);
        rgCount.setAlpha(alpha);
    }
}

