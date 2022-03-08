package server.command;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import utils.FTPLogger;

public class SessionThread extends Thread{

    //模式选择
    public enum PassivePort {
        PASV, PORT
    }

    public enum ASCIIBinary {
        ASCII, BINARY
    }

    public enum MODE{
        Stream, Block, Compress
    }

    private PassivePort passivePort;
    private ASCIIBinary asciiBinary = ASCIIBinary.BINARY;
    private MODE mode = MODE.Stream;

    private ServerSocket pasvServerSocket;
    private Socket cmdSocket;//控制连接
    private Socket dataSocket = null;

    private String clientIP = null;//数据连接的ip
    private int clientPort = -1;//数据连接的port

    private BufferedReader cmdReader = null;//在控制连接cmdSocket上读取的字符流
    private BufferedWriter cmdWriter = null;//在控制连接cmdSocket上写入的字符流

    private boolean isAnonymous = false;//是否匿名登录
    private String username = null;  //客户端发送的username
    private boolean isLogin = false;//用于标记用户是否已经登录

    private FTPLogger logger;//日志记录器

    public SessionThread(Socket cmdSocket) {
        this.cmdSocket = cmdSocket;
    }

    @Override
    public void run() {
       logger.info("Connection started");
       try{
           cmdReader = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
           cmdWriter = new BufferedWriter(new OutputStreamWriter(cmdSocket.getOutputStream()));

           while (true) {
               String cmdLine;
               cmdLine = cmdReader.readLine();
               System.out.println("cmd:"+cmdLine);
               if (cmdLine != null) {
                   logger.info(String.format("Received line from client %s: %s", cmdSocket.getRemoteSocketAddress().toString(), cmdLine));
                   CMD.parseCommand(this, cmdLine);
               } else {
                   logger.info("readLine gave null, quitting");
                   break;
               }
           }
       }catch (IOException e){
           logger.info("Connection was dropped");
       }
    }

    public void writeCmdResponse(String str){
        try {
            cmdWriter.write(str);
            cmdWriter.write("\r\n");
            cmdWriter.flush();
        }catch (IOException ignored){
        }
    }

    public void setPasvServerSocket(ServerSocket pasvServerSocket){
        this.pasvServerSocket = pasvServerSocket;
    }

    public boolean initDataSocket(){
        if (passivePort == PassivePort.PORT) {//主动模式
            try {
                System.out.println("clientIP:"+clientIP+" Port:"+clientPort);
                if(clientIP!=null&&clientPort!=-1) {
                    //与客户端建立一个数据连接
                    dataSocket = new Socket(clientIP, clientPort);
                    logger.info("clientIP:"+clientIP+" clientPort:"+clientPort);
                }else {
                    logger.info( "PORT mode but not initialized correctly");
                    writeCmdResponse("!!!!!!!!!!!!!!!!!!");//需要约定接口
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
            return true;

        } else if (passivePort == PassivePort.PASV) {//被动模式
            try {
                pasvServerSocket.setSoTimeout(10*1000);//设置阻塞的超时时间
                dataSocket = pasvServerSocket.accept();//accept客户端的socket连接请求
            } catch (IOException e) {
                //建立连接失败
                return false;
            }
            return true;
        }
        return false;
    }

    public Socket getDataSocket(){
        return dataSocket;
    }

    public void closeCmdSocket() {
        if (cmdSocket == null) {
            return;
        }
        try {
            cmdSocket.close();
        } catch (IOException ignore) {
        }
    }

    public void closeDataSocket(){
        if (dataSocket != null) {
            try {
                dataSocket.close();
            } catch (IOException ignore) {
            }
        }
        dataSocket = null;
    }

    public InetAddress getDataSocketPasvIP(){
        return cmdSocket.getLocalAddress();
    }

    public PassivePort getPassivePort(){
        return passivePort;
    }

    public void setPassivePort(PassivePort pp){
        this.passivePort = pp;
    }

    public ASCIIBinary getAsciiBinary(){
        return asciiBinary;
    }

    public void setACSIIBinary(ASCIIBinary ab){
        this.asciiBinary = ab;
    }

    public MODE getMode(){
        return mode;
    }

    public void setMode(MODE mode){
        this.mode = mode;
    }

    public String getClientIP(){
        return clientIP;
    }

    public void setClientIP(String IP){
        clientIP = IP;
    }

    public int getClientPort(){
        return clientPort;
    }

    public void setClientPort(int port){
        clientPort = port;
    }

    public boolean isAnonymous(){
        return isAnonymous;
    }

    public void setAnonymous(boolean isAnonymous){
        this.isAnonymous = isAnonymous;
    }

    public String getUsername(){
        return username;
    }

    public void setUsername(String username){
        this.username = username;
    }

    public boolean isLogin(){
        return isLogin;
    }

    public void setLogin(boolean isLogin){
        this.isLogin = isLogin;
    }

    public FTPLogger getLogger(){
        return logger;
    }

    public void setLogger(FTPLogger logger){
        this.logger = logger;
    }

}
