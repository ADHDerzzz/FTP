package clientCore;

public class FileHeader {
    public enum Compressed {
        COMPRESSED,
        UNCOMPRESSED
    }

    public long size;
    public Compressed compressed;

    public FileHeader() {}

    public FileHeader(long size, Compressed compressed) {
        this.size = size;
        this.compressed = compressed;
    }

    public long getSize() {
        return size;
    }

    public Compressed getCompressed() {
        return compressed;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setCompressed(Compressed compressed) {
        this.compressed = compressed;
    }
}
