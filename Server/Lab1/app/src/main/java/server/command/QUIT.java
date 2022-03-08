package server.command;


public class QUIT extends CMD{

    public QUIT(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {
        sessionThread.setLogin(false);
        sessionThread.setUsername(null);
        sessionThread.writeCmdResponse("221 quit succeed");
        sessionThread.closeCmdSocket();
    }
}
