package server.command;

import android.util.Log;

public class TYPE extends CMD{

    public TYPE(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {

        if(!cmdArg.equalsIgnoreCase("A")&&!cmdArg.equalsIgnoreCase("B")){
            sessionThread.writeCmdResponse("500 commamd TYPE arguments wrong(should be A(AScii)/B(Binary))");
        } else if(cmdArg.equalsIgnoreCase("A")){
            sessionThread.setACSIIBinary(SessionThread.ASCIIBinary.ASCII);
            sessionThread.writeCmdResponse("200 ASCII type set");
        } else if(cmdArg.equalsIgnoreCase("B")){
            sessionThread.setACSIIBinary(SessionThread.ASCIIBinary.BINARY);
            sessionThread.writeCmdResponse("200 Binary type set");
        }
    }
}
