package com.example.client;

import android.app.Activity;
import android.widget.Toast;

/**
 * 简单封装一下toast，让这一次toast的时候马上显示
 */
public class ToastUtil {

    private static Toast lastToast;

    public static void showToast(Activity activity, String text, int duration) {
        activity.runOnUiThread(() -> {
            synchronized (ToastUtil.class) {
                if (lastToast != null) {
                    lastToast.cancel();
                }
                Toast thisToast = Toast.makeText(activity, text, duration);
                lastToast = thisToast;
                thisToast.show();
            }
        });
    }

    public static void showToast(Activity activity, int text, int duration) {
        activity.runOnUiThread(() -> {
            synchronized (ToastUtil.class) {
                if (lastToast != null) {
                    lastToast.cancel();
                }
                Toast thisToast = Toast.makeText(activity, text, duration);
                lastToast = thisToast;
                thisToast.show();
            }
        });
    }

}
