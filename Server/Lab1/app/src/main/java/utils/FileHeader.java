package utils;

public class FileHeader {
    public long size;
    public String filename;

    public FileHeader(){

    }

    public FileHeader(long size, String filename) {
        this.size = size;
        this.filename = filename;
    }
}
