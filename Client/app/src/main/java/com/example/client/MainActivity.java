package com.example.client;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.leon.lfilepickerlibrary.LFilePicker;


import java.util.ArrayList;
import java.util.regex.Pattern;

import clientCore.DownloadUploadTask;
import clientCore.MyClient;
import clientCore.exceptions.ClientException;
import clientCore.exceptions.NoServerFoundException;

public class MainActivity extends AppCompatActivity {

    TextInputEditText host;
    TextInputEditText port;
    TextInputEditText username;
    EditText password;

    Button connect;
    Button login;
    Button upload;
    Button download;
    Button quit;

    TextView connectStatus;
    TextView logInStatus;

    RadioGroup mode;
    RadioGroup structure;
    RadioButton stream;
    RadioButton file;

    Switch passiveActive;
    Switch binaryAscii;
    Switch fileFolder;
    Switch singleMultiple;

    MyClient client;

    volatile boolean connected = false;
    volatile boolean loggedIn = false;

    String downloadDirectory;

    int UPLOAD_FILE_REQUEST_CODE = 1000;
    int UPLOAD_FOLDER_REQUEST_CODE = 1001;
    int UPLOAD_FOLDER_CONCURRENTLY_REQUEST_CODE = 1002;

    volatile DownloadUploadTask.OperationType operationType = DownloadUploadTask.OperationType.DOWNLOAD_FILE;

    class DownloadClickHandler implements View.OnClickListener {

        private DownloadUploadTask.OperationType type;

        public DownloadClickHandler(DownloadUploadTask.OperationType type) {
            this.type = type;
            System.err.println(type.toString());
        }

        @Override
        public void onClick(View v) {
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                return;
            }
            type = operationType;

            final EditText inputServer = new EditText(MainActivity.this);
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("请输入文件所在位置").setIcon(android.R.drawable.ic_dialog_info).setView(inputServer)
                    .setNegativeButton("取消", null);

            builder.setPositiveButton("确定", (dialog, which) -> {
                String args = inputServer.getText().toString();

                ProgressDialog downloadProgressDialog = new ProgressDialog(MainActivity.this);
                downloadProgressDialog.setOwnerActivity(MainActivity.this);

                //新开一个线程来下载
                Thread thread = new Thread(new DownloadUploadTask(
                        client,
                        type,
                        args,
                        downloadProgressDialog));

                thread.start();
            });
            builder.show();
        }
    }

    class UploadHandlerAfterChoosing {

        private final DownloadUploadTask.OperationType type;
        private final String chooseResult;

        public UploadHandlerAfterChoosing(DownloadUploadTask.OperationType type, String chooseResult) {
            this.type = type;
            this.chooseResult = chooseResult;
        }

        public void run() {
            ProgressDialog downloadProgressDialog = new ProgressDialog(MainActivity.this);
            downloadProgressDialog.setOwnerActivity(MainActivity.this);

            //新开一个线程来下载
            Thread thread = new Thread(new DownloadUploadTask(
                    client,
                    type,
                    chooseResult,
                    downloadProgressDialog));

            thread.start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //为了开发方便，禁用掉全部的严格模式限制
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().build());

        bindAllView();

        stream.setChecked(true);

        file.setChecked(true);

        downloadDirectory = getApplicationContext().getExternalFilesDir("ftp_download").getAbsolutePath();

        connect.setOnClickListener(v -> {
            Thread t = new Thread(() -> {
                if (connected) {
                    ToastUtil.showToast(MainActivity.this, R.string.hasConnected, Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    String hostContent = host.getText().toString();
                    String portContent = port.getText().toString();
                    if (!Pattern.matches("^([0-9]+\\.){3}[0-9]+$", hostContent)) {
                        ToastUtil.showToast(MainActivity.this, "host格式不对！", Toast.LENGTH_SHORT);
                        return;
                    }
                    if (!Pattern.matches("^[0-9]+$", portContent)) {
                        ToastUtil.showToast(MainActivity.this, "port应该是一个数字", Toast.LENGTH_SHORT);
                        return;
                    }
                    int portNum = Integer.parseInt(portContent);
                    if (portNum < 2048 || portNum > 65536) {
                        ToastUtil.showToast(MainActivity.this, "port应该在2048与65536之间", Toast.LENGTH_SHORT);
                        return;
                    }
                    client = new MyClient(hostContent, Integer.parseInt(portContent));
                    client.setDownloadDirectory(downloadDirectory);
                    ToastUtil.showToast(MainActivity.this, "您已连接服务器", Toast.LENGTH_SHORT);
                    connected = true;
                } catch (NoServerFoundException | ClientException e) {
                    ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                    e.printStackTrace();
                }
            });

            t.start();

            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (connected) {
                connectStatus.setText(getString(R.string.connected));
            }
        });

        login.setOnClickListener(v -> {
            Thread t = new Thread(() -> {
                if (loggedIn) {
                    ToastUtil.showToast(MainActivity.this, "您已登陆过了", Toast.LENGTH_SHORT);
                    return;
                }
                if (!connected) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                    return;
                }
                String usernameContent = username.getText().toString();
                String passwordContent = password.getText().toString();
                if (usernameContent.length() == 0) {
                    ToastUtil.showToast(MainActivity.this, "请填写用户名！", Toast.LENGTH_SHORT);
                }
                try {
                    client.logIn(usernameContent, passwordContent);
                    client.pasv();
                    client.type(MyClient.TypeOfCode.BINARY);
                    client.mode(MyClient.Mode.STREAM);
                    loggedIn = true;
                    ToastUtil.showToast(MainActivity.this, "已登录", Toast.LENGTH_SHORT);
                } catch (ClientException e) {
                    ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                    e.printStackTrace();
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (loggedIn) {
                logInStatus.setText(getString(R.string.hasLoggedIn));
            }
        });

        upload.setOnClickListener(v -> {
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                return;
            }

            if (!fileFolder.isChecked()) {
                new LFilePicker()
                        .withActivity(MainActivity.this)
                        .withRequestCode(UPLOAD_FILE_REQUEST_CODE)
                        .withStartPath(downloadDirectory)//指定初始显示路径
                        .withMaxNum(1)
                        .start();
            } else {
                if (!singleMultiple.isChecked()) {
                    new LFilePicker()
                            .withActivity(MainActivity.this)
                            .withRequestCode(UPLOAD_FOLDER_REQUEST_CODE)
                            .withStartPath(downloadDirectory)//指定初始显示路径
                            .withChooseMode(false)//这里应该选择目录
                            .start();
                } else {
                    new LFilePicker()
                            .withActivity(MainActivity.this)
                            .withRequestCode(UPLOAD_FOLDER_CONCURRENTLY_REQUEST_CODE)
                            .withStartPath(downloadDirectory)//指定初始显示路径
                            .withChooseMode(false)//这里应该选择目录
                            .start();
                }
            }

        });

        passiveActive.setOnCheckedChangeListener((v, checked) -> {
            ArrayList<Exception> exceptions = new ArrayList<>();
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (checked) {
                v.setText(R.string.active);
                Thread t1 = new Thread(() -> {
                    try {
                        client.port();
                        ToastUtil.showToast(MainActivity.this, "开启主动模式", Toast.LENGTH_SHORT);
                    } catch (ClientException e) {
                        exceptions.add(e);
                        ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                });
                t1.start();
                try {
                    t1.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exceptions.size() != 0) {
                    v.setChecked(false);
                    v.setText(R.string.passive);
                }
            } else {
                v.setText(R.string.passive);
                Thread t2 = new Thread(() -> {
                    try {
                        client.pasv();
                        ToastUtil.showToast(MainActivity.this, "开启被动模式", Toast.LENGTH_SHORT);
                    } catch (ClientException e) {
                        exceptions.add(e);
                        ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                });
                t2.start();
                try {
                    t2.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exceptions.size() != 0) {
                    v.setChecked(true);
                    v.setText(R.string.active);
                }
            }
        });

        binaryAscii.setOnCheckedChangeListener((v, checked) -> {
            ArrayList<Exception> exceptions = new ArrayList<>();
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (checked) {
                v.setText(getString(R.string.ascii));
                Thread t1 = new Thread(() -> {
                    try {
                        client.type(MyClient.TypeOfCode.ASCII);
                        ToastUtil.showToast(MainActivity.this, "转为ASCII编码！", Toast.LENGTH_SHORT);
                    } catch (ClientException e) {
                        exceptions.add(e);
                        ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                });
                t1.start();
                try {
                    t1.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exceptions.size() != 0) {
                    v.setChecked(false);
                    v.setText(getString(R.string.binary));
                }
            } else {
                v.setText(getString(R.string.binary));
                Thread t2 = new Thread(() -> {
                    try {
                        client.type(MyClient.TypeOfCode.BINARY);
                        ToastUtil.showToast(MainActivity.this, "转为binary编码！", Toast.LENGTH_SHORT);
                    } catch (ClientException e) {
                        exceptions.add(e);
                        ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                        e.printStackTrace();
                    }
                });
                t2.start();
                try {
                    t2.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (exceptions.size() != 0) {
                    v.setChecked(false);
                    v.setText(getString(R.string.ascii));
                }
            }
        });

        fileFolder.setOnCheckedChangeListener((v, checked) -> {
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (checked) {
                fileFolder.setText("Folder");
                if (singleMultiple.isChecked()) {
                    operationType = DownloadUploadTask.OperationType.DOWNLOAD_FOLDER_CONCURRENTLY;
                } else {
                    operationType = DownloadUploadTask.OperationType.DOWNLOAD_FOLDER;
                }
            } else {
                fileFolder.setText("File");
                operationType = DownloadUploadTask.OperationType.DOWNLOAD_FILE;
            }
            Log.e("operationType", operationType.toString());
        });

        download.setOnClickListener(new DownloadClickHandler(operationType));

        singleMultiple.setOnCheckedChangeListener((v, checked) -> {
            if (!connected) {
                ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (!loggedIn) {
                ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                v.setChecked(!checked);
                return;
            }
            if (checked) {
                if (!fileFolder.isChecked()) {
                    ToastUtil.showToast(MainActivity.this, "传输文件没有多线程功能！", Toast.LENGTH_SHORT);
                    v.setChecked(false);
                    return;
                } else {
                    operationType = DownloadUploadTask.OperationType.DOWNLOAD_FOLDER_CONCURRENTLY;
                }
                singleMultiple.setText(getString(R.string.multipleThread));
            } else {
                singleMultiple.setText(getString(R.string.singleThread));
                operationType = DownloadUploadTask.OperationType.DOWNLOAD_FOLDER;
            }
        });

        mode.setOnCheckedChangeListener((v, i) -> {
            new Thread(() -> {
                if (!connected) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                    return;
                }
                if (!loggedIn) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                    return;
                }
                RadioButton radioButton = findViewById(i);
                try {
                    switch (radioButton.getText().toString()) {
                        case "块模式":
                            break;
                        case "压缩模式":
                            client.mode(MyClient.Mode.COMPRESSED);
                            break;
                        case "流模式":
                        default:
                            client.mode(MyClient.Mode.STREAM);
                    }
                    ToastUtil.showToast(MainActivity.this, "切换到了" + radioButton.getText().toString(), Toast.LENGTH_SHORT);
                } catch (ClientException e) {
                    ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                    e.printStackTrace();
                }
            }).start();
        });

        structure.setOnCheckedChangeListener((v, i) -> {
            new Thread(() -> {
                if (!connected) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                    return;
                }
                if (!loggedIn) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                    return;
                }
                RadioButton radioButton = findViewById(i);
                ToastUtil.showToast(MainActivity.this, "当前structure是：" + radioButton.getText().toString(), Toast.LENGTH_SHORT);
            }).start();
        });

        quit.setOnClickListener(v -> {
            passiveActive.setChecked(false);
            passiveActive.setText(getString(R.string.passive));
            binaryAscii.setChecked(false);
            binaryAscii.setText(getString(R.string.binary));
            fileFolder.setChecked(false);
            fileFolder.setText(getString(R.string.file));
            singleMultiple.setChecked(false);
            singleMultiple.setText(getString(R.string.singleThread));
            Thread t = new Thread(() -> {
                if (!connected) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorUnconnected, Toast.LENGTH_SHORT);
                    return;
                }
                if (!loggedIn) {
                    ToastUtil.showToast(MainActivity.this, R.string.errorLogIn, Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    client.quit();
                    loggedIn = false;
                    connected = false;
                    ToastUtil.showToast(MainActivity.this, "退出成功", Toast.LENGTH_SHORT);
                } catch (ClientException e) {
                    ToastUtil.showToast(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
                    e.printStackTrace();
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!loggedIn && !connected) {
                connectStatus.setText(getString(R.string.unconnected));
                logInStatus.setText(getString(R.string.notLogIn));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == UPLOAD_FILE_REQUEST_CODE) {//上传单个文件
                ArrayList<String> list = data.getStringArrayListExtra("paths");
                String filename = list.get(0);
                new UploadHandlerAfterChoosing(DownloadUploadTask.OperationType.UPLOAD_FILE, filename).run();
            } else if (requestCode == UPLOAD_FOLDER_REQUEST_CODE) {//上传目录
                String path = data.getStringExtra("path");
                new UploadHandlerAfterChoosing(DownloadUploadTask.OperationType.UPLOAD_FOLDER, path).run();
            } else if (requestCode == UPLOAD_FOLDER_CONCURRENTLY_REQUEST_CODE) {//并发上传目录
                String path = data.getStringExtra("path");
                new UploadHandlerAfterChoosing(DownloadUploadTask.OperationType.UPLOAD_FOLDER_CONCURRENTLY, path).run();

            }
        }
    }

    void bindAllView() {
        // editable text
        host = findViewById(R.id.host);
        port = findViewById(R.id.port);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        // button
        connect = findViewById(R.id.connect);
        login = findViewById(R.id.login);
        quit = findViewById(R.id.quit);
        upload = findViewById(R.id.upload);
        download = findViewById(R.id.download);
        // text view
        connectStatus = findViewById(R.id.connectStatus);
        logInStatus = findViewById(R.id.logInStatus);
        // switch
        passiveActive = findViewById(R.id.passiveActive);
        binaryAscii = findViewById(R.id.binaryAscii);
        fileFolder = findViewById(R.id.fileFolder);
        singleMultiple = findViewById(R.id.singleMultiple);
        // radio group
        mode = findViewById(R.id.mode);
        structure = findViewById(R.id.structure);
        stream = findViewById(R.id.stream);
        file = findViewById(R.id.file);
    }
}