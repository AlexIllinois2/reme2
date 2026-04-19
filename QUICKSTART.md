# 🚀 快速开始指南

## 一分钟上手

### 重新打包
 ./gradlew clean assembleDebug

### 1️⃣ 编译安装
```bash
./gradlew installDebug
```

### 2️⃣ 首次运行
1. 打开应用
2. 授予悬浮窗权限(自动跳转)
3. 点击 **"启动悬浮窗"** 按钮
4. 允许电池优化请求

### 3️⃣ 完成!
- ✅ 悬浮窗已显示在屏幕上
- ✅ 可以拖动调整位置
- ✅ 可以编辑文字内容
- ✅ 可以缩放大小

---

## 📱 主要功能

### 配置悬浮窗
```
┌─────────────────────────────┐
│  [输入框] 编辑要显示的文字    │
│                             │
│  [更新文本] 保存并应用       │
│                             │
│  [缩小] [启动/停止] [放大]  │
│                             │
│  [⚡ 保活优化设置]           │
└─────────────────────────────┘
```

### 操作说明
- **编辑文字**: 在输入框修改,点击"更新文本"
- **调整大小**: 使用"放大(+)"和"缩小(-)"按钮
- **移动位置**: 直接拖动屏幕上的悬浮窗
- **启停服务**: 点击"启动悬浮窗"/"停止悬浮窗"
- **优化保活**: 点击"⚡ 保活优化设置"查看引导

---

## 🔧 开机自启设置

### 通用步骤
1. 点击 **"⚡ 保活优化设置"** 按钮
2. 按照引导完成系统设置
3. 重启手机测试

### 各品牌手机额外设置

#### 小米/Redmi
```
设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用
设置 → 省电与电池 → 应用智能省电 → 本应用 → 无限制
```

#### 华为/荣耀
```
手机管家 → 应用启动管理 → 本应用 → 手动管理(全选)
```

#### OPPO/Realme
```
手机管家 → 权限隐私 → 自启动管理 → 允许
设置 → 电池 → 应用耗电管理 → 允许完全后台行为
```

#### vivo/iQOO
```
i管家 → 应用管理 → 权限管理 → 自启动 → 允许
设置 → 电池 → 后台高耗电应用 → 允许
```

---

## ❓ 常见问题

### Q1: 悬浮窗不显示?
**A:** 检查是否授予悬浮窗权限
```
设置 → 应用 → 本应用 → 权限 → 悬浮窗 → 允许
```

### Q2: 重启后没自动显示?
**A:** 检查自启动权限和电池优化
```
1. 确认已允许自启动
2. 确认已关闭电池优化
3. 查看日志: adb logcat | grep BootReceiver
```

### Q3: 几分钟后悬浮窗消失?
**A:** 被系统杀死了,需要加强保活设置
```
1. 点击"⚡ 保活优化设置"按引导操作
2. 在手机管家中锁定最近任务
3. 设置为"无限制"或"允许后台活动"
```

### Q4: 如何修改显示的文字?
**A:** 
```
1. 打开应用主界面
2. 在输入框编辑文字
3. 点击"更新文本"按钮
4. 悬浮窗会立即更新
```

### Q5: 如何彻底停止?
**A:** 
```
1. 打开应用
2. 点击"停止悬浮窗"按钮
3. 或者: 设置 → 应用 → 本应用 → 强制停止
```

---

## 🛠️ 开发者调试

### 查看日志
```bash
# 实时日志
adb logcat -s FloatWordService:D BootReceiver:D MainActivity:D

# 查看所有相关日志
adb logcat | grep -E "reme2|FloatWord|Boot"
```

### 检查服务状态
```bash
# 查看是否运行
adb shell ps | grep reme2

# 查看服务详情
adb shell dumpsys activity services | grep FloatWord
```

### 模拟开机广播
```bash
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p io.github.reme2
```

### 查看配置数据
```bash
adb shell run-as io.github.reme2 cat shared_prefs/floating_window_prefs.xml
```

---

## 📚 更多文档

- **README_保活优化.md** - 详细的保活策略和技术说明
- **TESTING.md** - 完整的测试指南和故障排查
- **SUMMARY.md** - 项目总结和技术亮点

---

## ⚡ 快捷命令

```bash
# 一键安装并启动
./gradlew installDebug && adb shell am start -n io.github.reme2/.MainActivity

# 查看实时日志
adb logcat -c && adb logcat | grep -E "FloatWordService|BootReceiver"

# 测试开机自启
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p io.github.reme2

# 强制重启服务
adb shell am force-stop io.github.reme2 && sleep 2 && adb shell am startservice -n io.github.reme2/.FloatWordService

# 导出配置备份
adb shell run-as io.github.reme2 cp /data/data/io.github.reme2/shared_prefs/floating_window_prefs.xml /sdcard/backup.xml
```

---

## 🎯 下一步

1. ✅ 完成上述基础设置
2. ✅ 测试开机自启功能
3. ✅ 根据个人需求调整悬浮窗
4. 📖 阅读详细文档了解高级功能

**享受你的单词悬浮窗吧! 🎉**
