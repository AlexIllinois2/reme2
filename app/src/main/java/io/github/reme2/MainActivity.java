package io.github.reme2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面 Activity
 * 职责：管理页面生命周期、初始化UI组件、协调配置存储与服务控制
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    // Preference 相关
    private static final String PREF_NAME = "floating_window_prefs";
    private static final String KEY_TEXT_CONTENT = "text_content";
    private static final String KEY_ZOOM_SCALE = "zoom_scale";
    private static final String KEY_AUTO_HIDE_DURATION = "auto_hide_duration"; // 自动隐藏时长(分钟)
    private static final String KEY_IS_FIRST_LAUNCH = "is_first_launch"; // 是否首次启动

    // 默认值
    private static final String DEFAULT_TEXT = "delve\n翻找；\n探索，\n探究";
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float ZOOM_STEP = 0.1f;
    private static final int DEFAULT_AUTO_HIDE_DURATION = 30; // 默认30分钟

    // UI Components
    private EditText etConfigText, etAutoHideDuration;
    private Button btnSaveConfig, btnZoomIn, btnZoomOut, btnOptimize, btnToggleVisibility;

    // 状态数据
    private String currentConfigText = DEFAULT_TEXT;
    private float currentScale = DEFAULT_SCALE;
    private int autoHideDuration = DEFAULT_AUTO_HIDE_DURATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.d(TAG, "Activity onCreate");

        // 初始化流程
        initPreferences(); // 1. 先读取数据
        initViews();       // 2. 再初始化视图（依赖数据）
        setupEventListeners(); // 3. 绑定事件
        checkOverlayPermission(); // 4. 检查权限
    }

    //region 初始化模块
    /**
     * 初始化 SharedPreferences 数据
     */
    private void initPreferences() {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentConfigText = pref.getString(KEY_TEXT_CONTENT, DEFAULT_TEXT);
        currentScale = pref.getFloat(KEY_ZOOM_SCALE, DEFAULT_SCALE);
        autoHideDuration = pref.getInt(KEY_AUTO_HIDE_DURATION, DEFAULT_AUTO_HIDE_DURATION);
        
        // 检查是否是首次启动（通过检查是否有自定义配置）
        // 如果文本内容与默认值完全相同，且没有其他配置项，则认为是首次启动
        boolean hasCustomConfig = pref.contains(KEY_ZOOM_SCALE) || 
                                  pref.contains(KEY_AUTO_HIDE_DURATION) ||
                                  (pref.contains(KEY_TEXT_CONTENT) && !currentConfigText.equals(DEFAULT_TEXT));
        
        if (!hasCustomConfig) {
            // 首次启动，标记
            pref.edit().putBoolean(KEY_IS_FIRST_LAUNCH, true).apply();
            Log.d(TAG, "First launch detected - no custom config");
        } else {
            pref.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply();
            Log.d(TAG, "Not first launch - has custom config");
        }
        
        Log.d(TAG, "Loaded config: text=" + currentConfigText.substring(0, Math.min(20, currentConfigText.length())) 
                + ", scale=" + currentScale + ", autoHideDuration=" + autoHideDuration);
    }

    /**
     * 初始化视图组件
     */
    private void initViews() {
        etConfigText = findViewById(R.id.et_config_text);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        
        // 新增的UI控件
        etAutoHideDuration = findViewById(R.id.et_auto_hide_duration);
        btnToggleVisibility = findViewById(R.id.btn_toggle_visibility);
        
        // 优化按钮
        try {
            btnOptimize = findViewById(R.id.btn_optimize);
        } catch (Exception e) {
            btnOptimize = null;
        }

        // 设置初始文本
        etConfigText.setText(currentConfigText);
        
        // 设置自动隐藏时长初始值
        etAutoHideDuration.setText(String.valueOf(autoHideDuration));
    }

    /**
     * 绑定所有按钮点击事件
     */
    private void setupEventListeners() {
        if (btnOptimize != null) {
            btnOptimize.setOnClickListener(v -> showOptimizationGuide());
        }
        btnSaveConfig.setOnClickListener(v -> saveConfigText());
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnZoomOut.setOnClickListener(v -> zoomOut());
        
        // 隐藏/显示悬浮窗按钮
        if (btnToggleVisibility != null) {
            btnToggleVisibility.setOnClickListener(v -> toggleVisibility());
        }
    }
    //endregion

    //region 权限检查
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1001);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "未授予权限，悬浮窗无法显示", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    //endregion

    //region 业务逻辑方法
    /**
     * 检查服务是否运行
     */
    private boolean isServiceRunning() {
        android.app.ActivityManager manager = 
            (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : 
                 manager.getRunningServices(Integer.MAX_VALUE)) {
                if (FloatWordService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 保存配置文本
     */
    private void saveConfigText() {
        String newText = etConfigText.getText().toString().trim();
        currentConfigText = newText.isEmpty() ? "（空内容）" : newText;

        // 保存到 SharedPreferences
        saveData(KEY_TEXT_CONTENT, currentConfigText);
        
        // 如果服务正在运行，通知服务更新
        if (isServiceRunning()) {
            Intent intent = new Intent(this, FloatWordService.class);
            intent.putExtra("action", "update_text");
            intent.putExtra("text", currentConfigText);
            startService(intent);
        }
        
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Config saved: " + currentConfigText.substring(0, Math.min(20, currentConfigText.length())));
    }

    /**
     * 放大
     */
    private void zoomIn() {
        currentScale += ZOOM_STEP;
        applyScaleChange();
    }

    /**
     * 缩小
     */
    private void zoomOut() {
        currentScale = Math.max(0.5f, currentScale - ZOOM_STEP); // 限制最小缩放
        applyScaleChange();
    }

    /**
     * 应用缩放变化
     */
    private void applyScaleChange() {
        saveData(KEY_ZOOM_SCALE, currentScale);
        
        // 如果服务正在运行，通知服务更新
        if (isServiceRunning()) {
            Intent intent = new Intent(this, FloatWordService.class);
            intent.putExtra("action", "update_scale");
            intent.putExtra("scale", currentScale);
            startService(intent);
        }
        
        Toast.makeText(this, "已调整大小: " + String.format("%.1f", currentScale), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Scale changed to: " + currentScale);
    }

    /**
     * 通用保存方法
     */
    private void saveData(String key, Object value) {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        }
        editor.apply();
    }
    
    /**
     * 设置自动隐藏时长
     */
    private void setAutoHideDuration() {
        try {
            String durationStr = etAutoHideDuration.getText().toString().trim();
            if (durationStr.isEmpty()) {
                Toast.makeText(this, "请输入有效的时长", Toast.LENGTH_SHORT).show();
                return;
            }
            
            int duration = Integer.parseInt(durationStr);
            if (duration <= 0) {
                Toast.makeText(this, "时长必须大于0", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 保存配置
            autoHideDuration = duration;
            saveData(KEY_AUTO_HIDE_DURATION, duration);
            
            // 如果服务正在运行，通知服务更新
            if (isServiceRunning()) {
                Intent intent = new Intent(this, FloatWordService.class);
                intent.putExtra("action", "set_auto_hide_duration");
                intent.putExtra("duration", duration);
                startService(intent);
            }
            
            Toast.makeText(this, "已设置自动隐藏时长: " + duration + " 分钟", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Auto hide duration set to: " + duration + " minutes");
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid number format", e);
        }
    }
    
    /**
     * 切换悬浮窗显示/隐藏
     */
    private void toggleVisibility() {
        // 如果服务未运行，先启动服务
        if (!isServiceRunning()) {
            startFloatingService();
            Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 先检查并保存可能修改的时长
        checkAndSaveAutoHideDuration();
        
        Intent intent = new Intent(this, FloatWordService.class);
        intent.putExtra("action", "toggle_visibility");
        startService(intent);
        
        Log.d(TAG, "Toggle visibility requested");
    }
    
    /**
     * 启动悬浮窗服务
     */
    private void startFloatingService() {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                checkOverlayPermission();
                return;
            }
        }

        Intent serviceIntent = new Intent(this, FloatWordService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        Log.d(TAG, "Service started");
    }
    
    /**
     * 检查并保存自动隐藏时长(如果用户修改了但未点击设置)
     */
    private void checkAndSaveAutoHideDuration() {
        try {
            String durationStr = etAutoHideDuration.getText().toString().trim();
            if (durationStr.isEmpty()) {
                return;
            }
            
            int inputDuration = Integer.parseInt(durationStr);
            if (inputDuration <= 0) {
                return;
            }
            
            // 如果输入的时长与当前保存的不同,则自动保存
            if (inputDuration != autoHideDuration) {
                autoHideDuration = inputDuration;
                saveData(KEY_AUTO_HIDE_DURATION, inputDuration);
                
                // 同时通知服务更新
                Intent intent = new Intent(this, FloatWordService.class);
                intent.putExtra("action", "set_auto_hide_duration");
                intent.putExtra("duration", inputDuration);
                startService(intent);
                
                Log.d(TAG, "Auto-saved auto hide duration: " + inputDuration + " minutes");
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
            Log.w(TAG, "Invalid number format in auto-hide duration field");
        }
    }
    
    /**
     * 显示保活优化引导
     */
    private void showOptimizationGuide() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚡ 保活优化引导")
            .setMessage(
                "为确保悬浮窗不被系统杀死，请完成以下设置：\n\n" +
                "1️⃣ 允许自启动\n" +
                "   设置 → 应用 → 本应用 → 自启动 → 允许\n\n" +
                "2️⃣ 关闭电池优化\n" +
                "   设置 → 电池 → 电池优化 → 本应用 → 不优化\n\n" +
                "3️⃣ 允许后台活动\n" +
                "   设置 → 应用 → 本应用 → 电池 → 无限制\n\n" +
                "4️⃣ 锁定最近任务（可选）\n" +
                "   打开最近任务 → 找到本应用 → 点击锁定\n\n" +
                "是否立即前往电池优化设置？"
            )
            .setPositiveButton("前往设置", (dialog, which) -> {
                // 跳转到电池优化设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        // 如果失败，尝试打开应用详情
                        Intent appIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        appIntent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(appIntent);
                    }
                }
            })
            .setNegativeButton("稍后", null)
            .show();
    }
    //endregion

    @Override
    protected void onResume() {
        super.onResume();
        
        // 应用恢复时，如果不是首次启动且服务未运行，自动启动服务
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean isFirstLaunch = pref.getBoolean(KEY_IS_FIRST_LAUNCH, true);
        
        if (!isFirstLaunch && !isServiceRunning()) {
            Log.d(TAG, "Auto-starting service on resume (not first launch)");
            startFloatingService();
        } else if (isFirstLaunch) {
            Log.d(TAG, "First launch, waiting for user to configure content");
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // 应用进入后台时，自动保存可能修改的隐藏时长
        saveAutoHideDurationIfNeeded();
    }
    
    /**
     * 如果需要，自动保存隐藏时长
     */
    private void saveAutoHideDurationIfNeeded() {
        try {
            String durationStr = etAutoHideDuration.getText().toString().trim();
            if (durationStr.isEmpty()) {
                return;
            }
            
            int inputDuration = Integer.parseInt(durationStr);
            if (inputDuration <= 0) {
                return;
            }
            
            // 如果输入的时长与当前保存的不同,则自动保存
            if (inputDuration != autoHideDuration) {
                autoHideDuration = inputDuration;
                saveData(KEY_AUTO_HIDE_DURATION, inputDuration);
                
                // 同时通知服务更新
                if (isServiceRunning()) {
                    Intent intent = new Intent(this, FloatWordService.class);
                    intent.putExtra("action", "set_auto_hide_duration");
                    intent.putExtra("duration", inputDuration);
                    startService(intent);
                }
                
                Log.d(TAG, "Auto-saved auto hide duration on pause: " + inputDuration + " minutes");
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
            Log.w(TAG, "Invalid number format in auto-hide duration field");
        }
    }
}
