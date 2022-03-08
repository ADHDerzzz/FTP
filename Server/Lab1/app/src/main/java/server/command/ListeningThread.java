package server.command;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import utils.FTPLogger;

public class ListeningThread extends Thread {

    private static final String TAG = ListeningThread.class.getSimpleName();

    private final ServerSocket listenSocket;//用来监听的ServerSocket
    private final FTPLogger logger;

    public ListeningThread(ServerSocket listenSocket, FTPLogger logger) {
        this.listenSocket = listenSocket;
        this.logger = logger;
    }

    public void quit() {
        try {
            listenSocket.close();
        } catch (Exception e) {
            Log.d(TAG, "Exception closing listenSocket");
        }
    }

    @Override
    public void run() {
        while (true) {//无线循环，监听用户的连接
            Socket clientCmdSocket;
            try {
                clientCmdSocket = listenSocket.accept();//accept用户的连接请求，并将得到的socket作为这个用户的控制连接
                SessionThread sessionThread = new SessionThread(clientCmdSocket);//创建处理用户请求的线程
                if (logger != null) {
                    sessionThread.setLogger(logger);
                }
                //线程开始运行
                sessionThread.start();
            } catch (IOException ignored) {
            }

        }
    }
}

