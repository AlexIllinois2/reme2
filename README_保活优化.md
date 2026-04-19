# 单词悬浮窗应用 - 保活优化说明

## 🎯 优化目标
实现开机自动显示悬浮窗,并尽可能保证不被系统杀死。

## ✅ 已实现的保活策略

### 1. **前台服务 (Foreground Service)**
- ✅ 使用 `FloatWordService` 作为前台服务承载悬浮窗
- ✅ 设置 `foregroundServiceType="specialUse"` (Android 14+)
- ✅ 启动时显示持久化通知,提高服务优先级
- ✅ 返回 `START_STICKY`,服务被杀后自动重启

### 2. **开机自启**
- ✅ 创建 `BootReceiver` 监听系统广播
- ✅ 监听多个开机事件:
  - `BOOT_COMPLETED` (标准开机完成)
  - `LOCKED_BOOT_COMPLETED` (直接启动模式)
  - `QUICKBOOT_POWERON` (快速启动)
  - `ACTION_SHUTDOWN` / `REBOOT` (关机/重启)
- ✅ 设置高优先级 `android:priority="1000"`
- ✅ 支持 Android 7+ 的直接启动模式 `directBootAware="true"`

### 3. **权限申请**
已申请以下关键权限:
- ✅ `SYSTEM_ALERT_WINDOW` - 悬浮窗权限
- ✅ `WAKE_LOCK` - 保持CPU唤醒
- ✅ `RECEIVE_BOOT_COMPLETED` - 接收开机广播
- ✅ `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - 请求忽略电池优化
- ✅ `DISABLE_KEYGUARD` - 禁用锁屏
- ✅ `FOREGROUND_SERVICE` - 前台服务权限
- ✅ `POST_NOTIFICATIONS` - 发送通知(Android 13+)

### 4. **应用级别保活**
- ✅ `android:persistent="true"` - 声明为持久化应用
- ✅ `android:stopWithTask="false"` - 任务清除时不停止服务
- ✅ 独立进程 `android:process=":FloatWordService"` - 隔离主进程

### 5. **运行时保活**
- ✅ 启动时自动请求忽略电池优化
- ✅ 低优先级通知减少用户打扰
- ✅ SharedPreferences 持久化保存配置

## 📱 使用说明

### 首次使用
1. 安装应用后打开
2. 授予悬浮窗权限(会自动跳转设置页面)
3. 授予通知权限(Android 13+)
4. 点击"启动悬浮窗"按钮
5. 当提示"请求忽略电池优化"时,选择"允许"

### 配置悬浮窗
- **修改文字**: 在输入框编辑文本,点击"更新文本"
- **调整大小**: 使用"放大(+)"和"缩小(-)"按钮
- **拖动位置**: 直接在屏幕上拖动悬浮窗
- **停止服务**: 点击"停止悬浮窗"按钮

### 开机自启
- 确保手机未禁止应用自启动
- 在手机管家/电池优化中将本应用设为"无限制"或"允许后台活动"
- 重启手机后悬浮窗应自动显示

## ⚠️ 注意事项

### 不同品牌手机的额外设置

#### 小米/Redmi
1. 设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用
2. 设置 → 省电与电池 → 应用智能省电 → 本应用 → 无限制
3. 关闭"MIUI优化"(开发者选项中)

#### 华为/荣耀
1. 手机管家 → 应用启动管理 → 本应用 → 手动管理(允许所有)
2. 设置 → 电池 → 更多电池设置 → 休眠时始终保持网络连接

#### OPPO/Realme/一加
1. 手机管家 → 权限隐私 → 自启动管理 → 允许本应用
2. 设置 → 电池 → 应用耗电管理 → 本应用 → 允许完全后台行为

#### vivo/iQOO
1. i管家 → 应用管理 → 权限管理 → 自启动 → 允许
2. 设置 → 电池 → 后台高耗电应用 → 允许本应用

#### 三星
1. 设置 → 应用程序 → 本应用 → 电池 → 无限制
2. 设置 → 常规管理 → 重置应用程序偏好设置

### 如果悬浮窗仍被杀死

1. **检查电池优化**
   ```
   设置 → 电池 → 电池优化 → 找到本应用 → 不优化
   ```

2. **检查后台限制**
   ```
   设置 → 应用 → 本应用 → 电池 → 无限制/允许后台活动
   ```

3. **锁定最近任务**
   - 打开最近任务列表
   - 找到本应用
   - 点击锁定图标(如果有)

4. **开发者选项设置**
   ```
   设置 → 开发者选项 → 
   - 不保留活动: 关闭
   - 后台进程限制: 无限制
   ```

## 🔧 技术细节

### 服务生命周期
```
开机广播 → BootReceiver → startForegroundService() → FloatWordService.onCreate()
                                                        ↓
                                                 创建悬浮窗 + 显示通知
                                                        ↓
                                                 START_STICKY (自动重启)
```

### 数据持久化
所有配置保存在 `SharedPreferences`:
- `text_content`: 悬浮窗文字内容
- `pos_x`, `pos_y`: 悬浮窗位置
- `zoom_scale`: 缩放比例

### 通信机制
Activity 通过 `startService()` 传递 Intent  extras 来更新服务:
```java
intent.putExtra("action", "update_text");
intent.putExtra("text", newText);
```

## 📊 兼容性

- ✅ Android 6.0+ (API 23+)
- ✅ Android 8.0+ 前台服务适配
- ✅ Android 10+ 后台启动限制处理
- ✅ Android 13+ 通知权限处理
- ✅ Android 14+ 前台服务类型声明

## 🐛 故障排查

### 问题1: 开机后悬浮窗未显示
**解决:**
1. 检查是否授予悬浮窗权限
2. 查看日志: `adb logcat | grep BootReceiver`
3. 确认手机未禁止应用自启动

### 问题2: 悬浮窗几分钟后消失
**解决:**
1. 检查电池优化设置
2. 确认前台服务通知未被清除
3. 查看日志: `adb logcat | grep FloatWordService`

### 问题3: 无法启动服务
**解决:**
1. Android 8.0+ 必须使用 `startForegroundService()`
2. 检查是否有悬浮窗权限
3. 查看崩溃日志

## 📝 后续优化建议

如需更强的保活效果,可考虑:
1. **双进程守护**: 创建辅助进程相互监控
2. **JobScheduler**: 定期唤醒服务
3. **WorkManager**: 周期性任务保活
4. **账户同步**: 利用系统同步机制
5. **音乐播放**: 伪装成音乐播放器(需添加音频功能)
6. **无障碍服务**: 利用AccessibilityService的高优先级

> ⚠️ 注意: 过度保活可能违反Google Play政策,仅用于个人学习和本地使用。
