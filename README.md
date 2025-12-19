# 智能闹钟应用 - 项目目录结构与文件说明

## 一、Java 源代码目录结构

```
app/src/main/java/com/example/myapplication/
│
├── 📁 model/                          # 数据模型层
│   └── Alarm.java                     # 闹钟数据模型类
│
├── 📁 manager/                        # 数据管理层
│   └── AlarmDataManager.java          # 闹钟数据管理器
│
├── 📁 receiver/                       # 广播接收器层
│   └── AlarmReceiver.java             # 闹钟广播接收器
│
├── 📁 adapter/                        # 适配器层
│   └── AlarmAdapter.java              # 闹钟列表适配器
│
├── 📁 util/                           # 工具类层
│   └── HolidayUtil.java               # 节假日工具类
│
├── MainActivity.java                  # 主界面 Activity
├── EditAlarmActivity.java             # 编辑闹钟界面 Activity
├── AlarmRingingActivity.java          # 闹钟响铃界面 Activity
├── SettingsActivity.java              # 设置界面 Activity
└── SnoozeSettingsActivity.java        # 稍后提醒设置界面 Activity
```

---

## 二、Java 文件详细说明

### 2.1 数据模型层（model/）

#### `Alarm.java`
**路径**：`com.example.myapplication.model.Alarm`

**功能**：
- 闹钟数据实体类，实现 `Serializable` 接口
- 存储单个闹钟的所有配置信息

**关键字段**：
- `id` (int): 唯一标识，使用时间戳生成
- `hour` / `minute` (int): 闹钟时间
- `label` (String): 闹钟标签/名称，默认"闹钟"
- `enabled` (boolean): 是否启用
- `repeatDays` (boolean[]): 重复星期设置（7个布尔值，对应周一到周日）
- `ringtoneUri` / `ringtoneName` (String): 铃声URI和名称
- `vibrate` (boolean): 是否震动
- `volume` (int): 音量（0-100）
- `skipHolidays` (boolean): 是否跳过法定节假日
- `isSleepAlarm` (boolean): 是否为睡眠/起床闹钟（已废弃）
- `deleteAfterDismiss` (boolean): 关闭后是否删除此闹钟
- `snoozeAnnounce` (boolean): 稍后提醒时是否报时

**关键方法**：
- `getTimeString()`: 返回格式化的时间字符串（HH:mm）
- `getRepeatString()`: 返回重复设置的显示文本（"仅一次"、"每天"、"周一 周二"等）

**设计特点**：
- 默认时间使用**当前时间**（而非固定08:00），避免重装后出现默认闹钟
- 实现 `Serializable` 接口，支持通过 Intent 传递

---

### 2.2 数据管理层（manager/）

#### `AlarmDataManager.java`
**路径**：`com.example.myapplication.manager.AlarmDataManager`

**功能**：
- 管理闹钟数据的持久化存储（使用 SharedPreferences + Gson）
- 调度/取消系统闹钟（使用 AlarmManager）
- 实现节假日跳过逻辑

**核心方法**：

1. **`addAlarm(Alarm alarm)`**
   - 添加新闹钟到列表
   - 保存到 SharedPreferences（alarm_prefs）
   - 调度系统闹钟

2. **`updateAlarm(Alarm alarm)`**
   - 更新现有闹钟数据
   - 先取消旧系统闹钟，再重新调度

3. **`deleteAlarm(Alarm alarm)`**
   - 从列表删除闹钟
   - 取消系统闹钟

4. **`getAlarms()`: List<Alarm>**
   - 从 SharedPreferences 读取所有闹钟
   - 使用 Gson 反序列化 JSON 字符串

5. **`scheduleAlarm(Alarm alarm)`**（私有方法）
   - 创建 PendingIntent 指向 AlarmReceiver
   - 计算触发时间（如果已过当前时间，设置为明天）
   - **节假日跳过逻辑**：如果启用，循环查找下一个非节假日（最多365天）
   - 重复闹钟：使用 `AlarmManager.setRepeating()`
   - 单次闹钟：使用 `AlarmManager.setExactAndAllowWhileIdle()`

6. **`clearAllAlarms()`**
   - 取消所有系统闹钟
   - 清空 SharedPreferences 中的闹钟数据

**数据存储**：
- SharedPreferences 名称：`alarm_prefs`
- 存储格式：JSON 字符串（通过 Gson 序列化 `List<Alarm>`）

---

### 2.3 广播接收器层（receiver/）

#### `AlarmReceiver.java`
**路径**：`com.example.myapplication.receiver.AlarmReceiver`

**功能**：
- 接收系统闹钟触发的广播（由 AlarmManager 发送）
- 启动闹钟响铃流程

**核心方法**：

1. **`onReceive(Context context, Intent intent)`**
   - **安全检查**：验证闹钟ID是否存在于本地数据（防止旧版本残留的系统闹钟）
   - 获取唤醒锁（WakeLock）保持设备唤醒
   - 播放铃声（`playRingtone()`）
   - 震动（如果启用）
   - 启动 `AlarmRingingActivity`

2. **`playRingtone(Context context, String ringtoneUri, int volume)`**（私有方法）
   - **铃声选择优先级**：
     1. 使用传入的 `ringtoneUri`（闹钟自身设置的铃声）
     2. 如果为空，调用 `getDefaultRingtoneUri()` 获取设置中的默认铃声
     3. 如果还没有，使用系统默认铃声
   - 优先使用 `Ringtone` 播放，失败则回退到 `MediaPlayer`

3. **`getDefaultRingtoneUri(Context context)`**（私有方法）
   - 从 `settings_prefs` 读取默认铃声URI
   - 如果没有，返回系统默认铃声URI

4. **`stopRingtone()`**（静态方法）
   - 停止当前播放的铃声
   - 可以从 Activity 调用

**注册方式**：
- 在 `AndroidManifest.xml` 中注册
- 设置 `directBootAware="true"` 以支持开机启动

---

### 2.4 适配器层（adapter/）

#### `AlarmAdapter.java`
**路径**：`com.example.myapplication.adapter.AlarmAdapter`

**功能**：
- RecyclerView 适配器，管理闹钟列表的显示
- 处理列表项的点击和开关切换事件

**接口**：
- `OnAlarmActionListener`：
  - `onToggleAlarm(Alarm alarm, boolean enabled)`: 切换闹钟启用/禁用状态
  - `onEditAlarm(Alarm alarm)`: 编辑闹钟
  - `onDeleteAlarm(Alarm alarm)`: 删除闹钟

**核心方法**：

1. **`onCreateViewHolder(ViewGroup parent, int viewType)`**
   - 创建 ViewHolder，使用 `item_alarm.xml` 布局

2. **`onBindViewHolder(AlarmViewHolder holder, int position)`**
   - 绑定数据到 ViewHolder
   - 显示时间、重复设置、标签、跳过节假日标记
   - 根据启用状态设置文字颜色（启用=黑色，禁用=灰色）
   - 设置开关状态和点击监听器

3. **`updateAlarms(List<Alarm> newAlarms)`**
   - 更新数据源并通知 RecyclerView 刷新

**显示逻辑**：
- `tvTime`: 显示时间（HH:mm 格式）
- `tvRepeat`: 显示重复设置（"仅一次"、"每天"、"周一 周二"等）
- `tvLabel`: 如果有自定义标签且不是"闹钟"，则显示
- `tvSkipHolidays`: 如果启用了跳过节假日，显示"跳过节假日"标记（橙色）
- `switchEnabled`: 启用/禁用开关

---

### 2.5 工具类层（util/）

#### `HolidayUtil.java`
**路径**：`com.example.myapplication.util.HolidayUtil`

**功能**：
- 调用外部 API 判断指定日期是否为法定节假日

**API 信息**：
- 接口地址：`http://api.haoshenqi.top/holiday?date=YYYY-MM-DD`
- 请求方式：GET
- 响应格式：`{"date": "2019-05-01", "year": 2019, "month": 5, "day": 1, "status": 3}`
- status 含义：
  - `0`: 普通工作日
  - `1`: 周末双休日
  - `2`: 需要补班的工作日
  - `3`: **法定节假日**（我们要判断的）

**核心方法**：

1. **`isHoliday(Calendar calendar): boolean`**
   - 检查指定日期是否为法定节假日
   - 内部调用 `fetchHolidayFromApi()`
   - 如果网络失败，返回 `false`（确保闹钟正常响铃）

2. **`isHoliday(int year, int month, int day): boolean`**（重载方法）
   - 使用年月日参数检查

3. **`fetchHolidayFromApi(String dateStr): Boolean`**（私有方法）
   - 发送 HTTP GET 请求
   - 连接超时和读取超时都设置为 3 秒
   - 解析 JSON，判断 `status == 3`
   - 网络异常时返回 `null`

**注意事项**：
- 当前实现在主线程执行，可能阻塞 UI（建议改为后台线程）
- 网络失败时返回 `false`，确保闹钟正常响铃

---

### 2.6 Activity 类

#### `MainActivity.java`
**路径**：`com.example.myapplication.MainActivity`

**功能**：
- 主界面，显示闹钟列表
- 提供添加、编辑、删除闹钟功能

**核心功能**：

1. **列表展示**：
   - 使用 RecyclerView + AlarmAdapter
   - 显示所有闹钟（时间、重复设置、标签、跳过节假日标记）

2. **滑动删除**：
   - 使用 ItemTouchHelper 实现左右滑动
   - 滑动完成后显示确认对话框

3. **开关切换**：
   - 点击开关切换闹钟启用/禁用状态
   - 立即刷新列表以更新颜色显示

4. **权限检查**：
   - Android 12+ 检查精确闹钟权限
   - 如果没有权限，跳转到系统设置页面

5. **生命周期**：
   - `onResume()`: 刷新列表（确保从设置页返回后数据最新）

**实现接口**：
- `AlarmAdapter.OnAlarmActionListener`: 处理列表项的交互事件

---

#### `EditAlarmActivity.java`
**路径**：`com.example.myapplication.EditAlarmActivity`

**功能**：
- 添加新闹钟或编辑现有闹钟
- 提供所有闹钟配置选项的编辑界面

**配置项**：

1. **时间选择**：
   - 使用两个 NumberPicker（小时0-23，分钟0-59）
   - 显示格式："02时" 和 "30分"

2. **重复设置**：
   - 点击弹出对话框
   - 使用 Chip 组件选择星期几

3. **日期选择**：
   - 点击弹出 DatePickerDialog
   - 显示格式："今天"、"明天"、"后天" 或 "X月X日"

4. **闹钟名称**：
   - 点击弹出输入对话框

5. **提醒方式**：
   - 点击弹出对话框
   - 铃声选择器（RingtoneManager）
   - 震动开关

6. **稍后提醒**：
   - 点击跳转到 `SnoozeSettingsActivity`
   - 显示当前稍后提醒设置（间隔和次数）

7. **单个闹钟的特殊设置**（开关）：
   - 跳过节假日：是否跳过节假日（覆盖全局设置）
   - 关闭后删除此闹钟：点击"关闭"时是否删除闹钟
   - 稍后提醒报时：点击"稍后提醒"时是否播报时间

**数据保存**：
- 新建：调用 `AlarmDataManager.addAlarm()`
- 编辑：调用 `AlarmDataManager.updateAlarm()`
- 保存成功后返回 MainActivity，并刷新列表

---

#### `AlarmRingingActivity.java`
**路径**：`com.example.myapplication.AlarmRingingActivity`

**功能**：
- 闹钟响铃界面
- 显示闹钟时间和标签
- 处理用户交互（稍后提醒/关闭）

**特殊处理**：

1. **唤醒设备**：
   - 设置窗口标志（FLAG_TURN_SCREEN_ON, FLAG_SHOW_WHEN_LOCKED等）
   - 获取 WakeLock 保持设备唤醒（5分钟）

2. **播放铃声和震动**：
   - 从 Intent 获取铃声URI
   - 优先使用闹钟自身设置，否则使用默认铃声
   - 使用 Ringtone 或 MediaPlayer 播放
   - 根据设置决定是否震动

3. **稍后提醒功能**：
   - 读取全局设置（间隔时间、最大次数）
   - 检查当前闹钟的稍后提醒次数是否已达上限
   - 如果启用报时（`alarm.isSnoozeAnnounce()`），使用 TextToSpeech 播报当前时间
   - 调用 `snoozeAlarm()` 重新调度系统闹钟（N分钟后）

4. **关闭功能**：
   - 停止铃声和震动
   - 如果 `alarm.isDeleteAfterDismiss()` 为 true，删除闹钟

5. **安全检查**：
   - 如果闹钟ID不存在，直接关闭（防止旧版本残留）

**特殊设置**：
- 禁用返回键，必须点击按钮才能关闭
- 使用特殊主题（AlarmRingingTheme）确保在锁屏时显示

---

#### `SettingsActivity.java`
**路径**：`com.example.myapplication.SettingsActivity`

**功能**：
- 管理全局设置

**全局设置项**：

1. **默认铃声**：
   - 使用 RingtoneManager 选择铃声
   - 保存到 `settings_prefs` 的 `default_ringtone_uri` 和 `default_ringtone_name`
   - 闹钟没有设置铃声时使用

2. **全局跳过节假日**：
   - 开关控制
   - 保存到 `settings_prefs` 的 `global_skip_holidays`
   - 所有闹钟都会检查此设置（除非单个闹钟自身设置了跳过）

3. **稍后提醒设置**：
   - 点击跳转到 `SnoozeSettingsActivity`

4. **清空数据**：
   - 显示确认对话框
   - 调用 `AlarmDataManager.clearAllAlarms()`
   - 清空 `settings_prefs`
   - 返回主界面刷新列表

5. **关于**：
   - 显示应用版本和功能介绍

---

#### `SnoozeSettingsActivity.java`
**路径**：`com.example.myapplication.SnoozeSettingsActivity`

**功能**：
- 配置稍后提醒的全局设置

**设置项**：

1. **稍后提醒总开关**：
   - 控制是否启用稍后提醒功能
   - 如果关闭，间隔和次数选项会禁用（alpha=0.5）

2. **提醒间隔**（单选）：
   - 5分钟、10分钟、15分钟、30分钟
   - 默认：10分钟

3. **提醒次数**（单选）：
   - 2次、3次、5次、10次
   - 默认：5次
   - 表示最多可以稍后提醒多少次

**数据存储**：
- SharedPreferences 名称：`settings_prefs`
- `snooze_enabled` (boolean): 是否启用
- `snooze_interval` (int): 间隔时间（分钟）
- `snooze_count` (int): 最大次数

**使用场景**：
- 在 `AlarmRingingActivity` 中，用户点击"稍后提醒"按钮时：
  1. 检查 `snooze_enabled` 是否启用
  2. 检查当前闹钟的稍后提醒次数是否已达 `snooze_count` 上限
  3. 如果都通过，使用 `snooze_interval` 作为间隔时间重新调度闹钟

---

## 三、资源文件目录结构（res/）

```
app/src/main/res/
│
├── 📁 drawable/                       # 可绘制资源（图标、形状等）
│   ├── alarm_icon.png                 # 闹钟图标（PNG图片）
│   ├── ic_arrow_right.xml             # 右箭头图标（Vector Drawable）
│   ├── ic_launcher_background.xml     # 应用图标背景层
│   ├── ic_launcher_foreground_alarm.xml  # 应用图标前景层（闹钟图标）
│   └── ic_two_circles.xml             # 两个圆圈图标（Vector Drawable）
│
├── 📁 layout/                         # 布局文件
│   ├── activity_main.xml              # 主界面布局
│   ├── activity_edit_alarm.xml        # 编辑闹钟界面布局
│   ├── activity_alarm_ringing.xml     # 闹钟响铃界面布局
│   ├── activity_settings.xml          # 设置界面布局
│   ├── activity_snooze_settings.xml   # 稍后提醒设置界面布局
│   ├── item_alarm.xml                 # 闹钟列表项布局
│   ├── dialog_repeat.xml              # 重复设置对话框布局
│   ├── dialog_alarm_name.xml          # 闹钟名称输入对话框布局
│   └── dialog_reminder_method.xml     # 提醒方式设置对话框布局
│
├── 📁 mipmap-*/                       # 应用图标资源（不同密度）
│   ├── mipmap-anydpi/                 # 自适应图标配置
│   │   ├── ic_launcher.xml            # 自适应图标配置
│   │   └── ic_launcher_round.xml      # 圆形自适应图标配置
│   └── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/  # 不同密度的图标
│       ├── ic_launcher.webp           # 应用图标
│       └── ic_launcher_round.webp     # 圆形应用图标
│
├── 📁 values/                         # 值资源（字符串、颜色、样式等）
│   ├── colors.xml                     # 颜色定义
│   ├── strings.xml                    # 字符串资源
│   └── themes.xml                     # 主题定义
│
├── 📁 values-night/                   # 夜间模式值资源
│   └── themes.xml                     # 夜间模式主题
│
└── 📁 xml/                            # XML 配置文件
    ├── backup_rules.xml               # 备份规则配置
    └── data_extraction_rules.xml      # 数据提取规则配置
```

---

## 四、资源文件详细说明

### 4.1 可绘制资源（drawable/）

#### `alarm_icon.png`
- **类型**：PNG 图片
- **用途**：闹钟图标
- **使用位置**：应用图标的原始素材

#### `ic_arrow_right.xml`
- **类型**：Vector Drawable
- **用途**：右箭头图标
- **使用位置**：
  - `activity_edit_alarm.xml` 中各个设置项的右侧箭头指示器
- **设计**：简单的右箭头路径

#### `ic_two_circles.xml`
- **类型**：Vector Drawable
- **用途**：两个空心圆圈图标
- **使用位置**：
  - `activity_main.xml` 中右上角的设置按钮
  - `activity_settings.xml` 中各个设置项的图标
- **设计**：两个圆形路径，中心分别位于 (12, 6) 和 (12, 18)，半径 3.5

#### `ic_launcher_background.xml`
- **类型**：Vector Drawable
- **用途**：应用图标背景层（自适应图标）
- **设计**：纯白色背景

#### `ic_launcher_foreground_alarm.xml`
- **类型**：Vector Drawable
- **用途**：应用图标前景层（自适应图标）
- **设计**：应用 `alarm_icon` 并添加 8dp 的 inset 以确保图标在安全区域内显示

---

### 4.2 布局文件（layout/）

#### `activity_main.xml`
**用途**：主界面布局

**布局结构**：
```
CoordinatorLayout（根布局）
├── AppBarLayout（顶部标题栏，白色背景）
│   └── LinearLayout（水平布局）
│       ├── TextView（应用名称）
│       ├── ImageView（添加按钮，ic_input_add）
│       └── ImageView（设置按钮，ic_two_circles）
└── NestedScrollView（可滚动内容区域）
    └── LinearLayout（垂直布局）
        ├── RecyclerView（闹钟列表）
        └── TextView（空状态提示，"暂无闹钟"，默认隐藏）
```

**关键组件**：
- `rvAlarms`: RecyclerView，显示闹钟列表
- `tvNoAlarms`: 空状态提示文本
- `ivAddAlarm`: 添加闹钟按钮
- `ivMore`: 设置按钮

---

#### `activity_edit_alarm.xml`
**用途**：编辑闹钟界面布局

**布局结构**：
```
CoordinatorLayout（根布局）
├── AppBarLayout（顶部标题栏）
│   └── LinearLayout（取消 + 标题 + 完成）
└── NestedScrollView（可滚动内容区域）
    └── LinearLayout（垂直布局）
        ├── LinearLayout（时间选择器：小时 + 分钟 NumberPicker）
        └── LinearLayout（设置项列表，白色背景）
            ├── 重复设置（点击弹出对话框）
            ├── 日期选择（点击弹出 DatePickerDialog）
            ├── 闹钟名称（点击弹出输入对话框）
            ├── 提醒方式（点击弹出对话框）
            ├── 提醒关闭后删除此闹钟（开关）
            ├── 稍后提醒（点击跳转 SnoozeSettingsActivity）
            ├── 跳过节假日（开关）
            └── 稍后提醒报时（开关）
```

**关键组件**：
- `npHour` / `npMinute`: 时间选择器
- `tvRepeatValue`: 重复设置显示
- `tvDateValue`: 日期显示
- `tvAlarmNameValue`: 闹钟名称显示
- `tvReminderMethodValue`: 提醒方式显示
- `tvSnoozeValue`: 稍后提醒设置显示
- `switchSkipHolidays`: 跳过节假日开关
- `switchDeleteAfterDismiss`: 关闭后删除开关
- `switchSnoozeAnnounce`: 稍后提醒报时开关

---

#### `activity_alarm_ringing.xml`
**用途**：闹钟响铃界面布局

**布局特点**：
- 深色背景（#212121）：确保在亮屏时醒目显示
- 大字号时间显示（72sp）：清晰展示闹钟时间
- 全屏显示：即使在锁屏状态下也能显示

**布局结构**：
```
LinearLayout（根布局，垂直居中，深色背景）
├── TextView（闹钟时间，72sp，白色，粗体）
├── TextView（闹钟标签，24sp，灰色）
└── LinearLayout（按钮区域，水平布局）
    ├── MaterialButton（稍后提醒按钮，橙色）
    └── MaterialButton（关闭按钮，橙色）
```

**关键组件**：
- `tvAlarmTime`: 闹钟时间显示
- `tvAlarmLabel`: 闹钟标签显示
- `btnSnooze`: 稍后提醒按钮（可能隐藏）
- `btnDismiss`: 关闭按钮

---

#### `activity_settings.xml`
**用途**：设置界面布局

**布局结构**：
- 顶部标题栏（返回按钮 + 标题）
- 设置项列表（MaterialCardView）：
  1. 默认铃声
  2. 稍后提醒（跳转到 SnoozeSettingsActivity）
  3. 节假日设置（开关 + 说明对话框）
  4. 关于（显示版本信息）
  5. 清空数据（确认对话框）

**关键组件**：
- `tvDefaultRingtone`: 默认铃声显示
- `switchHoliday`: 全局跳过节假日开关

---

#### `activity_snooze_settings.xml`
**用途**：稍后提醒设置界面布局

**布局结构**：
- 顶部标题栏（返回按钮 + 标题）
- 稍后提醒总开关
- 提醒间隔（RadioGroup：5/10/15/30分钟）
- 提醒次数（RadioGroup：2/3/5/10次）

**关键组件**：
- `switchSnoozeEnabled`: 稍后提醒总开关
- `rgInterval`: 间隔单选组
- `rgCount`: 次数单选组

---

#### `item_alarm.xml`
**用途**：闹钟列表项布局

**布局结构**：
```
MaterialCardView（卡片容器，白色背景，圆角12dp）
└── LinearLayout（水平布局）
    ├── LinearLayout（左侧信息区域，垂直布局）
    │   ├── TextView（时间，48sp，粗体）
    │   ├── TextView（重复设置，14sp）
    │   ├── TextView（标签，14sp，可选）
    │   └── TextView（跳过节假日标记，14sp，橙色，可选）
    └── SwitchCompat（右侧开关）
```

**显示规则**：
- 启用状态：时间=黑色，其他=灰色
- 禁用状态：全部=浅灰色
- 跳过节假日标记：仅当 `skipHolidays=true` 时显示

---

#### `dialog_repeat.xml`
**用途**：重复设置对话框布局

**布局结构**：
- 使用 Chip 组件显示 7 个星期选项（周一到周日）
- 用户可以多选

---

#### `dialog_alarm_name.xml`
**用途**：闹钟名称输入对话框布局

**布局结构**：
- TextInputEditText：文本输入框
- 用户输入自定义标签

---

#### `dialog_reminder_method.xml`
**用途**：提醒方式设置对话框布局

**布局结构**：
- SwitchCompat：震动开关
- TextInputEditText：铃声选择器（点击打开系统铃声选择器）

---

### 4.3 应用图标资源（mipmap-*/）

#### `mipmap-anydpi/`
**用途**：自适应图标配置文件

**文件**：
- `ic_launcher.xml`: 自适应图标配置
  - background: `ic_launcher_background`
  - foreground: `ic_launcher_foreground_alarm`
  - monochrome: `ic_launcher_foreground_alarm`

- `ic_launcher_round.xml`: 圆形自适应图标配置（同上）

#### `mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/`
**用途**：不同密度的图标资源

**文件**：
- `ic_launcher.webp`: 应用图标
- `ic_launcher_round.webp`: 圆形应用图标

**密度说明**：
- `hdpi`: 高密度（240 dpi）
- `mdpi`: 中等密度（160 dpi）
- `xhdpi`: 超高密度（320 dpi）
- `xxhdpi`: 超超高密度（480 dpi）
- `xxxhdpi`: 超超超高密度（640 dpi）

---

### 4.4 值资源（values/）

#### `colors.xml`
**用途**：定义应用中使用的颜色

**关键颜色**：
- `background`: 背景色（浅灰色）
- `white`: 白色
- `text_primary`: 主要文本颜色（黑色）
- `text_secondary`: 次要文本颜色（灰色）
- `text_disabled`: 禁用文本颜色（浅灰色）
- `orange_primary`: 橙色主题色（用于按钮和图标）
- `red_primary`: 红色主题色（用于确认按钮）
- `divider`: 分隔线颜色

---

#### `strings.xml`
**用途**：定义所有字符串资源（支持国际化）

**关键字符串**：
- `app_name`: 应用名称
- `add_alarm` / `edit_alarm`: 添加/编辑闹钟
- `repeat_days`: 重复
- `skip_holidays`: 跳过节假日
- `snooze`: 稍后提醒
- `dismiss`: 关闭
- `enabled` / `disabled`: 已启用/已禁用
- 以及其他界面文本

---

#### `themes.xml`
**用途**：定义应用主题

**主题**：
- `Theme.MyApplication`: 应用主主题
- `AlarmRingingTheme`: 闹钟响铃界面主题（全屏、锁屏显示）

---

### 4.5 夜间模式资源（values-night/）

#### `themes.xml`
**用途**：定义夜间模式主题

**说明**：
- 当系统开启深色模式时，会自动使用此主题

---

### 4.6 XML 配置文件（xml/）

#### `backup_rules.xml`
**用途**：定义应用备份规则

**说明**：
- 控制哪些数据需要备份到云端

---

#### `data_extraction_rules.xml`
**用途**：定义数据提取规则

**说明**：
- 控制哪些数据可以被设备上的其他应用访问

---

## 五、文件依赖关系图

```
MainActivity
  ├── AlarmAdapter (显示列表)
  ├── AlarmDataManager (数据管理)
  └── EditAlarmActivity (编辑闹钟)
      ├── AlarmDataManager
      └── SnoozeSettingsActivity (稍后提醒设置)

AlarmDataManager
  ├── Alarm (数据模型)
  ├── AlarmReceiver (创建 PendingIntent)
  └── HolidayUtil (节假日判断)

AlarmReceiver
  ├── AlarmDataManager (验证闹钟是否存在)
  └── AlarmRingingActivity (启动响铃界面)

AlarmRingingActivity
  ├── AlarmDataManager (读取闹钟数据)
  └── AlarmReceiver (停止铃声)

SettingsActivity
  ├── AlarmDataManager (清空数据)
  └── SnoozeSettingsActivity (稍后提醒设置)
```

---

## 六、总结

### Java 代码组织结构
- **model/**: 数据模型层，定义数据结构
- **manager/**: 数据管理层，处理数据持久化和系统闹钟调度
- **receiver/**: 广播接收器层，处理系统闹钟触发
- **adapter/**: 适配器层，管理列表显示
- **util/**: 工具类层，提供通用功能
- **Activity 类**: 界面层，处理用户交互

### 资源文件组织结构
- **drawable/**: 图标和可绘制资源
- **layout/**: 布局文件，定义界面结构
- **mipmap-*/**: 应用图标（不同密度）
- **values/**: 值资源（颜色、字符串、主题）
- **values-night/**: 夜间模式资源
- **xml/**: XML 配置文件

所有文件都遵循 Android 标准目录结构，便于维护和扩展。
