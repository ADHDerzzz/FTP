package server.command;

import android.util.Log;

//主动模式 客户端打开端口
public class PORT extends CMD{

    public PORT(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {

        //用户未传入端口号
        if (cmdArg==null){
            sessionThread.writeCmdResponse("501 command syntax wrong");
            return;
        }

        String[] splits = cmdArg.split(",");

        if(splits.length!=6){
            sessionThread.writeCmdResponse("500 command syntax error");
            return;
        }

        for(String i:splits){
            if (!i.matches("[0-9]+") || i.length() > 3) {
                sessionThread.writeCmdResponse("500 Invalid PORT argument:"+i);
                return;
            }
        }

        String clientIP = splits[0] + "." + splits[1] + "." + splits[2] + "." + splits[3];
        int clientPort = Integer.parseInt(splits[4]) * 256 + Integer.parseInt(splits[5]);

        sessionThread.setPassivePort(SessionThread.PassivePort.PORT);
        sessionThread.setClientIP(clientIP);
        sessionThread.setClientPort(clientPort);
        sessionThread.writeCmdResponse(String.format("200 PORT succeed! ClientIP:%s  ; ClientPort:%d ",clientIP,clientPort));
    }
}
