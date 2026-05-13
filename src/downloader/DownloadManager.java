package downloader;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class DownloadManager {

    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Future<?>> activeTasks;

    public DownloadManager() {
        this.executor = Executors.newCachedThreadPool();
        this.activeTasks = new ConcurrentHashMap<>();
    }

    public void startDownload(DownloadInfo info, ProgressCallback callback) {
        Future<?> future = executor.submit(() -> {
            try {
                download(info, callback);
            } catch (Exception e) {
                if (info.getStatus() == DownloadInfo.Status.DOWNLOADING
                    || info.getStatus() == DownloadInfo.Status.PENDING) {
                    info.setErrorMessage(e.getMessage());
                    info.setStatus(DownloadInfo.Status.ERROR);
                    if (callback != null) callback.onError(info);
                }
            }
        });
        activeTasks.put(info.getUrl(), future);
    }

    public void pauseDownload(DownloadInfo info) {
        info.setStatus(DownloadInfo.Status.PAUSED);
    }

    public void resumeDownload(DownloadInfo info, ProgressCallback callback) {
        startDownload(info, callback);
    }

    public void cancelDownload(DownloadInfo info) {
        Future<?> future = activeTasks.remove(info.getUrl());
        if (future != null) {
            future.cancel(true);
        }
        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING
            || info.getStatus() == DownloadInfo.Status.PAUSED) {
            info.setStatus(DownloadInfo.Status.CANCELLED);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void download(DownloadInfo info, ProgressCallback callback) throws Exception {
        HttpUtil.ProbeResult probe = HttpUtil.probe(info.getUrl());

        info.setResolvedUrl(probe.resolvedUrl);
        info.setFileName(probe.fileName);
        info.setFileSize(probe.fileSize);
        info.resolveSavePath();

        info.setStatus(DownloadInfo.Status.DOWNLOADING);
        if (callback != null) callback.onStart(info);

        ScheduledExecutorService speedScheduler = Executors.newSingleThreadScheduledExecutor();
        speedScheduler.scheduleAtFixedRate(info::updateSpeed, 1, 1, TimeUnit.SECONDS);

        try {
            if (probe.supportsRange && probe.fileSize > 0 && info.getThreadCount() > 1) {
                downloadMultiThread(info, callback);
            } else {
                downloadSingleThread(info, callback);
            }
        } finally {
            speedScheduler.shutdown();
        }

        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING) {
            info.setStatus(DownloadInfo.Status.COMPLETED);
            if (callback != null) callback.onComplete(info);
        }
    }

    private void downloadMultiThread(DownloadInfo info, ProgressCallback callback) throws Exception {
        int threadCount = info.getThreadCount();
        long fileSize = info.getFileSize();
        long chunkSize = fileSize / threadCount;

        AtomicLongArray chunkProgress = new AtomicLongArray(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        RandomAccessFile raf = new RandomAccessFile(info.getFullPath(), "rw");
        raf.setLength(fileSize);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                final long startByte = i * chunkSize;
                final long endByte = (i == threadCount - 1) ? fileSize - 1 : (i + 1) * chunkSize - 1;
                final AtomicLong chunkBytes = new AtomicLong(0);

                executor.submit(() -> {
                    try {
                        HttpUtil.downloadChunk(
                            info.getResolvedUrl(), startByte, endByte,
                            raf, chunkBytes, info
                        );
                        chunkProgress.set(idx, chunkBytes.get());
                    } catch (IOException e) {
                        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING) {
                            info.setErrorMessage(e.getMessage());
                            info.setStatus(DownloadInfo.Status.ERROR);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            while (!latch.await(200, TimeUnit.MILLISECONDS)) {
                if (info.getStatus() != DownloadInfo.Status.DOWNLOADING) {
                    break;
                }
                if (callback != null) callback.onProgress(info);
            }
        } finally {
            raf.close();
        }
    }

    private void downloadSingleThread(DownloadInfo info, ProgressCallback callback) throws Exception {
        File outFile = new File(info.getFullPath());
        AtomicLong progress = new AtomicLong(0);

        try (FileOutputStream out = new FileOutputStream(outFile)) {
            HttpUtil.downloadFull(info.getResolvedUrl(), out, progress, info);
        }

        if (info.getStatus() == DownloadInfo.Status.DOWNLOADING
            && progress.get() > 0) {
            return;
        }
    }

    public interface ProgressCallback {
        void onStart(DownloadInfo info);
        void onProgress(DownloadInfo info);
        void onComplete(DownloadInfo info);
        void onError(DownloadInfo info);
    }
}