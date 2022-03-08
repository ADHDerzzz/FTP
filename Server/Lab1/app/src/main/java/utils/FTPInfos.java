package utils;

import java.util.HashMap;
import java.util.Map;

public class FTPInfos {
    public static Map<String, String> user_pass = new HashMap<>();
    public static String rootPath;//文件根目录地址

    public static void init() {
        //内置用户名和密码
        user_pass.put("anonymous", null);
        user_pass.put("test", "test");
    }

    public static void setRootPath(String path){
        rootPath = path;
    }
}
