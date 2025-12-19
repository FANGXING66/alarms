package com.example.myapplication.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.R;
import com.example.myapplication.model.Alarm;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/**
 * 闹钟列表适配器（RecyclerView Adapter）
 * 
 * 功能说明：
 * 1. 管理闹钟列表的显示（使用 item_alarm.xml 布局）
 * 2. 显示闹钟时间、重复设置、标签、跳过节假日标记
 * 3. 根据启用状态设置文字颜色（启用=黑色，禁用=灰色）
 * 4. 提供开关切换功能（通过回调接口通知 MainActivity）
 * 5. 支持点击整个 item 进入编辑界面
 * 
 * 显示逻辑：
 * - tvTime: 显示闹钟时间（HH:mm 格式）
 * - tvRepeat: 显示重复设置（"仅一次"、"每天"、"周一 周二"等）
 * - tvLabel: 如果有自定义标签且不是默认的"闹钟"，则显示
 * - tvSkipHolidays: 如果启用了跳过节假日，显示"跳过节假日"标记（橙色）
 * - switchEnabled: 启用/禁用开关，切换时通过 OnAlarmActionListener 回调
 * 
 * 颜色规则：
 * - 启用状态：text_primary（黑色）、text_secondary（灰色）
 * - 禁用状态：text_disabled（浅灰色）
 */
public class AlarmAdapter extends RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder> {
    private List<Alarm> alarms;
    private OnAlarmActionListener listener;

    /**
     * 闹钟操作回调接口
     */
    public interface OnAlarmActionListener {
        /**
         * 切换闹钟启用/禁用状态
         * @param alarm 要切换的闹钟对象
         * @param enabled true=启用，false=禁用
         */
        void onToggleAlarm(Alarm alarm, boolean enabled);
        
        /**
         * 编辑闹钟
         * @param alarm 要编辑的闹钟对象
         */
        void onEditAlarm(Alarm alarm);
        
        /**
         * 删除闹钟
         * @param alarm 要删除的闹钟对象
         */
        void onDeleteAlarm(Alarm alarm);
    }

    /**
     * 构造函数
     * @param alarms 闹钟列表
     * @param listener 操作回调监听器
     */
    public AlarmAdapter(List<Alarm> alarms, OnAlarmActionListener listener) {
        this.alarms = alarms;
        this.listener = listener;
    }

    /**
     * 创建ViewHolder
     * @param parent 父容器
     * @param viewType 视图类型
     * @return ViewHolder实例
     */
    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alarm, parent, false);
        return new AlarmViewHolder(view);
    }

    /**
     * 绑定数据到ViewHolder
     * @param holder ViewHolder实例
     * @param position 列表项位置
     */
    @Override
    public void onBindViewHolder(@NonNull AlarmViewHolder holder, int position) {
        Alarm alarm = alarms.get(position);
        
        holder.tvTime.setText(alarm.getTimeString());
        holder.tvRepeat.setText(alarm.getRepeatString());
        
        // 显示标签
        if (alarm.getLabel() != null && !alarm.getLabel().isEmpty() && !alarm.getLabel().equals("闹钟")) {
            holder.tvLabel.setText(alarm.getLabel());
            holder.tvLabel.setVisibility(View.VISIBLE);
        } else {
            holder.tvLabel.setVisibility(View.GONE);
        }
        
        // 显示跳过节假日
        if (alarm.isSkipHolidays()) {
            holder.tvSkipHolidays.setVisibility(View.VISIBLE);
        } else {
            holder.tvSkipHolidays.setVisibility(View.GONE);
        }
        
        // 根据启用状态设置文字颜色
        if (alarm.isEnabled()) {
            holder.tvTime.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
            holder.tvRepeat.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
            if (holder.tvLabel.getVisibility() == View.VISIBLE) {
                holder.tvLabel.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
            }
        } else {
            holder.tvTime.setTextColor(holder.itemView.getContext().getColor(R.color.text_disabled));
            holder.tvRepeat.setTextColor(holder.itemView.getContext().getColor(R.color.text_disabled));
            if (holder.tvLabel.getVisibility() == View.VISIBLE) {
                holder.tvLabel.setTextColor(holder.itemView.getContext().getColor(R.color.text_disabled));
            }
        }

        // 设置开关颜色：使用颜色选择器，开启=橙色，关闭=灰色
        ColorStateList thumbColor = ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.switch_thumb);
        ColorStateList trackColor = ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.switch_track);
        holder.switchEnabled.setThumbTintList(thumbColor);
        holder.switchEnabled.setTrackTintList(trackColor);
        
        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(alarm.isEnabled());
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onToggleAlarm(alarm, isChecked);
            }
        });

        // 点击整个item编辑闹钟
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditAlarm(alarm);
            }
        });
    }

    /**
     * 获取列表项数量
     * @return 列表项数量
     */
    @Override
    public int getItemCount() {
        return alarms != null ? alarms.size() : 0;
    }

    /**
     * 更新闹钟列表数据
     * @param newAlarms 新的闹钟列表
     */
    public void updateAlarms(List<Alarm> newAlarms) {
        this.alarms = newAlarms;
        notifyDataSetChanged();
    }

    /**
     * ViewHolder类，持有列表项的视图引用
     */
    static class AlarmViewHolder extends RecyclerView.ViewHolder {
        TextView tvTime;
        TextView tvRepeat;
        TextView tvLabel;
        TextView tvSkipHolidays;
        SwitchCompat switchEnabled;

        /**
         * ViewHolder构造函数
         * @param itemView 列表项根视图
         */
        AlarmViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvRepeat = itemView.findViewById(R.id.tvRepeat);
            tvLabel = itemView.findViewById(R.id.tvLabel);
            tvSkipHolidays = itemView.findViewById(R.id.tvSkipHolidays);
            switchEnabled = itemView.findViewById(R.id.switchEnabled);
        }
    }
}

