package clientCore;

public class DataInfo {
    long size;
    long progressed;
    long remain;
    String filename;

    public enum Type {
        DOWNLOAD, UPLOAD
    }

    Type type;

    public DataInfo(String filename, long size, long progressed, Type type) {
        this.size = size;
        this.progressed = progressed;
        this.remain = size - progressed;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("%s,文件:%s,已经:%d,还剩:%d", type, filename, progressed, remain);
    }

    public long getSize() {
        return size;
    }

    public Type getType() {
        return type;
    }

    public String getFilename() {
        return filename;
    }

    public long getRemain() {
        return remain;
    }

    public long getProgressed() {
        return progressed;
    }
}
