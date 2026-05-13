package downloader;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadInfo {

    public enum Status {
        PENDING, DOWNLOADING, PAUSED, COMPLETED, ERROR, CANCELLED
    }

    private String url;
    private String resolvedUrl;
    private String saveDir;
    private String fullPath;
    private String fileName;
    private long fileSize;
    private int threadCount;
    private volatile Status status;
    private String errorMessage;

    private final AtomicLong downloadedBytes;
    private final AtomicLong lastSecondBytes;
    private volatile long speed;

    public DownloadInfo() {
        this.status = Status.PENDING;
        this.threadCount = 4;
        this.downloadedBytes = new AtomicLong(0);
        this.lastSecondBytes = new AtomicLong(0);
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getResolvedUrl() { return resolvedUrl; }
    public void setResolvedUrl(String resolvedUrl) { this.resolvedUrl = resolvedUrl; }

    public String getSaveDir() { return saveDir; }
    public void setSaveDir(String saveDir) { this.saveDir = saveDir; }

    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getThreadCount() { return threadCount; }
    public void setThreadCount(int threadCount) { this.threadCount = threadCount; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDownloadedBytes() { return downloadedBytes.get(); }

    public void addDownloadedBytes(long bytes) {
        downloadedBytes.addAndGet(bytes);
    }

    public long getSpeed() { return speed; }

    public void updateSpeed() {
        long current = downloadedBytes.get();
        long last = lastSecondBytes.getAndSet(current);
        speed = current - last;
    }

    public int getProgress() {
        if (fileSize <= 0) return 0;
        long downloaded = downloadedBytes.get();
        if (downloaded >= fileSize) return 100;
        return (int) (downloaded * 100 / fileSize);
    }

    public void resolveSavePath() {
        if (saveDir == null || saveDir.isEmpty()) {
            saveDir = System.getProperty("user.home");
        }
        File dir = new File(saveDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        fullPath = new File(dir, fileName).getAbsolutePath();
    }
}