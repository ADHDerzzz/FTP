package utils;

import java.io.IOException;
import java.net.ServerSocket;

import server.command.ListeningThread;

public class FTPServer {

    private final ServerSocket serverSocket; //用来监听的ServerSocket
    private final Thread listeningThread;

    public FTPServer(int listenPort, FTPLogger logger) throws IOException {
        FTPInfos.init();//初始化FTP用户和根目录信息
        serverSocket = new ServerSocket(listenPort);
        listeningThread = new ListeningThread(serverSocket,logger);
    }

    public void start() {
        listeningThread.start();
    }
}
