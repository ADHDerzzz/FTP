package com.example.lab1;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import utils.FTPInfos;
import utils.FTPServer;
import utils.FTPLogger;

public class MainActivity extends AppCompatActivity {

    private LinearLayout logLinearLayout;
    private ScrollView logScrollView;
    private EditText portText;//输入端口的编辑区
    private Button startButton;//启动ftp服务器的按钮
    private TextView statusText;
    private TextView rootPathText;
    private TextView serverIPText;

    boolean serverStarted = false;//服务器是否已经启动

    private volatile FTPServer ftpServer;

    private String rootPath;
    private String logPath;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logLinearLayout = findViewById(R.id.log_linear_layout);
        logScrollView = findViewById(R.id.log);
        portText = findViewById(R.id.port_edittext);
        startButton = findViewById(R.id.start);
        statusText = findViewById(R.id.status);
        rootPathText = findViewById(R.id.rootpath);
        serverIPText = findViewById(R.id.serverIP);

        //申请文件访问权限
        requestPermissions(new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        logPath = getApplicationContext().getExternalFilesDir(null) + File.separator + "log.txt";

        setTitle("FTP SERVER");
        //设置ftp服务器的根目录
        rootPath = getApplicationContext().getExternalFilesDir("ftp_server").getAbsolutePath();
        rootPathText.setText(rootPath);
        serverIPText.setText("IP"+getLocalHostLANAddress());
        //设置按下按钮后启动服务器！
        startButton.setOnClickListener((view) -> {
            List<Exception> exceptions = new Vector<>();
            if (!serverStarted) {
                //端口号
                String port = portText.getText().toString();
                //检查端口号是否符合规格
                if (port.length() == 0 || !(2048 <= Integer.parseInt(port) && Integer.parseInt(port) <= 65535)) {
                    Toast.makeText(getApplicationContext(), "Port should be in (2048,65536)", Toast.LENGTH_LONG).show();
                    return;
                }

                Thread mainThread = new Thread(() -> {
                    try {
                        ftpServer = new FTPServer(Integer.parseInt(port), new FTPLogger(logPath,logScrollView, logLinearLayout, this));
                        FTPInfos.setRootPath(rootPath);
                        ftpServer.start();
                    } catch (Exception e) {
                        exceptions.add(e);
                    }
                });

                try {
                    mainThread.start();
                    mainThread.join();//主线程等待子线程的终止
                } catch (InterruptedException ignored) {
                }

                if (exceptions.size() == 0) {
                    serverStarted = true;
                    Toast.makeText(getApplicationContext(), "Succeed running", Toast.LENGTH_LONG).show();
                    statusText.setText("Running!");
                } else {
                    Toast.makeText(getApplicationContext(), "Failed running", Toast.LENGTH_LONG).show();
                }

            } else {
                Toast toast = Toast.makeText(getApplicationContext(), "Already Running", Toast.LENGTH_LONG);
                toast.show();
            }

        });
    }

    /**
     * 由于不能在主线程请求网络操作因此需要调用方法
     * 获取复杂网络环境下的Ip地址
     * 参考https://www.cnblogs.com/xiaoBlog2016/p/7076230.html
     */
    public InetAddress getLocalHostLANAddress(){
        try {
            InetAddress candidateAddress = null;
            // 遍历所有的网络接口
            for (Enumeration ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements(); ) {
                NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = (InetAddress) inetAddrs.nextElement();
                    if (!inetAddr.isLoopbackAddress()) {// 排除loopback类型地址
                        if (inetAddr.isSiteLocalAddress()) {
                            // site-local地址
                            return inetAddr;
                        } else if (candidateAddress == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress;
            }
            // 如果没有发现 non-loopback地址.只能用最次选的方案
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            return jdkSuppliedAddress;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}