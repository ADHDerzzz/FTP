package clientCore;


import android.util.Log;
import com.alibaba.fastjson.JSON;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import clientCore.exceptions.ClientException;
import clientCore.exceptions.NoServerFoundException;

public class MyClient {
    // 属性
    // 控制连接
    Socket controlSocket = null;
    BufferedReader controlSocketReader = null;
    BufferedWriter controlSocketWriter = null;

    String thisHost;
    int thisPort;

    // 被动数据连接
    Socket dataSocket = null;

    // 主动数据连接
    volatile ServerSocket serverSocket = null;

    // 用户名和密码
    String username;
    String password;

    // PASV记录下来的服务器主机名和端口号
    String serverHost;
    volatile int serverPort;

    // 一些枚举
    public enum ClientPattern {PASSIVE, ACTIVE}
    volatile ClientPattern clientPattern = ClientPattern.PASSIVE;
    public enum TypeOfCode {BINARY, ASCII}
    volatile TypeOfCode typeOfCode = TypeOfCode.BINARY;
    public enum Mode {STREAM, BLOCK, COMPRESSED};
    volatile Mode mode  = Mode.STREAM;

    String downloadDirectory;

    volatile boolean progressMonitor = false;

    ArrayList<Monitor> monitors = new ArrayList<>();

//    ArrayList<String> absoluteFilePathInAFolder = new ArrayList<>();

    /**
     * 建立连接
     * @param host 主机
     * @param port 端口号
     * @throws NoServerFoundException 失败就是没和服务器建立连接
     */
    public MyClient(String host, int port) throws NoServerFoundException {
        // 建立控制连接
        try {
            controlSocket = new Socket(host, port);
            controlSocketReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            controlSocketWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
        } catch (IOException e) {
            // e.printStackTrace();
            throw new NoServerFoundException(e.getMessage());
        }
        thisHost = host;
        thisPort = port;
    }

    /**
     * 用户登录系统
     * @param username 用户名
     * @param password 密码
     * @return 是否登陆成功
     */
    public synchronized void logIn(String username, String password) throws ClientException {
        this.username = username;
        this.password = password;
        readAll();

        try {
            // 输入用户名
            write(String.format("USER %s", username));
            String resp = controlSocketReader.readLine();
            if (resp.startsWith("331")) {
                // 需要密码
                write(String.format("PASS %s", password));
                resp = controlSocketReader.readLine();
                if (resp == null) {
                    throw new ClientException("Can't read anything from server");
                }
            }
            if (!resp.startsWith("230")) {
                throw new ClientException(resp);
            }
        } catch (IOException e) {
            readAll();
            throw new ClientException(e.getMessage());
        }
    }

    /**
     * 看是否有数据流在传输，有的话关闭数据流，没有的话直接关掉所有的控制socket
     * @throws ClientException
     */
    public synchronized void quit() throws ClientException {
        readAll();

        try {
            write("QUIT");
            String resp = controlSocketReader.readLine();
            if (resp != null && resp.startsWith("221")) {
                if (controlSocket != null) {
                    controlSocket.close();
                }
                if (dataSocket != null) {
                    dataSocket.close();
                }
                if (controlSocketReader != null) {
                    controlSocketReader.close();
                }
                if (controlSocketWriter != null) {
                    controlSocketWriter.close();
                }
            } else {
                if (resp != null) {
                    throw new ClientException(resp);
                } else {
                    throw new ClientException(String.format("username: %s can't quit", username));
                }
            }
        } catch (IOException e) {
            readAll();
            Log.e("quit: IOException", e.getMessage());
            throw new ClientException(e.getMessage());
        }
    }

    /**
     * server打开serverSocket，client
     * 设成被动模式，获取server发过来的port和host，存下来备用
     * @throws ClientException
     */
    public synchronized void pasv() throws ClientException {
        readAll();
        String[] hostPort = null;
        try {
            write("PASV");
            String resp = controlSocketReader.readLine();
            if (resp == null) {
                readAll();
                throw new ClientException("Illegal response to command 'PASV'");
            }
            if (resp.endsWith(")")) {
                resp = resp.substring(0, resp.length() - 1);
            } else {
                throw new ClientException("Illegal response to command 'PASV'");
            }
            Pattern p = Pattern.compile("(?<=227 Entering Passive Mode \\()([0-9]+,){5}([0-9]+)");
            Matcher m = p.matcher(resp);
            if (m.find()) {
                hostPort = m.group().split(",");
            } else {
                readAll();
                throw new ClientException(resp);
            }
            serverHost = hostPort[0] + "." + hostPort[1] + "." + hostPort[2] + "." + hostPort[3];
            serverPort = Integer.parseInt(hostPort[4]) * 256 + Integer.parseInt(hostPort[5]);
            Log.e("pasv", String.valueOf(serverPort));
            clientPattern = ClientPattern.PASSIVE;
        } catch (IOException e) {
            readAll();
            Log.e("pasv:IOException", e.getMessage());
            throw new ClientException(e.getMessage());
        }
    }

    /**
     * 主动模式，client打开数据socket等待服务器链接
     * client accept
     * @throws ClientException 端口冲突；
     *                         关于socket的IO异常；
     *                         关于getLocalAddress的UnknownHostException
     */
    public synchronized void port() throws ClientException {
        readAll();
        boolean success = false;
        int p1 = 1, p2 = 1;
        ServerSocket newServerSocket = null;
        while (!success) {
            if (p2 < 256) {
                p2++;
            } else if (p1 < 256) {
                p1++;
            } else {
                readAll();
                throw new ClientException("no available port");
            }
            int port = p1 * 256 + p2;
            try {
                newServerSocket = new ServerSocket(port);
            } catch (IOException e) {
                continue;
            }
            success = true;
        }
        if (this.serverSocket != null) {
            try {
                this.serverSocket.close();
            } catch (IOException e) {
                readAll();
                Log.e("port:IOException", e.getMessage());
                throw new ClientException(e.getMessage());
            }
        }
        this.serverSocket = newServerSocket;
        String localAddress;
        localAddress = getLocalIPAddr().getHostAddress();
        String command = String.format("PORT %s,%d,%d", localAddress.replace('.', ','), p1, p2);
        try {
            write(command);
            String resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("200")) {
                readAll();
                throw new ClientException(resp);
            }
        } catch (IOException e) {
            readAll();
            Log.e("port:IOException", e.getMessage());
            throw new ClientException(e.getMessage());
        }
        clientPattern = ClientPattern.ACTIVE;
    }

    /**
     *
     * @param newTypeOfCode 新设置的编码
     * @throws ClientException IOException，奇怪的服务器响应
     */
    public synchronized void type(TypeOfCode newTypeOfCode) throws ClientException {
        readAll();
        String type = (newTypeOfCode == TypeOfCode.ASCII) ? "A" : "B";
        try {
            write(String.format("TYPE %s", type));
            String resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("200")) {
                readAll();
                Log.e("type", "wrong response from server");
                throw new ClientException("wrong response from server");
            }
        } catch (IOException e) {
            readAll();
            Log.e("type:IOException", e.getMessage());
            throw new ClientException(e.getMessage());
        }
        this.typeOfCode = newTypeOfCode;
    }

    public synchronized void mode(Mode mode) throws ClientException {
        readAll();
        String modeStr;
        switch (mode) {
            case BLOCK:
                modeStr = "B";
                break;
            case COMPRESSED:
                modeStr = "C";
                break;
            case STREAM:
            default:
                modeStr = "S";
        }
        String command = String.format("MODE %s", modeStr);
        try {
            write(command);
            String resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("220")) {
                readAll();
                if (resp == null)
                    throw new ClientException("wrong response from server");
                else
                    throw new ClientException(resp);
            }
        } catch (IOException e) {
            throw new ClientException(e.getMessage());
        }
    }

    /**
     *
     * @throws ClientException 响应不规范，IOException
     */
    public synchronized void noop() throws ClientException {
        readAll();
        try {
            write("NOOP");
            String resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("200")) {
                readAll();
                Log.e("noop", "Illegal response");
                throw new ClientException("Illegal response");
            }
        } catch (IOException e) {
            readAll();
            Log.e("noop:IOException", e.getMessage());
            throw new ClientException(e.getMessage());
        }
    }

    /**
     *
     * @param destination 目标绝对地址
     * @param toBeUploaded 要上传文件的绝对地址
     * @throws ClientException 比如对面出问题了。。。
     */
    public synchronized void stor(String destination, String toBeUploaded) throws ClientException {
        readAll();
        try {
            write(String.format("STOR %s", destination));
            Log.e("hh", String.format("STOR %s", destination));
            String resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("200")) {
                readAll();
                Log.e("stor", "error in response");
                if (resp != null) {
                    throw new ClientException(resp);
                } else {
                    throw new ClientException("No response");
                }
            }
            connectToServer();
            resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("125")) {
                throw new ClientException(resp);
            }

            if (typeOfCode == TypeOfCode.ASCII) {
                Scanner scanner = null;
                try {
                    scanner = new Scanner(new FileInputStream(toBeUploaded));
                    OutputStream outputStream = dataSocket.getOutputStream();
                    String data;
                    while (scanner.hasNext()) {
                        data = scanner.nextLine();
                        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    readAll();
                    Log.e("stor", "IO errors in stor");
                    throw new ClientException(e.getMessage());
                } finally {
                    if (scanner != null) {
                        scanner.close();
                    }
                    dataSocket.close();
                    dataSocket = null;
                }
                resp = controlSocketReader.readLine();
                if (resp == null || !resp.startsWith("226")) {
                    if (resp != null) {
                        throw new ClientException(resp);
                    } else {
                        throw new ClientException("No response");
                    }
                }
            } else {
                try {
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(toBeUploaded));
                    OutputStream outputStream = dataSocket.getOutputStream();
                    long size = new File(toBeUploaded).length();
                    FileHeader fileHeader = new FileHeader(size, FileHeader.Compressed.UNCOMPRESSED);
                    outputStream.write((JSON.toJSONString(fileHeader) + "\r\n").getBytes(StandardCharsets.UTF_8));
                    long process = 0;
                    byte[] buffer = new byte[1024 * 1024];
                    int canRead;
                    while ((canRead = bufferedInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, canRead);
                        outputStream.flush();
                        process += canRead;
                        // 这里搞一个记录进度的东西
                        if (progressMonitor) {
                            notifyAllMonitors(new DataInfo(toBeUploaded, fileHeader.size, process, DataInfo.Type.UPLOAD));
                        }
                    }
                } catch (IOException e) {
                    readAll();
                    Log.e("STOR", "fail to upload");
                    if (dataSocket != null) {
                        dataSocket.close();
                        dataSocket = null;
                    }
                }
                resp = controlSocketReader.readLine();
                if (resp == null || !resp.startsWith("226")) {
                    if (resp != null) {
                        throw new ClientException(resp);
                    } else {
                        throw new ClientException("No response");
                    }
                }
            }
        } catch (IOException e) {
            readAll();
            Log.e("STOR", "readline and close");
            throw new ClientException(e.getMessage());
        }
    }

    /**
     *
     * @param localFilePath 要上传文件在本机上的绝对路径
     * @param destinationFolder 要传到对方机器上的目标文件夹
     * @throws ClientException 不是文件
     */
    public synchronized void storFile(String localFilePath, String destinationFolder) throws ClientException {
        readAll();
        File file = new File(localFilePath);
        if (!file.isFile()) {
            throw new ClientException("not a file");
        }
        String fileName = file.getName();
        stor(destinationFolder + File.separator + fileName, file.getAbsolutePath());
    }

    /**
     *
     * @param localFolderPath 要上传文件夹在本机上的绝对路径
     * @param destinationFolder 要传到对方的文件夹
     * @throws ClientException storFile的exceptions
     */
    public synchronized void storFolder(String localFolderPath, String destinationFolder) throws ClientException {
        readAll();
        ArrayList<String> absoluteFilePaths = getAllAbsoluteFilePathInAFolder(localFolderPath);
        ArrayList<String> absoluteDestinationPaths = getAllUploadedPaths(absoluteFilePaths, destinationFolder, localFolderPath);
        for (int i = 0; i < absoluteFilePaths.size(); i++) {
            stor(absoluteDestinationPaths.get(i), absoluteFilePaths.get(i));
        }
    }

    public synchronized void storFolderMultiThread(String localFolderPath, String destinationFolder) throws ClientException {
        ArrayList<String> absoluteFilePaths = getAllAbsoluteFilePathInAFolder(localFolderPath);
        ArrayList<String> absoluteDestinationPaths = getAllUploadedPaths(absoluteFilePaths, destinationFolder, localFolderPath);

        MyClient client;
        try {
            client = cloneClient();
        } catch (NoServerFoundException e) {
            throw new ClientException(e.getMessage());
        }

        ArrayList<Exception> exceptions = new ArrayList<>();

        int sz = absoluteDestinationPaths.size();
        List<String> absoluteFilePathsA = absoluteFilePaths.subList(0, sz / 2);
        List<String> absoluteFilePathsB = absoluteFilePaths.subList(sz / 2, sz);
        List<String> absoluteDestinationPathsA = absoluteDestinationPaths.subList(0, sz / 2);
        List<String> absoluteDestinationPathsB = absoluteDestinationPaths.subList(sz / 2, sz);

        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < absoluteFilePathsA.size(); i++) {
                    client.stor(absoluteDestinationPathsA.get(i), absoluteFilePathsA.get(i));
                }
            } catch (ClientException e) {
                exceptions.add(e);
            }
        });

        t.start();
        for (int i = 0; i < absoluteFilePathsB.size(); i++) {
            stor(absoluteDestinationPathsB.get(i), absoluteFilePathsB.get(i));
        }

        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.close();

        if (exceptions.size() != 0) {
            throw new ClientException(exceptions.get(0).getMessage());
        }
    }

    /**
     *
     * @param toBeRetrieved 取回的文件绝对路径
     * @param destination 取回之后存的路径
     * @throws ClientException 就是异常
     */
    public synchronized void retr(String toBeRetrieved, String destination) throws ClientException {
        readAll();
        try {
            write(String.format("RETR %s", toBeRetrieved));
        } catch (IOException e) {
            Log.e("retr", "can't write RETR command");
            throw new ClientException("can't write RETR command");
        }
        String resp;
        try {
            resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("200")) {
                readAll();
                if (resp != null) {
                    throw new ClientException(resp);
                } else {
                    throw new ClientException("No response");
                }
            }
        } catch (IOException e) {
            Log.e("retr", "error in response");
            readAll();
            throw new ClientException(e.getMessage());
        }
        connectToServer();
        try {
            resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("125")) {
                readAll();
                if (resp != null) {
                    throw new ClientException(resp);
                } else {
                    throw new ClientException("No response");
                }
            }
        } catch (IOException e) {
            Log.e("retr", "error in response");
            readAll();
            throw new ClientException(e.getMessage());
        }

        if (typeOfCode == TypeOfCode.ASCII) {
            try {
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(destination)));
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                int lineNum = 0;
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (lineNum > 0) {
                        bufferedWriter.newLine();
                    }
                    bufferedWriter.write(line);
                    lineNum++;
                }
                bufferedWriter.close();
                bufferedReader.close();
            } catch (IOException e) {
                throw new ClientException(e.getMessage());
            }
        } else {
            BufferedOutputStream bufferedOutputStream;
            InputStream inputStream;
            try {
                bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
                inputStream = dataSocket.getInputStream();
                FileHeader fileHeader = JSON.parseObject(readHeader(inputStream), FileHeader.class);

                byte[] buffer = new byte[1024 * 1024];
                int read = 0;
                int canRead;
                while ((canRead = inputStream.read(buffer)) != -1) {
                    bufferedOutputStream.write(buffer, 0, canRead);
                    read += canRead;

                    if (progressMonitor) {
                        notifyAllMonitors(new DataInfo(destination, fileHeader.size, read, DataInfo.Type.DOWNLOAD));
                    }
                }
                bufferedOutputStream.close();
                inputStream.close();
            } catch (IOException e) {
                readAll();
                throw new ClientException(e.getMessage());
            }
        }
        try {
            dataSocket.close();
            dataSocket = null;
            resp = controlSocketReader.readLine();
            if (resp == null || !resp.startsWith("226")) {
                readAll();
                if (resp != null) {
                    throw new ClientException(resp);
                } else {
                    throw new ClientException("No response");
                }
            }
        } catch (IOException e) {
            readAll();
            throw new ClientException(e.getMessage());
        }
    }

    /**
     *
     * @param toBeRetrieved 要取回文件的绝对路径
     * @throws ClientException 没找到文件目录
     */
    public synchronized void retrFile(String toBeRetrieved) throws ClientException {
        if (downloadDirectory == null) {
            throw new ClientException("no download directory found");
        }
        String[] hierarchy = toBeRetrieved.split("[/]|[\\\\]");
        String fileName = hierarchy[hierarchy.length - 1];
        String destination = downloadDirectory + File.separator + fileName;
        retr(toBeRetrieved, destination);
    }

    public synchronized ArrayList<String> list(String folderName) throws ClientException {
        readAll();
        String command = String.format("LIST %s", folderName);
        try {
            write(command);
        } catch (IOException e) {
            readAll();
            throw new ClientException(e.getMessage());
        }

        String resp;
        try {
            resp = controlSocketReader.readLine();
        } catch (IOException e) {
            readAll();
            throw new ClientException(e.getMessage());
        }

        if (resp == null) {
            readAll();
            throw new ClientException("no response from server");
        } else if (!resp.startsWith("200")) {
            readAll();
            throw new ClientException(resp);
        }

        ArrayList<String> fileNames = new ArrayList<>();

        String fileName;
        try {
            while ((fileName = controlSocketReader.readLine()) != null && !fileName.equals("EOF")) {
                fileNames.add(fileName);
            }
        } catch (IOException e) {
            throw new ClientException(e.getMessage());
        }

        return fileNames;
    }

    public synchronized void retrFolder(String toBeRetrieved) throws ClientException {
        String[] hierarchy = toBeRetrieved.split("[/]|[\\\\]");
        ArrayList<String> filesToBeRetrieved = list(hierarchy[hierarchy.length - 1]);
        for (int i = 0; i < filesToBeRetrieved.size(); i++) {
            String tmp = filesToBeRetrieved.get(i).replace("//", "/");
            filesToBeRetrieved.remove(i);
            filesToBeRetrieved.add(i,tmp);
        }
        ArrayList<String> destinations = getAllUploadedPaths(filesToBeRetrieved, downloadDirectory, toBeRetrieved);
        createDirs(destinations);
        for (int i = 0; i < filesToBeRetrieved.size(); i++) {
            retr(filesToBeRetrieved.get(i), destinations.get(i));
        }
    }

    public synchronized void retrFolderMultiThread(String toBeRetrieved) throws ClientException {
        readAll();
        MyClient client;
        try {
            client = cloneClient();
        } catch (NoServerFoundException e) {
            throw new ClientException(e.getMessage());
        }

        String[] hierarchy = toBeRetrieved.split("[/]|[\\\\]");
        ArrayList<String> filesToBeRetrieved = list(hierarchy[hierarchy.length - 1]);
        for (int i = 0; i < filesToBeRetrieved.size(); i++) {
            String tmp = filesToBeRetrieved.get(i).replace("//", "/");
            filesToBeRetrieved.remove(i);
            filesToBeRetrieved.add(i,tmp);
        }
        ArrayList<String> destinations = getAllUploadedPaths(filesToBeRetrieved, downloadDirectory, toBeRetrieved);
        createDirs(destinations);

        int sz = destinations.size();
        List<String> filesToBeRetrievedA = filesToBeRetrieved.subList(0, sz / 2);
        List<String> filesToBeRetrievedB = filesToBeRetrieved.subList(sz / 2, sz);
        List<String> destinationsA = destinations.subList(0, sz / 2);
        List<String> destinationsB = destinations.subList(sz / 2, sz);

        ArrayList<Exception> exceptions = new ArrayList<>();

        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < filesToBeRetrievedA.size(); i++) {
                    client.retr(filesToBeRetrievedA.get(i), destinationsA.get(i));
                }
            } catch (ClientException e) {
                exceptions.add(e);
            }
        });

        t.start();

        for (int i = 0; i < filesToBeRetrievedB.size(); i++) {
            retr(filesToBeRetrievedB.get(i), destinationsB.get(i));
        }

        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.close();

        if (exceptions.size() > 0) {
            throw new ClientException(exceptions.get(0).getMessage());
        }
    }

    /**
     *
     * @param downloadDirectory 下载的文件夹
     * @throws ClientException 不是文件夹，没建文件夹
     */
    public synchronized void setDownloadDirectory(String downloadDirectory) throws ClientException {
        File downloadDir = new File(downloadDirectory);
        if (!downloadDir.exists()) {
            throw new ClientException("can't find the directory");
        }
        if (!downloadDir.isDirectory()) {
            throw new ClientException("not a directory");
        }
        this.downloadDirectory = downloadDirectory;
    }

    // 工具方法
    void readAll() {
        try {
            while (controlSocketReader.ready()) {
                controlSocketReader.read();
            }
        } catch (IOException e) {
            Log.e("readAll: IOException", e.getMessage());
        }
    }

    /**
     * 向服务器发消息
     * @param content 写入内容
     */
    void write(String content) throws IOException {
        controlSocketWriter.write(content);
        controlSocketWriter.write("\r\n");
        controlSocketWriter.flush();
    }

    /*String getLocalIPAddr() throws UnknownHostException {
        InetAddress address = InetAddress.getLocalHost();
        return address.getHostAddress();
    }*/

    public InetAddress getLocalIPAddr() {
        try {
            InetAddress candidateAddress = null;

            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface iface = networkInterfaces.nextElement();
                // 该网卡接口下的ip会有多个，也需要一个个的遍历，找到自己所需要的
                for (Enumeration<InetAddress> inetAddrs = iface.getInetAddresses(); inetAddrs.hasMoreElements(); ) {
                    InetAddress inetAddr = inetAddrs.nextElement();

                    //排除ipv6地址
                    if (!(inetAddr instanceof Inet4Address)) {
                        continue;
                    }

                    // 排除loopback回环类型地址
                    if (!inetAddr.isLoopbackAddress()) {
                        if (inetAddr.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了 就是我们要找的
                            // ~~~~~~~~~~~~~绝大部分情况下都会在此处返回你的ip地址值~~~~~~~~~~~~~
                            return inetAddr;
                        }

                        // 若不是site-local地址 那就记录下该地址当作候选
                        if (candidateAddress == null) {
                            candidateAddress = inetAddr;
                        }

                    }
                }
            }

            // 如果出去loopback回环地之外无其它地址了，那就回退到原始方案吧
            return candidateAddress == null ? InetAddress.getLocalHost() : candidateAddress;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 主动模式：client accept
     * 被动模式：client打开Socket，对面accept
     */
    void connectToServer() throws ClientException{
        try {
            if (dataSocket != null) {
                dataSocket.close();
            }
        } catch (IOException e) {
            readAll();
            Log.e("connectToServer", "can't close data socket");
            throw new ClientException(e.getMessage());
        }

        if (clientPattern == ClientPattern.PASSIVE) {
            try {
                dataSocket = new Socket(serverHost, serverPort);
            } catch (IOException e) {
                readAll();
                Log.e("connectToServer", "can't open socket");
                throw new ClientException(e.getMessage());
            }
        } else {
            try {
                if (serverSocket == null) {
                    readAll();
                    Log.e("connectToServer", "active mode but no server socket");
                    throw new ClientException("active mode but no server socket");
                }
                dataSocket = serverSocket.accept();
            } catch (IOException e) {
                readAll();
                Log.e("connectToServer", "can't find the data server.");
                throw new ClientException("can't find the data server.");
            }
        }
    }

    /**
     *
     * @param inputStream 写入流
     * @return header信息
     * @throws IOException 流意外终止
     */
    String readHeader(InputStream inputStream) throws IOException {
        ArrayList<Byte> bytes = new ArrayList<>();
        int canRead;
        while ((canRead = inputStream.read()) != -1 && (byte)canRead != '\n') {
            bytes.add((byte)canRead);
        }
        if (canRead == -1) {
            throw new IOException();
        }
        if (bytes.get(bytes.size() - 1) == '\r') {
            bytes.remove(bytes.size() - 1);
        }
        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            result[i] = bytes.get(i);
        }
        return new String(result);
    }

    /**
     *
     * @param absoluteFolderPath 文件夹的绝对路径
     * @return 文件夹下面子文件绝对路径的链表
     * @throws ClientException 不是文件夹
     */
    ArrayList<String> getAllAbsoluteFilePathInAFolder(String absoluteFolderPath) throws ClientException {
        File folder = new File(absoluteFolderPath);
        if (!folder.isDirectory()) {
            throw new ClientException("not a folder");
        }
        ArrayList<String> result = new ArrayList<>();
        findAllSubFiles(folder, result);
        Log.e("getAllAbsoluteFile", result.toString());
        return result;
    }

    void findAllSubFiles(File folder, ArrayList<String> absoluteFilePaths) {
        File[] list = folder.listFiles();
        for (int i = 0; i < list.length; i++) {
            if (list[i].isDirectory()) {
                findAllSubFiles(list[i], absoluteFilePaths);
            } else {
                absoluteFilePaths.add(list[i].getAbsolutePath());
            }
        }
    }

    ArrayList<String> getAllUploadedPaths(ArrayList<String> absoluteFilePaths, String destinationFolder, String localFolder) {
        ArrayList<String> result = new ArrayList<>();
        String processedLocalFolder = localFolder.replace("\\\\", "/");
        String[] filenames = processedLocalFolder.split("/");
        String folderName = filenames[filenames.length - 1];
        if (destinationFolder.endsWith("/")) {
            destinationFolder = destinationFolder.substring(0, destinationFolder.length() - 1);
        } else if (destinationFolder.endsWith("\\\\")) {
            destinationFolder = destinationFolder.substring(0, destinationFolder.length() - 2);
        }
        // Log.e("getDestinationPaths:fo", processedLocalFolder);
        for (int i = 0; i < absoluteFilePaths.size(); i++) {
            String processedfilePath = absoluteFilePaths.get(i).replace("\\\\", "/");
            result.add(destinationFolder + "/" + folderName + processedfilePath.substring(processedLocalFolder.length()));
        }
        // Log.e("getDestinationPaths:foo", result.toString());
        return result;
    }

    public void setProgressMonitor(boolean progressMonitor) {
        this.progressMonitor = progressMonitor;
    }

    public void addMonitor(Monitor monitor) {
        monitors.add(monitor);
    }

    public void notifyAllMonitors(DataInfo dataInfo) {
        for (int i = 0; i < monitors.size(); i++) {
            monitors.get(i).tell(dataInfo);
        }
    }

    public void clearAllMonitors() {
        monitors.clear();
    }

    public void createDirs(ArrayList<String> fileList) {
        for (int i = 0; i < fileList.size(); i++) {
            File file = new File(fileList.get(i)).getAbsoluteFile();
            File parent = file.getParentFile();
            if (parent != null) {
                if (!parent.exists()) {
                    parent.mkdirs();
                }
            }
        }
    }

    public MyClient cloneClient() throws ClientException, NoServerFoundException {
        MyClient newClient = new MyClient(thisHost, thisPort);
        newClient.logIn(username, password);

        if (clientPattern == ClientPattern.PASSIVE) {
            newClient.pasv();
        } else {
            newClient.port();
        }

        newClient.type(typeOfCode);

        newClient.setDownloadDirectory(downloadDirectory);

        for (int i = 0; i < monitors.size(); i++) {
            newClient.addMonitor(monitors.get(i));
        }

        newClient.progressMonitor = progressMonitor;
        return newClient;
    }

    public void close() throws ClientException {
        try {
            if (controlSocket != null) {
                controlSocket.close();
            }
            if (dataSocket != null) {
                dataSocket.close();
            }
            if (controlSocketReader != null) {
                controlSocketReader.close();
            }
            if (controlSocketWriter != null) {
                controlSocketWriter.close();
            }
        } catch (IOException e) {
            throw new ClientException(e.getMessage());
        }
    }
}
