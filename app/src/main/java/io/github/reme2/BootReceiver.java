package io.github.reme2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机自启广播接收器
 * 职责：监听系统开机完成广播，自动启动悬浮窗服务
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "Received broadcast: " + action);
            
            // 处理开机完成、快速启动等事件
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
                
                Log.d(TAG, "System boot completed, starting FloatWordService");
                
                // 启动悬浮窗服务
                Intent serviceIntent = new Intent(context, FloatWordService.class);
                
                // Android 8.0+ 需要使用 startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                Log.d(TAG, "FloatWordService started successfully");
            }
        }
    }
}
