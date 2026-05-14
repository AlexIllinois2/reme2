package io.github.reme2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 悬浮窗前台服务
 * 职责：在后台持久运行，显示可拖拽的单词悬浮窗
 */
public class FloatWordService extends Service {

    private static final String TAG = "FloatWordService";
    
    // Preference 相关
    private static final String PREF_NAME = "floating_window_prefs";
    private static final String KEY_TEXT_CONTENT = "text_content";
    private static final String KEY_POS_X = "pos_x";
    private static final String KEY_POS_Y = "pos_y";
    private static final String KEY_ZOOM_SCALE = "zoom_scale";
    private static final String KEY_AUTO_HIDE_DURATION = "auto_hide_duration"; // 自动隐藏时长(分钟)
    private static final String KEY_IS_HIDDEN = "is_hidden"; // 是否处于隐藏状态
    private static final String KEY_NEXT_DISPLAY_TIME = "next_display_time"; // 下一次显示时间戳

    // 默认值
    private static final String DEFAULT_TEXT = "delve\n翻找；\n探索，\n探究";
    private static final float DEFAULT_SCALE = 1.0f;
    private static final int DEFAULT_Y_POS = 100;
    private static final int DEFAULT_AUTO_HIDE_DURATION = 30; // 默认30分钟

    // 通知渠道
    private static final String CHANNEL_ID = "FloatWordServiceChannel";
    private static final int NOTIFICATION_ID = 1001;

    // UI Components
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvFloatingText;

    // 状态数据
    private String currentConfigText = DEFAULT_TEXT;
    private float currentScale = DEFAULT_SCALE;
    private int savedX = 0;
    private int savedY = DEFAULT_Y_POS;
    private int autoHideDuration = DEFAULT_AUTO_HIDE_DURATION; // 自动隐藏时长(分钟)
    private boolean isHidden = false; // 是否处于隐藏状态
    private long nextDisplayTime = 0; // 下一次显示时间戳
    
    // 保活相关
    private android.os.Handler keepAliveHandler;
    private Runnable keepAliveRunnable;
    private static final long KEEP_ALIVE_INTERVAL = 30000; // 30秒心跳
    
    // 定时隐藏相关
    private android.os.Handler hideHandler;
    private Runnable hideRunnable;
    private long hideStartTime = 0; // 隐藏开始时间戳

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        
        // 加载配置
        loadPreferences();
        
        // 创建通知渠道并启动前台服务
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        
        // 请求忽略电池优化
        requestIgnoreBatteryOptimizations();
        
        // 初始化保活机制
        initKeepAlive();
        
        // 检查是否需要继续隐藏
        long currentTime = System.currentTimeMillis();
        if (nextDisplayTime > 0 && currentTime < nextDisplayTime) {
            // 还没到显示时间，保持隐藏状态
            Log.d(TAG, "Service restarted but next display time not reached, staying hidden for " + 
                    ((nextDisplayTime - currentTime) / 1000 / 60) + " more minutes");
            isHidden = true;
            startAutoHideTimer();
            updateNotificationForHiddenState();
        } else {
            // 到了显示时间或者没有设置，正常显示
            Log.d(TAG, "Service restarted, showing window normally");
            isHidden = false;
            nextDisplayTime = 0;
            saveConfig(KEY_IS_HIDDEN, false);
            saveConfig(KEY_NEXT_DISPLAY_TIME, 0L);
            createFloatingWindow();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        // 处理来自 Activity 的更新请求
        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            
            if ("update_text".equals(action)) {
                String newText = intent.getStringExtra("text");
                if (newText != null) {
                    updateText(newText);
                    Log.d(TAG, "Updated text from intent");
                }
            } else if ("update_scale".equals(action)) {
                float newScale = intent.getFloatExtra("scale", DEFAULT_SCALE);
                updateScale(newScale);
                Log.d(TAG, "Updated scale from intent: " + newScale);
            } else if ("toggle_visibility".equals(action)) {
                // 切换悬浮窗显示/隐藏
                toggleVisibility();
                Log.d(TAG, "Toggled visibility from intent");
            } else if ("set_auto_hide_duration".equals(action)) {
                int duration = intent.getIntExtra("duration", DEFAULT_AUTO_HIDE_DURATION);
                setAutoHideDuration(duration);
                Log.d(TAG, "Set auto hide duration: " + duration + " minutes");
            }
        }
        
        // 检查是否需要显示悬浮窗
        long currentTime = System.currentTimeMillis();
        if (floatingView == null && !isHidden && (nextDisplayTime == 0 || currentTime >= nextDisplayTime)) {
            createFloatingWindow();
        }
        
        // START_STICKY: 服务被杀后自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        
        // 停止保活机制
        stopKeepAlive();
        
        // 停止定时隐藏计时器
        stopAutoHideTimer();
        
        destroyFloatingWindow();
        super.onDestroy();
    }

    /**
     * 加载 SharedPreferences 配置
     */
    private void loadPreferences() {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentConfigText = pref.getString(KEY_TEXT_CONTENT, DEFAULT_TEXT);
        savedX = pref.getInt(KEY_POS_X, 0);
        savedY = pref.getInt(KEY_POS_Y, DEFAULT_Y_POS);
        currentScale = pref.getFloat(KEY_ZOOM_SCALE, DEFAULT_SCALE);
        autoHideDuration = pref.getInt(KEY_AUTO_HIDE_DURATION, DEFAULT_AUTO_HIDE_DURATION);
        isHidden = pref.getBoolean(KEY_IS_HIDDEN, false);
        nextDisplayTime = pref.getLong(KEY_NEXT_DISPLAY_TIME, 0);
        
        Log.d(TAG, "Loaded config: text=" + currentConfigText.substring(0, Math.min(20, currentConfigText.length())) 
                + ", pos=(" + savedX + "," + savedY + "), scale=" + currentScale
                + ", autoHideDuration=" + autoHideDuration + ", isHidden=" + isHidden
                + ", nextDisplayTime=" + nextDisplayTime);
    }

    /**
     * 保存配置到 SharedPreferences
     */
    private void saveConfig(String key, Object value) {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        }
        editor.apply();
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW // 低重要性，减少打扰
            );
            channel.setDescription("保持单词悬浮窗运行");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 构建前台服务通知
     */
    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("单词悬浮窗运行中")
            .setContentText(currentConfigText.split("\n")[0]) // 显示第一行文字
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 设置为持续通知
            .build();
    }

    /**
     * 请求忽略电池优化
     */
    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                Log.d(TAG, "Requested ignore battery optimizations");
            } catch (Exception e) {
                Log.e(TAG, "Failed to request ignore battery optimizations", e);
            }
        }
    }

    /**
     * 创建悬浮窗
     */
    private void createFloatingWindow() {
        Log.d(TAG, "Creating floating window");
        
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "No overlay permission!");
                return;
            }
        }

        // 1. 加载布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null);
        tvFloatingText = floatingView.findViewById(R.id.tv_floating_text);

        // 2. 初始化 WindowManager
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }

        // 3. 配置 LayoutParams
        final WindowManager.LayoutParams params = buildLayoutParams();

        // 4. 应用数据到视图
        applyDataToView();

        // 5. 绑定触摸移动逻辑
        setupTouchEventListener(params);

        // 6. 添加到窗口
        try {
            windowManager.addView(floatingView, params);
            Log.d(TAG, "Floating window added successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error creating overlay", e);
        }
    }

    /**
     * 构建悬浮窗的 LayoutParams
     */
    private WindowManager.LayoutParams buildLayoutParams() {
        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = savedX;
        params.y = savedY;
        return params;
    }

    /**
     * 将内存中的数据应用到 View 上
     */
    private void applyDataToView() {
        if (tvFloatingText != null) {
            tvFloatingText.setText(currentConfigText);
            // 强制设置文字颜色为白色，避免深色模式下变成黑色
            tvFloatingText.setTextColor(0xFFFFFFFF); // 纯白色 #FFFFFF
            updateTextSize(tvFloatingText);
        }
    }

    /**
     * 销毁悬浮窗（并保存位置）
     */
    private void destroyFloatingWindow() {
        if (floatingView != null && windowManager != null) {
            // 移除前保存位置
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) floatingView.getLayoutParams();
            saveConfig(KEY_POS_X, p.x);
            saveConfig(KEY_POS_Y, p.y);

            try {
                windowManager.removeView(floatingView);
                Log.d(TAG, "Floating window removed");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay", e);
            } finally {
                floatingView = null;
                tvFloatingText = null;
            }
        }
    }

    /**
     * 设置触摸事件监听器（拖拽逻辑）
     */
    private void setupTouchEventListener(final WindowManager.LayoutParams params) {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 手指抬起时保存位置
                        saveConfig(KEY_POS_X, params.x);
                        saveConfig(KEY_POS_Y, params.y);
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 更新悬浮窗文字大小
     */
    private void updateTextSize(TextView tv) {
        float baseTextSize = 14; // 基准字号
        float newTextSize = baseTextSize * currentScale;
        tv.setTextSize(Math.max(8, newTextSize)); // 限制最小字号
    }

    /**
     * 公开方法：更新悬浮窗文字内容
     */
    public void updateText(String newText) {
        this.currentConfigText = newText;
        saveConfig(KEY_TEXT_CONTENT, newText);
        
        if (tvFloatingText != null) {
            tvFloatingText.setText(newText);
            // 强制设置文字颜色为白色，避免深色模式下变成黑色
            tvFloatingText.setTextColor(0xFFFFFFFF); // 纯白色 #FFFFFF
        }
        
        // 更新通知内容
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /**
     * 公开方法：更新缩放比例
     */
    public void updateScale(float newScale) {
        this.currentScale = newScale;
        saveConfig(KEY_ZOOM_SCALE, newScale);
        
        if (tvFloatingText != null) {
            updateTextSize(tvFloatingText);
        }
    }
    
    /**
     * 切换悬浮窗显示/隐藏状态
     */
    private void toggleVisibility() {
        if (isHidden) {
            // 当前是隐藏状态，恢复显示
            showFloatingWindow();
        } else {
            // 当前是显示状态，开始隐藏
            hideFloatingWindow();
        }
    }
    
    /**
     * 隐藏悬浮窗并启动定时器
     */
    private void hideFloatingWindow() {
        Log.d(TAG, "Hiding floating window for " + autoHideDuration + " minutes");
        
        // 保存当前位置
        if (floatingView != null && windowManager != null) {
            WindowManager.LayoutParams p = (WindowManager.LayoutParams) floatingView.getLayoutParams();
            saveConfig(KEY_POS_X, p.x);
            saveConfig(KEY_POS_Y, p.y);
            
            // 移除悬浮窗
            try {
                windowManager.removeView(floatingView);
                floatingView = null;
                tvFloatingText = null;
                Log.d(TAG, "Floating window removed for hiding");
            } catch (Exception e) {
                Log.e(TAG, "Error removing overlay for hiding", e);
            }
        }
        
        // 标记为隐藏状态
        isHidden = true;
        saveConfig(KEY_IS_HIDDEN, true);
        
        // 计算并保存下一次显示时间
        hideStartTime = System.currentTimeMillis();
        nextDisplayTime = hideStartTime + autoHideDuration * 60 * 1000L;
        saveConfig(KEY_NEXT_DISPLAY_TIME, nextDisplayTime);
        
        // 启动定时器
        startAutoHideTimer();
        
        // 更新通知
        updateNotificationForHiddenState();
        
        Toast.makeText(this, "悬浮窗已隐藏，" + autoHideDuration + "分钟后自动恢复", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 恢复显示悬浮窗
     */
    private void showFloatingWindow() {
        Log.d(TAG, "Showing floating window");
        
        // 停止定时器
        stopAutoHideTimer();
        
        // 标记为显示状态
        isHidden = false;
        saveConfig(KEY_IS_HIDDEN, false);
        
        // 清除下一次显示时间
        nextDisplayTime = 0;
        saveConfig(KEY_NEXT_DISPLAY_TIME, 0L);
        
        // 重新创建悬浮窗
        createFloatingWindow();
        
        // 更新通知
        updateNotificationForShownState();
        
        Toast.makeText(this, "悬浮窗已恢复显示", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 设置自动隐藏时长
     */
    public void setAutoHideDuration(int duration) {
        if (duration <= 0) {
            Log.w(TAG, "Invalid auto hide duration: " + duration + ", using default");
            duration = DEFAULT_AUTO_HIDE_DURATION;
        }
        
        this.autoHideDuration = duration;
        saveConfig(KEY_AUTO_HIDE_DURATION, duration);
        Log.d(TAG, "Auto hide duration set to: " + duration + " minutes");
        
        // 如果当前正在隐藏，重新计算下一次显示时间并启动定时器
        if (isHidden) {
            stopAutoHideTimer();
            hideStartTime = System.currentTimeMillis();
            nextDisplayTime = hideStartTime + autoHideDuration * 60 * 1000L;
            saveConfig(KEY_NEXT_DISPLAY_TIME, nextDisplayTime);
            startAutoHideTimer();
        }
    }
    
    /**
     * 启动自动隐藏定时器
     */
    private void startAutoHideTimer() {
        if (hideHandler == null) {
            hideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        hideRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                
                if (nextDisplayTime > 0 && currentTime >= nextDisplayTime) {
                    // 时间到了，恢复显示
                    Log.d(TAG, "Auto hide timer expired, showing window");
                    showFloatingWindow();
                } else if (nextDisplayTime > 0) {
                    // 继续等待
                    long remainingTime = nextDisplayTime - currentTime;
                    Log.d(TAG, "Auto hide timer continuing, remaining: " + (remainingTime / 1000 / 60) + " minutes");
                    hideHandler.postDelayed(this, Math.min(remainingTime, 60000)); // 最多每分钟检查一次
                } else {
                    // 没有设置下一次显示时间，停止定时器
                    Log.d(TAG, "No next display time set, stopping auto hide timer");
                    stopAutoHideTimer();
                }
            }
        };
        
        // 立即执行第一次检查
        hideHandler.post(hideRunnable);
        Log.d(TAG, "Auto hide timer started for " + autoHideDuration + " minutes");
    }
    
    /**
     * 停止自动隐藏定时器
     */
    private void stopAutoHideTimer() {
        if (hideHandler != null && hideRunnable != null) {
            hideHandler.removeCallbacks(hideRunnable);
            hideHandler = null;
            hideRunnable = null;
            Log.d(TAG, "Auto hide timer stopped");
        }
    }
    
    /**
     * 更新通知为隐藏状态
     */
    private void updateNotificationForHiddenState() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("单词悬浮窗已隐藏")
                .setContentText(autoHideDuration + "分钟后自动恢复")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
            manager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 更新通知为显示状态
     */
    private void updateNotificationForShownState() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }
    
    //region 保活机制
    /**
     * 初始化保活机制
     * 定期更新通知以保持服务活跃
     */
    private void initKeepAlive() {
        keepAliveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        keepAliveRunnable = new Runnable() {
            @Override
            public void run() {
                // 定期更新通知时间戳，保持服务活跃
                if (floatingView != null) {
                    NotificationManager manager = 
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, buildNotification());
                        Log.d(TAG, "Keep-alive heartbeat sent");
                    }
                    
                    // 检查悬浮窗是否仍然存在
                    try {
                        floatingView.getLocationOnScreen(new int[2]);
                    } catch (Exception e) {
                        Log.w(TAG, "Floating view may be detached, recreating...");
                        createFloatingWindow();
                    }
                }
                
                // 继续下一次心跳
                keepAliveHandler.postDelayed(this, KEEP_ALIVE_INTERVAL);
            }
        };
        
        // 启动心跳
        keepAliveHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_INTERVAL);
        Log.d(TAG, "Keep-alive mechanism initialized");
    }
    
    /**
     * 停止保活机制
     */
    private void stopKeepAlive() {
        if (keepAliveHandler != null && keepAliveRunnable != null) {
            keepAliveHandler.removeCallbacks(keepAliveRunnable);
            keepAliveHandler = null;
            keepAliveRunnable = null;
            Log.d(TAG, "Keep-alive mechanism stopped");
        }
    }
    //endregion
    
}
