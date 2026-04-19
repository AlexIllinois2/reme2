# 快速测试指南

## 🚀 编译和运行

```bash
# 清理并构建
./gradlew clean assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或者直接运行
./gradlew installDebug
```

## 📋 测试步骤

### 1. 首次启动测试
```bash
# 启动应用
adb shell am start -n io.github.reme2/.MainActivity

# 查看日志
adb logcat | grep -E "FloatWordService|BootReceiver|MainActivity"
```

**预期结果:**
- ✅ 应用正常启动
- ✅ 提示授予悬浮窗权限
- ✅ 点击"启动悬浮窗"后显示通知
- ✅ 屏幕上出现可拖动的悬浮窗

### 2. 开机自启测试
```bash
# 模拟开机广播
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p io.github.reme2

# 查看日志
adb logcat | grep BootReceiver
```

**预期结果:**
- ✅ BootReceiver 收到广播
- ✅ FloatWordService 自动启动
- ✅ 悬浮窗自动显示

### 3. 保活测试
```bash
# 查看服务状态
adb shell dumpsys activity services | grep FloatWordService

# 强制停止服务
adb shell am force-stop io.github.reme2

# 等待几秒后检查是否重启
adb shell ps | grep reme2
```

**预期结果:**
- ✅ 服务显示为 foreground service
- ✅ 被杀死后自动重启(START_STICKY)

### 4. 配置更新测试
```bash
# 在应用中修改文本
# 点击"更新文本"按钮

# 检查 SharedPreferences
adb shell run-as io.github.reme2 cat shared_prefs/floating_window_prefs.xml
```

**预期结果:**
- ✅ 悬浮窗文字立即更新
- ✅ 配置保存到 XML 文件
- ✅ 重启后配置依然有效

### 5. 位置保存测试
```bash
# 拖动悬浮窗到新位置
# 停止服务
# 重新启动服务

# 检查位置是否恢复
adb shell run-as io.github.reme2 cat shared_prefs/floating_window_prefs.xml
```

**预期结果:**
- ✅ 悬浮窗在上次关闭的位置重新出现

## 🔍 调试技巧

### 查看实时日志
```bash
# 只看本应用的日志
adb logcat -s FloatWordService:D BootReceiver:D MainActivity:D

# 查看所有相关日志
adb logcat | grep -E "reme2|FloatWord|BootReceiver"
```

### 检查权限状态
```bash
# 检查悬浮窗权限
adb shell appops get io.github.reme2 SYSTEM_ALERT_WINDOW

# 检查通知权限
adb shell dumpsys notification --rank | grep io.github.reme2
```

### 检查电池优化状态
```bash
# 查看是否在白名单中
adb shell dumpsys deviceidle whitelist | grep io.github.reme2
```

### 模拟低内存杀死
```bash
# 查找进程ID
adb shell ps | grep reme2

# 杀死进程
adb shell kill <PID>

# 观察是否自动重启
adb shell ps | grep reme2
```

## ⚠️ 常见问题排查

### 问题1: 服务无法启动
```bash
# 检查是否有悬浮窗权限
adb shell appops get io.github.reme2 SYSTEM_ALERT_WINDOW

# 手动授予权限
adb shell pm grant io.github.reme2 android.permission.SYSTEM_ALERT_WINDOW
```

### 问题2: 开机广播未收到
```bash
# 检查接收器是否注册
adb shell dumpsys package io.github.reme2 | grep -A 10 "ReceiverResolver"

# 手动发送广播测试
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p io.github.reme2
```

### 问题3: 通知未显示
```bash
# 检查通知渠道
adb shell dumpsys notification | grep FloatWordServiceChannel

# 检查通知权限(Android 13+)
adb shell appops get io.github.reme2 POST_NOTIFICATIONS
```

### 问题4: 悬浮窗不显示
```bash
# 检查 WindowManager 错误
adb logcat | grep -E "WindowManager|addView"

# 检查悬浮窗视图层级
adb shell dumpsys window windows | grep -A 5 "TYPE_APPLICATION_OVERLAY"
```

## 📊 性能监控

### 监控内存使用
```bash
# 查看内存占用
adb shell dumpsys meminfo io.github.reme2

# 持续监控
watch -n 1 "adb shell dumpsys meminfo io.github.reme2 | grep TOTAL"
```

### 监控CPU使用
```bash
# 查看CPU占用
adb shell top | grep reme2
```

### 监控电池消耗
```bash
# 查看电池统计
adb shell dumpsys batterystats | grep io.github.reme2
```

## 🎯 压力测试

### 长时间运行测试
```bash
# 让应用运行24小时
# 每隔1小时检查一次服务状态
for i in {1..24}; do
    echo "Hour $i:"
    adb shell ps | grep reme2
    sleep 3600
done
```

### 频繁启停测试
```bash
# 循环启动停止100次
for i in {1..100}; do
    adb shell am startservice -n io.github.reme2/.FloatWordService
    sleep 2
    adb shell am stopservice -n io.github.reme2/.FloatWordService
    sleep 1
    echo "Cycle $i completed"
done
```

## ✅ 验收标准

- [ ] 应用安装后能正常启动
- [ ] 授予权限后悬浮窗能显示
- [ ] 悬浮窗可以拖动且位置能保存
- [ ] 文字内容和缩放比例能保存
- [ ] 重启手机后悬浮窗自动显示
- [ ] 服务被杀死后能自动重启
- [ ] 前台服务通知持续显示
- [ ] 配置更新能实时反映到悬浮窗
- [ ] 应用在后台运行时悬浮窗依然可见
- [ ] 长按Home键清除最近任务时服务不停止

## 📝 日志示例

正常的启动日志应该类似:
```
D/MainActivity: Activity onCreate
D/MainActivity: Loaded config: scale=1.0
D/FloatWordService: Service onCreate
D/FloatWordService: Loaded config: text=delve..., pos=(0,100), scale=1.0
D/FloatWordService: Requested ignore battery optimizations
D/FloatWordService: Creating floating window
D/FloatWordService: Floating window added successfully
D/FloatWordService: Keep-alive mechanism initialized
```

开机自启日志:
```
D/BootReceiver: Received broadcast: android.intent.action.BOOT_COMPLETED
D/BootReceiver: System boot completed, starting FloatWordService
D/FloatWordService: Service onCreate
D/FloatWordService: Floating window added successfully
```
