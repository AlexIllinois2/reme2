package io.github.reme2;

//import android.app.Activity;
//import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

// todo 在idea plus开发后运行时报错找不到MainActivity时，需要把构建分支在debug和release之间切换一下

/**
 * 主界面 Activity
 * 职责：管理页面生命周期、初始化UI组件、协调配置存储与悬浮窗逻辑
 */
public class MainActivity extends AppCompatActivity {

    //region 1. 常量定义
    // Preference 相关
    private static final String PREF_NAME = "floating_window_prefs";
    private static final String KEY_TEXT_CONTENT = "text_content";
    private static final String KEY_POS_X = "pos_x";
    private static final String KEY_POS_Y = "pos_y";
    private static final String KEY_ZOOM_SCALE = "zoom_scale";

    // 默认值
    private static final String DEFAULT_TEXT = "delve\n翻找；\n探索，\n探究";
    private static final float DEFAULT_SCALE = 1.0f;
    private static final float ZOOM_STEP = 0.1f;
    private static final int DEFAULT_Y_POS = 100;
    //endregion

    //region 2. 成员变量
    // UI Components
    private EditText etConfigText;
    private Button btnSaveConfig, btnToggleFloating, btnZoomIn, btnZoomOut;

    // 悬浮窗相关
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvFloatingText; // 缓存引用，避免频繁 findViewById

    // 状态数据
    private String currentConfigText = DEFAULT_TEXT;
    private float currentScale = DEFAULT_SCALE;
    private int savedX, savedY;
    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // 初始化流程
        initPreferences(); // 1. 先读取数据
        initViews();       // 2. 再初始化视图（依赖数据）
        setupEventListeners(); // 3. 绑定事件
        checkOverlayPermission(); // 4. 检查权限并创建悬浮窗
    }

    //region 3. 初始化模块
    /**
     * 初始化 SharedPreferences 数据
     * 将数据读取集中在此处，避免 onCreate 过于臃肿
     */
    private void initPreferences() {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        currentConfigText = pref.getString(KEY_TEXT_CONTENT, DEFAULT_TEXT);
        savedX = pref.getInt(KEY_POS_X, 0);
        savedY = pref.getInt(KEY_POS_Y, DEFAULT_Y_POS);
        currentScale = pref.getFloat(KEY_ZOOM_SCALE, DEFAULT_SCALE);
    }

    /**
     * 初始化视图组件
     * 将 findViewById 集中管理
     */
    private void initViews() {
        etConfigText = findViewById(R.id.et_config_text);
        btnSaveConfig = findViewById(R.id.btn_save_config);
        btnToggleFloating = findViewById(R.id.btn_toggle_floating);
        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);

        // 设置初始文本
        etConfigText.setText(currentConfigText);
    }

    /**
     * 绑定所有按钮点击事件
     */
    private void setupEventListeners() {
        btnToggleFloating.setOnClickListener(v -> toggleFloatingWindow());
        btnSaveConfig.setOnClickListener(v -> saveConfigText());
        btnZoomIn.setOnClickListener(v -> zoomIn());
        btnZoomOut.setOnClickListener(v -> zoomOut());
    }
    //endregion

    //region 4. 悬浮窗权限与生命周期
    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1001);
            } else {
                createFloatingWindow();
            }
        } else {
            createFloatingWindow();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    createFloatingWindow();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        // 注意：此处销毁悬浮窗是为了配合 Activity 生命周期
        // 如果希望 App 退到后台悬浮窗依然存在，建议将悬浮窗逻辑移至 Service
        destroyFloatingWindow();
        super.onDestroy();
    }
    //endregion

    //region 5. 业务逻辑方法
    private void toggleFloatingWindow() {
        if (floatingView == null) {
            createFloatingWindow();
            btnToggleFloating.setText("隐藏");
            Toast.makeText(this, "悬浮窗已显示", Toast.LENGTH_SHORT).show();
        } else {
            destroyFloatingWindow();
            btnToggleFloating.setText("显示");
            Toast.makeText(this, "悬浮窗已隐藏", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveConfigText() {
        String newText = etConfigText.getText().toString().trim();
        currentConfigText = newText.isEmpty() ? "（空内容）" : newText;

        // 保存并更新
        saveData(KEY_TEXT_CONTENT, currentConfigText);
        updateFloatingWindowText();
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
    }

    private void zoomIn() {
        currentScale += ZOOM_STEP;
        applyScaleChange();
    }

    private void zoomOut() {
        currentScale = Math.max(0.5f, currentScale - ZOOM_STEP); // 限制最小缩放
        applyScaleChange();
    }

    private void applyScaleChange() {
        saveData(KEY_ZOOM_SCALE, currentScale);
        updateFloatingWindowScale();
        Toast.makeText(this, "已调整大小", Toast.LENGTH_SHORT).show();
    }
    //endregion

    //region 6. 悬浮窗核心逻辑 (重构重点)
    /**
     * 创建悬浮窗
     * 逻辑：加载布局 -> 配置参数 -> 绑定触摸事件 -> 添加到 Window
     */
    private void createFloatingWindow() {
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
            Log.d("FloatingWindow", "AddView Success");
        } catch (Exception e) {
            Log.e("FloatingWindow", "Error creating overlay", e);
        }
    }

    /**
     * 构建悬浮窗的 LayoutParams
     */
    private WindowManager.LayoutParams buildLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
			? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
			: WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
			WindowManager.LayoutParams.WRAP_CONTENT,
			WindowManager.LayoutParams.WRAP_CONTENT,
			type,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
            saveData(KEY_POS_X, p.x);
            saveData(KEY_POS_Y, p.y);

            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                floatingView = null;
                tvFloatingText = null; // 清理引用
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
							savedX = params.x;
							savedY = params.y;
							saveData(KEY_POS_X, params.x);
							saveData(KEY_POS_Y, params.y);
							return true;
					}
					return false;
				}
			});
    }
    //endregion

    //region 7. 数据存储工具类 (消除重复代码)
    /**
     * 通用保存方法，减少重复代码
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
        // 可以根据需要扩展 Boolean, Long 等
        editor.apply();
    }
    //endregion

    //region 8. 更新悬浮窗内部状态
    @Override
    protected void onResume() {
        super.onResume();
        // 如果悬浮窗已存在，同步最新的缩放比例（例如从其他页面回来时）
        if (floatingView != null) {
            updateFloatingWindowScale();
        }
    }

    /**
     * 仅更新悬浮窗内的文字内容
     */
    private void updateFloatingWindowText() {
        if (tvFloatingText != null) {
            tvFloatingText.setText(currentConfigText);
        }
        // 如果悬浮窗未创建，会在 createFloatingWindow 中自动应用 currentConfigText
    }

    /**
     * 仅更新悬浮窗内的文字大小
     */
    private void updateFloatingWindowScale() {
        if (tvFloatingText != null) {
            updateTextSize(tvFloatingText);
        }
    }

    /**
     * 实际计算并设置 TextView 大小
     */
    private void updateTextSize(TextView tv) {
        float baseTextSize = 14; // 基准字号
        float newTextSize = baseTextSize * currentScale;
        tv.setTextSize(Math.max(8, newTextSize)); // 限制最小字号
    }
    //endregion
}

