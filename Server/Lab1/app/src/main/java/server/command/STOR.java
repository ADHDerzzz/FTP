package server.command;

import com.alibaba.fastjson.JSON;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import utils.FTPInfos;
import utils.FileHeader;

public class STOR extends CMD {

    public STOR(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run(){

        //需要指定PASV或PORT
        if(sessionThread.getPassivePort()==null){
            sessionThread.writeCmdResponse("503 require mode(PASV/PORT)");
            return;
        }

        String filePath = FTPInfos.rootPath + File.separator + cmdArg;
        System.out.println(filePath);

        //创建存储这个文件所需要的路径
        File file = new File(filePath);
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }

        sessionThread.writeCmdResponse("200 create folder succeed");
        //建立数据连接
        boolean success = sessionThread.initDataSocket();
        if (success) {
            sessionThread.writeCmdResponse("125 get data socket succeed");
        } else {
            sessionThread.writeCmdResponse("221 get data socket failed");
            return;
        }

        byte[] buf = new byte[1024 * 1024];
        //ASCII模式
        if (sessionThread.getAsciiBinary() == SessionThread.ASCIIBinary.ASCII) {
            int numRead = 0;
            InputStream in = null;
            FileOutputStream out = null;
            try {
                in = sessionThread.getDataSocket().getInputStream();
                out = new FileOutputStream(file);
                while (numRead != -1) {
                    numRead = in.read(buf,0, buf.length);
                    int startPos = 0, endPos;
                    for (endPos = 0; endPos < numRead; endPos++) {
                        if (buf[endPos] == '\r') {
                            out.write(buf, startPos, endPos - startPos);
                            startPos = endPos + 1;
                        }
                    }
                    if (startPos < numRead) {
                        out.write(buf, startPos, endPos - startPos);
                    }
                }
                sessionThread.writeCmdResponse("226 file transfer succeed(ASCII)");
            } catch (IOException ignored){
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
            //BINARY模式
        } else if(sessionThread.getAsciiBinary() == SessionThread.ASCIIBinary.BINARY){
            InputStream in = null;
            OutputStream out = null;
            try {
                in = sessionThread.getDataSocket().getInputStream();
                out = new BufferedOutputStream(new FileOutputStream(filePath));

                FileHeader fileHeader = JSON.parseObject(readLine(in), FileHeader.class);

                int totalRead = 0;//总共读取了多少字节

                while (fileHeader.size > 0) {
                    int numRead = in.read(buf);
                    if (numRead == -1) {
                        throw new IOException();
                    }
                    totalRead += numRead;
                    if (totalRead < fileHeader.size) {//未读完
                        out.write(buf, 0, numRead);
                    } else if (totalRead >= fileHeader.size) {//读完break
                        out.write(buf, 0, numRead);
                        break;
                    }
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
                }catch (IOException e){

                }
            }

        }
        sessionThread.closeDataSocket();
    }

    public static String readLine(InputStream in) throws IOException {
        List<Byte> bytesList = new ArrayList<>();
        boolean streamClosed = false;
        while (true) {
            int a = in.read();
            if (a == -1) {
                streamClosed = true;
                break;
            }
            byte b = (byte) a;
            if (b == '\n') {
                break;
            }
            bytesList.add(b);
        }
        if (streamClosed) {//如果流终止了，就抛出异常
            throw new IOException();
        }
        if (bytesList.get(bytesList.size() - 1) == '\r') {
            bytesList.remove(bytesList.size() - 1);
        }

        byte[] byteArr = new byte[bytesList.size()];
        for (int i = 0; i < byteArr.length; i++) {
            byteArr[i] = bytesList.get(i);
        }
        return new String(byteArr);
    }

}

