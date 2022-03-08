package server.command;

public class NOOP extends CMD{

    public NOOP(String cmdType, String cmdArg , SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {
        sessionThread.writeCmdResponse("200 NOOP ok");
    }
}
