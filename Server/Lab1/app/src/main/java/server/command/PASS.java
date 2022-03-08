package server.command;

import utils.FTPInfos;

public class PASS extends CMD{

    public PASS(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {
        String password = cmdArg;
        String username = sessionThread.getUsername();

        if (username == null) {
            sessionThread.writeCmdResponse("503 Must send USER first");
            return;
        } else if(!FTPInfos.user_pass.containsKey(username)||!FTPInfos.user_pass.get(username).equals(password)){
            sessionThread.writeCmdResponse("530 username or password wrong");
        } else {
            sessionThread.setLogin(true);
            sessionThread.writeCmdResponse(String.format("230 %s login succeed",username));
        }
    }
}
