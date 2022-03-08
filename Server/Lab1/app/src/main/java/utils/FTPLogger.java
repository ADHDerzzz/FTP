package utils;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileWriter;
import java.io.IOException;

public class FTPLogger{
    private final Activity activity;
    private final ScrollView logScrollView;
    private final LinearLayout logLinearLayout;
    private FileWriter logWriter;

    public FTPLogger(String logPath, ScrollView logScrollView, LinearLayout logLinearLayout, Activity activity) {
        try {
            this.logWriter = new FileWriter(logPath, true);
        } catch (IOException ignored) {
        }
        this.logLinearLayout = logLinearLayout;
        this.logScrollView = logScrollView;
        this.activity = activity;
    }

    public void info(String logStr) {
        activity.runOnUiThread(() -> {
            TextView textView = new TextView(activity.getApplicationContext());
            textView.setText(logStr);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setPadding(0,10,0,10);
            logLinearLayout.addView(textView);
            logScrollView.fullScroll(View.FOCUS_DOWN);
        });

        if (logWriter != null) {
            try {
                logWriter.write(logStr);
            } catch (IOException ignored) {
            }
        }
    }
}
