package com.example.myapplication;

import android.app.AlarmManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.AlarmAdapter;
import com.example.myapplication.manager.AlarmDataManager;
import com.example.myapplication.model.Alarm;

import java.util.ArrayList;
import java.util.List;

/**
 * 主界面 Activity - 闹钟列表管理
 * 
 * 功能概述：
 * 1. 显示所有闹钟的列表（使用 RecyclerView + AlarmAdapter）
 * 2. 提供添加闹钟功能（点击右上角"+"按钮）
 * 3. 提供设置入口（点击右上角"两个圆圈"图标）
 * 4. 支持滑动删除闹钟（使用 ItemTouchHelper）
 * 5. 支持开关切换闹钟启用/禁用状态
 * 6. 点击闹钟项进入编辑界面
 * 
 * 生命周期管理：
 * - onCreate(): 初始化视图、数据管理器、检查权限、加载闹钟列表
 * - onResume(): 每次返回时刷新列表（确保从设置页返回后数据最新）
 * 
 * 权限检查：
 * - Android 12+ 需要精确闹钟权限（SCHEDULE_EXACT_ALARM）
 * - 如果没有权限，自动跳转到系统设置页面
 * 
 * 数据流：
 * - AlarmDataManager: 负责数据的 CRUD 操作和系统闹钟调度
 * - AlarmAdapter: 负责列表项的显示和交互事件回调
 */
public class MainActivity extends AppCompatActivity implements AlarmAdapter.OnAlarmActionListener {
    // RecyclerView：用于显示闹钟列表的控件
    private RecyclerView rvAlarms;
    // TextView：当没有闹钟时显示的提示文本（"暂无闹钟"）
    private TextView tvNoAlarms;
    // ImageView：右上角的添加闹钟按钮（"+"图标）
    private ImageView ivAddAlarm;

    // AlarmDataManager：闹钟数据管理器，负责数据的 CRUD 操作和系统闹钟调度
    private AlarmDataManager alarmDataManager;
    // AlarmAdapter：RecyclerView 的适配器，负责列表项的显示和事件处理
    private AlarmAdapter alarmAdapter;
    // List<Alarm>：存储所有闹钟的列表，从 AlarmDataManager 获取
    private List<Alarm> allAlarms;

    /**
     * Activity 的 onCreate 生命周期方法，在 Activity 创建时调用
     * @param savedInstanceState 保存的实例状态（用于Activity重建时恢复数据）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类的 onCreate 方法，执行 Activity 的基础初始化
        super.onCreate(savedInstanceState);
        
        // 设置状态栏颜色为白色（需要 Android 5.0 以上版本支持）
        // Build.VERSION.SDK_INT：获取当前 Android 系统的 API 级别
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // getWindow()：获取当前 Activity 的窗口对象
            // setStatusBarColor()：设置状态栏的颜色
            // ContextCompat.getColor()：安全地获取颜色资源（兼容旧版本）
            // R.color.white：引用 colors.xml 中定义的白色
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        }
        
        // EdgeToEdge.enable()：启用边缘到边缘显示（让内容延伸到系统栏下方）
        EdgeToEdge.enable(this);
        // setContentView()：设置 Activity 的布局文件（activity_main.xml）
        setContentView(R.layout.activity_main);
        // 设置窗口插槽监听器，处理系统栏（状态栏、导航栏）的边距
        // findViewById(R.id.main)：找到根布局 CoordinatorLayout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // 获取系统栏的尺寸（左、上、右、下的边距）
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 为根布局设置内边距，避免内容被系统栏遮挡
            // systemBars.left：左侧边距（通常是 0，除非有刘海屏）
            // systemBars.top：顶部边距（状态栏高度）
            // systemBars.right：右侧边距（通常是 0）
            // systemBars.bottom：底部边距（导航栏高度，如果有）
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            // 返回 insets 对象，表示已经处理了窗口插槽
            return insets;
        });

        // 初始化视图组件（绑定布局文件中的控件）
        initViews();
        // 初始化数据管理器
        initData();
        // 检查并请求精确闹钟权限（Android 12+）
        checkAndRequestPermissions();
        // 加载并显示所有闹钟列表
        loadAlarms();
    }

    /**
     * Activity 的 onResume 生命周期方法，在 Activity 恢复可见时调用
     * 每次从其他 Activity 返回时都会调用此方法
     */
    @Override
    protected void onResume() {
        // 调用父类的 onResume 方法
        super.onResume();
        // 返回设置页后刷新列表，确保清空操作立即生效
        // 例如：用户在设置页清空了所有闹钟，返回主界面时需要立即看到空列表
        loadAlarms();
    }
    
    /**
     * 检查并请求精确闹钟权限（Android 12+）
     * 
     * 说明：
     * - Android 12 (API 31) 开始，需要使用精确闹钟必须请求 SCHEDULE_EXACT_ALARM 权限
     * - 使用 AlarmManager.canScheduleExactAlarms() 检查是否有权限
     * - 如果没有权限，跳转到系统设置页面让用户授权
     * 
     * 注意：
     * - 这个权限不能通过运行时权限请求，必须跳转到系统设置
     * - 用户可能在设置中拒绝，应用需要优雅处理这种情况
     */
    /**
     * 检查并请求精确闹钟权限（Android 12+）
     */
    private void checkAndRequestPermissions() {
        // 检查精确闹钟权限（Android 12+，API 31）
        // Build.VERSION_CODES.S 对应 Android 12 (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // getSystemService(ALARM_SERVICE)：获取系统的 AlarmManager 服务
            // AlarmManager 用于调度系统级别的闹钟
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            // 检查是否已经有精确闹钟权限
            // alarmManager != null：确保 AlarmManager 服务可用
            // !alarmManager.canScheduleExactAlarms()：如果返回 false，表示没有权限
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                // 如果没有精确闹钟权限，跳转到系统设置页面让用户授权
                // Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM：系统提供的精确闹钟权限请求页面
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                // 启动系统设置页面
                startActivity(intent);
            }
        }
    }

    /**
     * 初始化视图组件
     * 绑定布局文件中的控件，设置 RecyclerView 和按钮的点击事件
     */
    private void initViews() {
        // findViewById()：根据资源 ID 查找布局文件中的视图组件
        // R.id.rvAlarms：activity_main.xml 中定义的 RecyclerView 的 ID
        rvAlarms = findViewById(R.id.rvAlarms);
        // R.id.tvNoAlarms：空状态提示文本的 ID
        tvNoAlarms = findViewById(R.id.tvNoAlarms);
        // R.id.ivAddAlarm：添加闹钟按钮的 ID
        ivAddAlarm = findViewById(R.id.ivAddAlarm);

        // 为 RecyclerView 设置布局管理器
        // LinearLayoutManager：线性布局管理器，列表项垂直排列
        // this：传入 Context（当前 Activity）
        rvAlarms.setLayoutManager(new LinearLayoutManager(this));
        // 创建 AlarmAdapter 适配器
        // new ArrayList<>()：初始化为空列表，稍后通过 updateAlarms() 更新数据
        // this：传入 OnAlarmActionListener 接口实现（MainActivity 实现了此接口）
        alarmAdapter = new AlarmAdapter(new ArrayList<>(), this);
        // 将适配器设置到 RecyclerView，这样 RecyclerView 才能显示数据
        rvAlarms.setAdapter(alarmAdapter);
        
        // 添加滑动删除功能
        // ItemTouchHelper：RecyclerView 提供的触摸辅助类，用于实现滑动和拖拽
        // new SwipeToDeleteCallback()：自定义的滑动回调类（内部类）
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeToDeleteCallback());
        // attachToRecyclerView()：将 ItemTouchHelper 附加到 RecyclerView，启用滑动功能
        itemTouchHelper.attachToRecyclerView(rvAlarms);

        // 查找设置按钮（右上角的两个圆圈图标）
        ImageView ivMore = findViewById(R.id.ivMore);
        
        // 设置添加按钮的点击监听器
        // v -> openEditAlarmActivity(null)：Lambda 表达式，点击时打开编辑界面（null 表示新建）
        ivAddAlarm.setOnClickListener(v -> openEditAlarmActivity(null));
        // 设置设置按钮的点击监听器
        // 点击时启动 SettingsActivity（设置界面）
        ivMore.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    /**
     * 初始化数据管理器
     */
    private void initData() {
        // 创建 AlarmDataManager 实例
        // this：传入 Context（当前 Activity），用于访问 SharedPreferences 和系统服务
        alarmDataManager = new AlarmDataManager(this);
    }

    /**
     * 加载闹钟列表
     * 
     * 流程：
     * 1. 从 AlarmDataManager 获取所有闹钟（从 SharedPreferences 读取）
     * 2. 更新 Adapter 的数据源
     * 3. 更新空状态显示（有闹钟时显示列表，无闹钟时显示"暂无闹钟"提示）
     * 
     * 调用时机：
     * - onCreate() 初始化时
     * - onResume() 返回界面时
     * - 添加/编辑/删除闹钟后
     * - 切换闹钟开关后（立即更新颜色显示）
     */
    private void loadAlarms() {
        // 从 AlarmDataManager 获取所有闹钟
        // getAlarms()：从 SharedPreferences 读取并反序列化 JSON 数据
        allAlarms = alarmDataManager.getAlarms();
        
        // 显示所有闹钟
        // updateAlarms()：更新适配器的数据源，并通知 RecyclerView 刷新显示
        alarmAdapter.updateAlarms(allAlarms);
        // 根据列表是否为空，更新空状态提示的显示/隐藏
        updateEmptyState();
    }

    /**
     * 更新空状态显示
     * 
     * 逻辑：
     * - 如果闹钟列表为空，显示"暂无闹钟"提示，隐藏列表
     * - 如果闹钟列表不为空，隐藏提示，显示列表
     */
    private void updateEmptyState() {
        // 判断列表是否为空
        // allAlarms == null：列表对象为 null（理论上不会发生，但防御性编程）
        // allAlarms.isEmpty()：列表为空（没有元素）
        boolean isEmpty = allAlarms == null || allAlarms.isEmpty();
        // 设置空状态提示文本的可见性
        // isEmpty ? View.VISIBLE : View.GONE：如果为空则显示，否则隐藏
        // View.VISIBLE：可见
        // View.GONE：隐藏（不占用布局空间）
        tvNoAlarms.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        // 设置 RecyclerView 的可见性
        // isEmpty ? View.GONE : View.VISIBLE：如果为空则隐藏列表，否则显示列表
        rvAlarms.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * 切换闹钟启用/禁用状态（AlarmAdapter 回调）
     * 当用户在列表项中切换开关时，Adapter 会调用此方法
     * 
     * @param alarm 要切换状态的闹钟对象
     * @param enabled true=启用，false=禁用
     * 
     * 流程：
     * 1. 更新闹钟的启用状态
     * 2. 调用 AlarmDataManager.updateAlarm() 保存并重新调度系统闹钟
     * 3. 重新加载列表以更新颜色显示（启用=黑色，禁用=灰色）
     * 4. 显示 Toast 提示
     */
    @Override
    public void onToggleAlarm(Alarm alarm, boolean enabled) {
        // 设置闹钟的启用状态（true=启用，false=禁用）
        alarm.setEnabled(enabled);
        // 更新闹钟数据并重新调度系统闹钟
        // updateAlarm() 会：
        // 1. 将更新的数据保存到 SharedPreferences
        // 2. 如果启用了，取消旧的系统闹钟，重新调度新的系统闹钟
        // 3. 如果禁用了，只取消系统闹钟，不重新调度
        alarmDataManager.updateAlarm(alarm);
        // 重新加载列表以更新颜色显示
        // 启用状态的闹钟时间显示为黑色，禁用状态显示为灰色
        loadAlarms();
        // 显示 Toast 提示消息
        // enabled ? R.string.enabled : R.string.disabled：根据状态选择提示文本
        // Toast.LENGTH_SHORT：短时间显示（约 2 秒）
        Toast.makeText(this, enabled ? R.string.enabled : R.string.disabled, Toast.LENGTH_SHORT).show();
    }

    /**
     * 编辑闹钟（AlarmAdapter 回调）
     * 当用户点击列表项时，Adapter 会调用此方法
     * 
     * @param alarm 要编辑的闹钟对象
     */
    @Override
    public void onEditAlarm(Alarm alarm) {
        // 打开编辑闹钟界面
        openEditAlarmActivity(alarm);
    }

    /**
     * 打开编辑闹钟界面
     * 
     * @param alarm 要编辑的闹钟，如果为 null 表示新建闹钟
     * 
     * 说明：
     * - 通过 Intent 传递 alarm_id 标识要编辑的闹钟
     * - 如果 alarm_id > 0，EditAlarmActivity 会加载现有闹钟数据
     * - 如果 alarm_id <= 0，EditAlarmActivity 会创建新闹钟
     * - 使用 startActivityForResult 以便编辑完成后刷新列表
     */
    private void openEditAlarmActivity(Alarm alarm) {
        // 创建 Intent，指定目标 Activity 为 EditAlarmActivity
        Intent intent = new Intent(this, EditAlarmActivity.class);
        // 如果传入的 alarm 不为 null，说明是编辑现有闹钟
        if (alarm != null) {
            // putExtra()：将闹钟 ID 放入 Intent 的额外数据中
            // "alarm_id"：键名
            // alarm.getId()：闹钟的唯一标识（时间戳）
            intent.putExtra("alarm_id", alarm.getId());
        }
        // 如果不是新建闹钟，alarm_id 会被 EditAlarmActivity 读取为 -1（默认值）
        // 设置是否为睡眠闹钟（当前版本已废弃，统一传 false）
        intent.putExtra("is_sleep_alarm", false);
        // 启动 Activity 并等待结果
        // 200：请求码，用于在 onActivityResult 中识别是哪个 Activity 返回的
        startActivityForResult(intent, 200);
    }

    /**
     * 处理从其他 Activity 返回的结果
     * 当 EditAlarmActivity 关闭并返回结果时，会调用此方法
     * 
     * @param requestCode 请求码（用于识别是哪个 Activity 返回的）
     * @param resultCode 结果码（RESULT_OK 表示成功，RESULT_CANCELED 表示取消）
     * @param data 返回的数据（Intent 对象，可能包含额外信息）
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 调用父类方法，处理基础逻辑
        super.onActivityResult(requestCode, resultCode, data);
        // 判断是否是 EditAlarmActivity 返回（requestCode == 200）且操作成功（RESULT_OK）
        if (requestCode == 200 && resultCode == RESULT_OK) {
            // 重新加载闹钟列表，刷新界面显示
            // 因为用户可能添加、编辑或删除了闹钟
            loadAlarms();
        }
    }

    /**
     * 删除闹钟（AlarmAdapter 回调）
     * 当用户滑动删除列表项时，Adapter 会调用此方法
     * 
     * @param alarm 要删除的闹钟对象
     */
    @Override
    public void onDeleteAlarm(Alarm alarm) {
        // 显示删除确认对话框
        showDeleteConfirmDialog(alarm);
    }

    /**
     * 显示删除确认对话框
     * 防止用户误删闹钟
     * 
     * @param alarm 要删除的闹钟对象
     */
    private void showDeleteConfirmDialog(Alarm alarm) {
        // 创建 AlertDialog（系统对话框）
        // AlertDialog.Builder：对话框构建器，使用链式调用设置属性
        new AlertDialog.Builder(this)
                // 设置对话框标题
                // R.string.delete_alarm：字符串资源（"删除闹钟"）
                .setTitle(R.string.delete_alarm)
                // 设置对话框消息内容
                // R.string.delete_confirm：字符串资源（"确定要删除这个闹钟吗？"）
                .setMessage(R.string.delete_confirm)
                // 设置确认按钮（正面按钮）
                // R.string.confirm：按钮文本（"确定"）
                // (dialog, which) -> {...}：Lambda 表达式，点击确认按钮时的回调
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    // 删除闹钟
                    // deleteAlarm() 会：
                    // 1. 从 SharedPreferences 中移除闹钟数据
                    // 2. 取消系统闹钟（通过 AlarmManager.cancel()）
                    alarmDataManager.deleteAlarm(alarm);
                    // 显示删除成功的提示
                    Toast.makeText(this, R.string.alarm_deleted, Toast.LENGTH_SHORT).show();
                    // 重新加载列表，移除已删除的项
                    loadAlarms();
                })
                // 设置取消按钮（负面按钮）
                // R.string.cancel：按钮文本（"取消"）
                // null：点击取消按钮时不执行任何操作，直接关闭对话框
                .setNegativeButton(R.string.cancel, null)
                // 显示对话框
                .show();
    }
    
    /**
     * 滑动删除功能的回调类
     * 
     * 功能：
     * 1. 支持左右滑动删除闹钟
     * 2. 滑动时显示红色背景和删除图标
     * 3. 滑动完成后显示确认对话框
     * 
     * 实现：
     * - 继承 ItemTouchHelper.SimpleCallback
     * - onMove() 返回 false（不支持拖拽排序）
     * - onSwiped() 处理滑动完成后的逻辑
     * - onChildDraw() 自定义滑动时的视觉效果（红色背景+删除图标）
     */
    /**
     * 滑动删除功能的回调类
     * 
     * 功能：
     * 1. 支持左右滑动删除闹钟
     * 2. 滑动时显示红色背景和删除图标
     * 3. 滑动完成后显示确认对话框
     * 
     * 实现：
     * - 继承 ItemTouchHelper.SimpleCallback
     * - onMove() 返回 false（不支持拖拽排序）
     * - onSwiped() 处理滑动完成后的逻辑
     * - onChildDraw() 自定义滑动时的视觉效果（红色背景+删除图标）
     */
    private class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
        /**
         * 构造函数
         * 
         * @param dragDirs 拖拽方向（0 表示不支持拖拽）
         * @param swipeDirs 滑动方向（LEFT | RIGHT 表示支持左右滑动）
         */
        SwipeToDeleteCallback() {
            // 调用父类构造函数
            // 第一个参数 0：不支持拖拽排序（不支持上下移动）
            // 第二个参数 ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT：支持左右滑动删除
            // ItemTouchHelper.LEFT：向左滑动
            // ItemTouchHelper.RIGHT：向右滑动
            // |：按位或运算符，组合多个方向
            super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        }

        /**
         * 处理拖拽移动事件（拖拽排序）
         * 因为本应用不支持拖拽排序，所以直接返回 false
         * 
         * @param recyclerView RecyclerView 对象
         * @param viewHolder 被拖拽的 ViewHolder
         * @param target 目标位置的 ViewHolder
         * @return false 表示不允许拖拽
         */
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            // 返回 false 表示不支持拖拽排序功能
            return false;
        }

        /**
         * 处理滑动删除事件
         * 当用户滑动列表项到足够距离时，会调用此方法
         * 
         * @param viewHolder 被滑动的 ViewHolder（列表项）
         * @param direction 滑动方向（ItemTouchHelper.LEFT 或 ItemTouchHelper.RIGHT）
         */
        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // 获取被滑动项在列表中的位置（索引）
            // getAdapterPosition()：返回 ViewHolder 在适配器中的位置
            int position = viewHolder.getAdapterPosition();
            // 检查位置是否有效（防止数组越界）
            // position >= 0：位置不能是负数
            // position < allAlarms.size()：位置不能超出列表范围
            if (position >= 0 && position < allAlarms.size()) {
                // 根据位置获取对应的闹钟对象
                Alarm alarm = allAlarms.get(position);
                // 刷新列表恢复item位置
                // 因为滑动后 RecyclerView 的布局可能被打乱，需要重新绑定数据恢复原样
                loadAlarms();
                // 显示确认对话框，询问用户是否真的要删除
                showDeleteConfirmDialog(alarm);
            }
        }
        
        /**
         * 自定义绘制滑动时的视觉效果
         * 在用户滑动列表项时，绘制红色背景和删除图标
         * 
         * @param c Canvas 画布对象，用于绘制图形
         * @param recyclerView RecyclerView 对象
         * @param viewHolder 被滑动的 ViewHolder
         * @param dX 水平方向的滑动距离（正值=向右滑动，负值=向左滑动）
         * @param dY 垂直方向的滑动距离（本应用中未使用）
         * @param actionState 动作状态（ACTION_STATE_SWIPE=滑动状态）
         * @param isCurrentlyActive 当前是否处于活动状态（用户正在滑动）
         */
        @Override
        public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, 
                                @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, 
                                int actionState, boolean isCurrentlyActive) {
            // 只处理滑动状态（ACTION_STATE_SWIPE）
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                // 获取列表项的根视图
                View itemView = viewHolder.itemView;
                // 创建红色背景绘制对象
                // ColorDrawable：纯色绘制对象
                // 0xFFFF5722：ARGB 颜色值（红色，用于表示删除操作）
                android.graphics.drawable.ColorDrawable background = 
                    new android.graphics.drawable.ColorDrawable(0xFFFF5722); // 红色背景
                // 获取系统删除图标
                // getDrawable()：从资源中获取 Drawable 对象
                // android.R.drawable.ic_menu_delete：系统提供的删除图标
                android.graphics.drawable.Drawable deleteIcon = 
                    getDrawable(android.R.drawable.ic_menu_delete);
                
                // 计算删除图标的位置（垂直居中）
                // iconMargin：图标距离上下边缘的边距
                // itemView.getHeight()：列表项的高度
                // deleteIcon.getIntrinsicHeight()：图标的固有高度
                int iconMargin = (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                // iconTop：图标的顶部 Y 坐标（列表项顶部 + 边距）
                int iconTop = itemView.getTop() + (itemView.getHeight() - deleteIcon.getIntrinsicHeight()) / 2;
                // iconBottom：图标的底部 Y 坐标（顶部 + 图标高度）
                int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();
                
                // 向右滑动（dX > 0）
                if (dX > 0) {
                    // 设置红色背景的绘制区域
                    // itemView.getLeft()：列表项左边界
                    // itemView.getTop()：列表项顶部
                    // (int) dX：滑动距离（作为右边界）
                    // itemView.getBottom()：列表项底部
                    background.setBounds(itemView.getLeft(), itemView.getTop(), 
                                       (int) dX, itemView.getBottom());
                    // 在画布上绘制红色背景
                    background.draw(c);
                    
                    // 计算删除图标的水平位置（在列表项左侧）
                    // iconLeft：图标左边界（列表项左边界 + 边距）
                    int iconLeft = itemView.getLeft() + iconMargin;
                    // iconRight：图标右边界（左边界 + 图标宽度）
                    int iconRight = itemView.getLeft() + iconMargin + deleteIcon.getIntrinsicWidth();
                    // 设置图标的绘制区域
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    // 在画布上绘制删除图标
                    deleteIcon.draw(c);
                } else if (dX < 0) {
                    // 向左滑动（dX < 0，负值）
                    // 设置红色背景的绘制区域（从右侧开始）
                    // itemView.getRight() + dX：滑动后的右边界（dX 是负值，所以是右边界向左移动）
                    // itemView.getRight()：列表项右边界（作为红色背景的右边界）
                    background.setBounds((int) (itemView.getRight() + dX), itemView.getTop(), 
                                       itemView.getRight(), itemView.getBottom());
                    // 在画布上绘制红色背景
                    background.draw(c);
                    
                    // 计算删除图标的水平位置（在列表项右侧）
                    // iconLeft：图标左边界（列表项右边界 - 边距 - 图标宽度）
                    int iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicWidth();
                    // iconRight：图标右边界（列表项右边界 - 边距）
                    int iconRight = itemView.getRight() - iconMargin;
                    // 设置图标的绘制区域
                    deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    // 在画布上绘制删除图标
                    deleteIcon.draw(c);
                } else {
                    // 没有滑动（dX == 0），不绘制背景
                    background.setBounds(0, 0, 0, 0);
                }
            }
            // 调用父类方法，继续执行默认的绘制逻辑（例如绘制列表项本身）
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }
}
