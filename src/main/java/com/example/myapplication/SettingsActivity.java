package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

/**
 * 设置界面 Activity
 * 
 * 功能概述：
 * 1. 默认铃声设置：选择全局默认铃声（闹钟未设置铃声时使用）
 * 2. 稍后提醒设置：跳转到 SnoozeSettingsActivity
 * 3. 节假日设置：全局跳过节假日开关（所有闹钟都会检查此设置）
 * 4. 关于信息：显示应用版本和功能介绍
 * 5. 清空数据：一键删除所有闹钟和设置
 * 
 * 数据存储：
 * - 使用 SharedPreferences，名称：settings_prefs
 * - KEY_DEFAULT_RINGTONE_URI: 默认铃声 URI
 * - KEY_DEFAULT_RINGTONE_NAME: 默认铃声名称（用于显示）
 * - KEY_GLOBAL_SKIP_HOLIDAYS: 全局跳过节假日开关（boolean）
 * 
 * 全局设置说明：
 * - 全局跳过节假日：如果开启，所有闹钟在调度时都会检查是否为节假日
 * - 单个闹钟也可以单独设置跳过节假日，优先级高于全局设置
 * - 默认铃声：如果闹钟自身没有设置铃声，会使用此默认铃声
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "settings_prefs";
    private static final String KEY_DEFAULT_RINGTONE_URI = "default_ringtone_uri";
    private static final String KEY_DEFAULT_RINGTONE_NAME = "default_ringtone_name";
    private static final String KEY_GLOBAL_SKIP_HOLIDAYS = "global_skip_holidays";
    
    private TextView tvDefaultRingtone;
    private MaterialCardView cardDefaultRingtone;
    private MaterialCardView cardSnooze;
    private MaterialCardView cardHoliday;
    private MaterialCardView cardAbout;
    private MaterialCardView cardClearData;
    private androidx.appcompat.widget.SwitchCompat switchHoliday;
    
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置状态栏颜色为白色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        }
        
        setContentView(R.layout.activity_settings);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        initViews();
        loadSettings();
    }

    /**
     * 初始化视图组件并设置点击事件
     */
    private void initViews() {
        tvDefaultRingtone = findViewById(R.id.tvDefaultRingtone);
        cardDefaultRingtone = findViewById(R.id.cardDefaultRingtone);
        cardSnooze = findViewById(R.id.cardSnooze);
        cardHoliday = findViewById(R.id.cardHoliday);
        cardAbout = findViewById(R.id.cardAbout);
        cardClearData = findViewById(R.id.cardClearData);
        switchHoliday = findViewById(R.id.switchHoliday);

        cardDefaultRingtone.setOnClickListener(v -> showRingtonePicker());
        cardSnooze.setOnClickListener(v -> {
            Intent intent = new Intent(this, SnoozeSettingsActivity.class);
            startActivity(intent);
        });
        cardHoliday.setOnClickListener(v -> showHolidaySettings());
        cardAbout.setOnClickListener(v -> showAbout());
        cardClearData.setOnClickListener(v -> showClearDataDialog());

        // 设置开关颜色：使用颜色选择器，开启=橙色，关闭=灰色
        android.content.res.ColorStateList thumbColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_thumb);
        android.content.res.ColorStateList trackColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.switch_track);
        switchHoliday.setThumbTintList(thumbColor);
        switchHoliday.setTrackTintList(trackColor);

        // 全局节假日开关
        switchHoliday.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(KEY_GLOBAL_SKIP_HOLIDAYS, isChecked).apply());
    }

    /**
     * 加载并显示保存的设置
     */
    private void loadSettings() {
        String ringtoneName = prefs.getString(KEY_DEFAULT_RINGTONE_NAME, getString(R.string.default_alarm_ringtone));
        tvDefaultRingtone.setText(ringtoneName);

        boolean skipHolidays = prefs.getBoolean(KEY_GLOBAL_SKIP_HOLIDAYS, false);
        switchHoliday.setChecked(skipHolidays);
    }

    /**
     * 显示铃声选择器对话框
     */
    private void showRingtonePicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.select_ringtone));
        
        String savedUri = prefs.getString(KEY_DEFAULT_RINGTONE_URI, null);
        if (savedUri != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(savedUri));
        }
        
        startActivityForResult(intent, 200);
    }

    /**
     * 处理从其他Activity返回的结果
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 返回的数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri ringtoneUri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (ringtoneUri != null) {
                String ringtoneName = RingtoneManager.getRingtone(this, ringtoneUri)
                        .getTitle(this);
                prefs.edit()
                        .putString(KEY_DEFAULT_RINGTONE_URI, ringtoneUri.toString())
                        .putString(KEY_DEFAULT_RINGTONE_NAME, ringtoneName)
                        .apply();
                tvDefaultRingtone.setText(ringtoneName);
                Toast.makeText(this, "默认铃声已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 显示节假日设置说明对话框
     */
    private void showHolidaySettings() {
        new AlertDialog.Builder(this)
                .setTitle("节假日设置")
                .setMessage("开启后，闹钟会在法定节假日自动跳过。\n\n" +
                        "每个闹钟中也可以单独设置是否跳过节假日，此处为全局默认设置。")
                .setPositiveButton(getString(R.string.confirm), null)
                .show();
    }

    /**
     * 一键清空闹钟和设置
     */
    private void showClearDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空数据")
                .setMessage(getString(R.string.clear_data_confirm))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    // 清空闹钟
                    com.example.myapplication.manager.AlarmDataManager alarmDataManager =
                            new com.example.myapplication.manager.AlarmDataManager(this);
                    alarmDataManager.clearAllAlarms();

                    // 清空设置 SharedPreferences
                    prefs.edit().clear().apply();

                    // 关闭节假日开关显示
                    switchHoliday.setOnCheckedChangeListener(null);
                    switchHoliday.setChecked(false);
                    switchHoliday.setOnCheckedChangeListener((buttonView, isChecked) ->
                            prefs.edit().putBoolean(KEY_GLOBAL_SKIP_HOLIDAYS, isChecked).apply());

                    Toast.makeText(this, "已清空闹钟和设置", Toast.LENGTH_SHORT).show();

                    // 回到主界面并刷新列表
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    /**
     * 显示关于对话框
     */
    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("俺的智能闹钟 v1.1.0\n\n支持自定义铃声、重复设置、跳过节假日，同时修复了bug问题等功能，欢迎提出意见")
                .setPositiveButton(getString(R.string.confirm), null)
                .show();
    }
}

