package server.command;

import utils.FTPInfos;

public class USER extends CMD implements Runnable{

    public USER(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {
        String username = cmdArg;// USER命令的参数为用户名

        //若与内置的用户名不匹配
        if (!FTPInfos.user_pass.containsKey(username)) {
            sessionThread.writeCmdResponse("530 Invalid username");
            return;
        } else if(username.equalsIgnoreCase("anonymous")){
            sessionThread.writeCmdResponse("230 anonymous succeed");
            sessionThread.setAnonymous(true);
        } else {
            sessionThread.writeCmdResponse("331 Send password");
        }
        sessionThread.setUsername(username);
    }
}
