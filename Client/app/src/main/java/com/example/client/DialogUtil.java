package com.example.client;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

public class DialogUtil {

    public static void simpleAlert(Context context, String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title).setMessage(message).create().show();
    }
}
