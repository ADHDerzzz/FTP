package server.command;

import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;

//使程序有更好的可扩展性
public abstract class CMD implements Runnable{

    private static final String TAG = CMD.class.getSimpleName();

    protected final String cmdType;
    protected final String cmdArg;
    protected SessionThread sessionThread;

    public CMD(String cmdType, String cmdArg, SessionThread sessionThread) {
        this.cmdType = cmdType;
        this.cmdArg = cmdArg;
        this.sessionThread = sessionThread;
    }

    public String getCmdType() {
        return cmdType;
    }

    public String getCmdArg() {
        return cmdArg;
    }

    @Override
    abstract public void run();

    protected static void parseCommand(SessionThread session, String inputString) throws IOException {
        String[] strings = inputString.split(" ");
        if (strings == null) {
            session.writeCmdResponse("502 Command parse error");
            return;
        }
        if (strings.length < 1) {
            session.writeCmdResponse("No strings parsed");//
            return;
        }

        String cmdType = strings[0];
        String cmdArg = "";
        if(strings.length>1) {
            cmdArg = strings[1];
        }

        if (cmdType.length() != 4) {
            session.writeCmdResponse("Invalid command type");
            return;
        }

        cmdType = cmdType.trim();
        cmdType = cmdType.toUpperCase();

        //获取对应的命令的具体类对象
        CMD cmdInstance = null;
        Class<?> clazz;
        try {
            clazz = Class.forName(String.format("server.command.%s", cmdType));
            //获得构造函数，第一个是命令类型，第二个是命令参数,第三个是sessionThread
            Constructor<?> constructor = clazz.getConstructor(String.class, String.class, SessionThread.class);
            //用构造函数创建对象并返回
            cmdInstance = (CMD) constructor.newInstance(cmdType, cmdArg, session);

        } catch (Throwable e) {
            Log.e(TAG, "Instance creation error");
        }

        if (cmdInstance == null) {
            session.writeCmdResponse("Ignoring unrecognized FTP command: " + cmdType);
            return;
        }

        if (session.isLogin()||session.isAnonymous()) {
            cmdInstance.run();
        } else if (cmdInstance.getClass().equals(USER.class)
                || cmdInstance.getClass().equals(PASS.class)
                || cmdInstance.getClass().equals(QUIT.class)) {
            cmdInstance.run();
        } else {
            session.writeCmdResponse("530 Login first with USER and PASS, or QUIT");
        }
    }
}
