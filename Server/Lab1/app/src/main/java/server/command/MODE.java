package server.command;

public class MODE extends CMD{

    public MODE(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {
        switch (cmdArg){
            case "S":
                sessionThread.setMode(SessionThread.MODE.Stream);
                sessionThread.writeCmdResponse("220 set MODE STREAM");
                break;
            case "B":
                sessionThread.setMode(SessionThread.MODE.Block);
                sessionThread.writeCmdResponse("220 set MODE BLOCK");
                break;
            case "C":
                sessionThread.setMode(SessionThread.MODE.Compress);
                sessionThread.writeCmdResponse("220 set MODE COMPRESS");
                break;
            default:
                sessionThread.writeCmdResponse("444 command arguments wrong");
        }
    }
}
