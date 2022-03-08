package server.command;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;

//被动模式 服务器打开端口
public class PASV extends CMD{

    SecureRandom random = new SecureRandom();

    public PASV(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {

        int tempPort = getRandomPort();

        if(tempPort <= 1024){
            sessionThread.writeCmdResponse("502 PASV port number invalid(port<1024)");
        }
        //设置ServerSocket监听用户的连接
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(tempPort);
        } catch (IOException e) {
            sessionThread.writeCmdResponse("502 Data socket creation error");//需要约定接口
            return;
        }

        InetAddress address = sessionThread.getDataSocketPasvIP();
        if(address == null){
            sessionThread.writeCmdResponse("502 PASV IP string invalid");//需要约定接口
            return;
        }

        int a = tempPort / 256;
        int b = tempPort % 256;

        String response = String.format("227 Entering Passive Mode (%s,%d,%d)",address.getHostAddress().replace('.', ','), a, b);//需要约定接口
        sessionThread.writeCmdResponse(response);
        //设置为被动模式
        sessionThread.setPasvServerSocket(serverSocket);
        sessionThread.setPassivePort(SessionThread.PassivePort.PASV);
    }

    //生成一个大于1024的随机端口
    private int getRandomPort() {
        int port = 1024 + random.nextInt(65535-1024);
        return port;
    }
}
