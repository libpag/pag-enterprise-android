package libpag.pagviewer;

public class MediaItem {
    private String path;
    private MediaType type;

    public MediaItem(String path, MediaType type) {
        this.path = path;
        this.type = type;
    }

    public enum MediaType {
        IMAGE, VIDEO
    }

    public String getPath() {
        return path;
    }

    public MediaType getType() {
        return type;
    }

    public boolean isVideo() {
        return MediaType.VIDEO == type;
    }

    public boolean isImage() {
        return MediaType.IMAGE == type;
    }

    @Override
    public String toString() {
        return "MediaItem{"
                + "path='" + path + '\''
                + ", type=" + type
                + '}';
    }
}