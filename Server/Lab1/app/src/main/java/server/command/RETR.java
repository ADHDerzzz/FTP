package server.command;

import android.util.Log;

import com.alibaba.fastjson.JSON;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import utils.FTPInfos;
import utils.FileHeader;

//服务器发送文件给客户端
public class RETR extends CMD{

    private static final String TAG = RETR.class.getSimpleName();

    public RETR(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {

        //需要指定PASV或PORT
        if(sessionThread.getPassivePort()==null){
            sessionThread.writeCmdResponse("503 require mode(PASV/PORT)");
            return;
        }

        String filePath = FTPInfos.rootPath + File.separator + cmdArg;
        System.out.println(filePath);
        File file = new File(filePath);

        mainBlock:{
            if(!file.exists()){
                sessionThread.writeCmdResponse("501 file path not exist");
                break mainBlock;
            } else if(file.isDirectory()){
                sessionThread.writeCmdResponse("501 can't RETR a directory");
                break mainBlock;
            } else if(!file.canRead()){//需要约定一下接口！！！！！！！！！！！
                sessionThread.writeCmdResponse("550 No read permissions");
                break mainBlock;
            }

            sessionThread.writeCmdResponse("200 create folder succeed");

            if (sessionThread.initDataSocket()) {
                sessionThread.writeCmdResponse("125 RETR opened data socket succeed");
            } else {
                sessionThread.writeCmdResponse("425 RETR opened data socket failed");
                break mainBlock;
            }

            byte[] buf = new byte[1024 * 1024];//每次只从带缓冲的字节流中读取1MB文件
            int bytesRead = 0;
            //Binary模式
            if(sessionThread.getAsciiBinary() == SessionThread.ASCIIBinary.BINARY){
                Log.d(TAG,"Transferring in Binary mode");
                BufferedInputStream in = null;
                OutputStream out = null;
                try{
                    in = new BufferedInputStream(new FileInputStream(filePath));
                    out = sessionThread.getDataSocket().getOutputStream();

                    FileHeader fileHeader = new FileHeader(file.length(), cmdArg);
                    out.write((JSON.toJSONString(fileHeader) + "\r\n").getBytes(StandardCharsets.UTF_8));

                    while ((bytesRead=in.read(buf)) != -1) {
                        out.write(buf, 0, bytesRead);
                        out.flush();
                    }

                    sessionThread.writeCmdResponse("226 file transfer succeed(BINARY)");
                } catch (Exception e) {
                    sessionThread.writeCmdResponse("426 file transfer failed(BINARY)");
                } finally {
                    try {
                        if(in != null){
                            in.close();
                        }
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
                //ASCII模式
            } else if (sessionThread.getAsciiBinary() == SessionThread.ASCIIBinary.ASCII){
                FileInputStream in = null;
                OutputStream out = null;
                try{
                    in = new FileInputStream(file);
                    out = sessionThread.getDataSocket().getOutputStream();

                    byte[] crnBuf = {'\r', '\n'};
                    boolean lastBufEndedWithCR = false;
                    while (bytesRead!=-1) {//逐行输出
                        int startPos = 0, endPos = 0;
                        bytesRead = in.read(buf);
                        for (endPos = 0; endPos < bytesRead; endPos++) {
                            if (buf[endPos] == '\n') {
                                out.write(buf, startPos, endPos - startPos);
                                if (endPos == 0) {
                                    if (!lastBufEndedWithCR) {
                                        out.write(crnBuf, 0, 1);
                                    }
                                } else if (buf[endPos - 1] != '\r') {
                                    out.write(crnBuf, 0, 1);
                                }
                                startPos = endPos;
                            }
                        }
                    }
                    //写传输成功
                    sessionThread.writeCmdResponse("226 file transfer succeed(ASCII)");
                } catch (Exception e) {
                    sessionThread.writeCmdResponse("426 file transfer failed(ASCII)");
                } finally {
                    try {
                        if(in != null){
                            in.close();
                        }
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        sessionThread.closeDataSocket();
    }
}
