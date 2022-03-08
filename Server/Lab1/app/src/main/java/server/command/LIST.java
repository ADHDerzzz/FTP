package server.command;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import utils.FTPInfos;

public class LIST extends CMD{

    private final List<String> fileNames = new ArrayList<>();

    public LIST(String cmdType, String cmdArg, SessionThread sessionThread) {
        super(cmdType, cmdArg, sessionThread);
    }

    @Override
    public void run() {

        File rootPathFile = new File(FTPInfos.rootPath);
        File folder = new File(FTPInfos.rootPath + File.separator + cmdArg);

        //判断是否存在，是文件夹，如果不是就返回给用户错误信息
        if (!folder.exists() || !folder.isDirectory()) {
            sessionThread.writeCmdResponse("501 folder not found");
            return;
        }

        fileNames.clear();
        findAllFile(folder);

        sessionThread.writeCmdResponse("200 file ok");

        //将每个文件的文件名去除掉根目录的前缀后，写给客户端
        for (String filename : fileNames) {
            //去掉绝对目录文件名的根目录前缀
            String newName = File.separator + new StringBuilder(filename).delete(0, rootPathFile.getAbsolutePath().length()).toString();
            newName = newName.replace("\\", "/");
            sessionThread.writeCmdResponse(newName);
        }

        //EOF标明结束
        sessionThread.writeCmdResponse("EOF");
    }

    private void findAllFile(File folder) {
        File[] files = folder.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                findAllFile(f);
            } else {
                fileNames.add(f.getAbsolutePath());
            }
        }
    }
}
