package downloader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

public class HttpUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;
    private static final int MAX_REDIRECTS = 10;
    private static final int MAX_RETRIES = 3;
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    public static class ProbeResult {
        public final String resolvedUrl;
        public final long fileSize;
        public final boolean supportsRange;
        public final String fileName;

        ProbeResult(String resolvedUrl, long fileSize, boolean supportsRange, String fileName) {
            this.resolvedUrl = resolvedUrl;
            this.fileSize = fileSize;
            this.supportsRange = supportsRange;
            this.fileName = fileName;
        }
    }

    public static ProbeResult probe(String urlString) throws IOException {
        String resolved = resolveRedirect(urlString);

        String fileName = extractFileName(resolved);
        URL url = new URL(resolved);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=0-0");

        int code = conn.getResponseCode();
        long fileSize = -1;
        boolean supportsRange = false;

        if (code == HttpURLConnection.HTTP_PARTIAL) {
            supportsRange = true;
            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange != null) {
                int slashIdx = contentRange.lastIndexOf('/');
                if (slashIdx >= 0) {
                    try {
                        fileSize = Long.parseLong(contentRange.substring(slashIdx + 1));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } else if (code == HttpURLConnection.HTTP_OK) {
            fileSize = conn.getContentLengthLong();
        } else {
            conn.disconnect();
            throw new IOException("Probe failed, server returned HTTP " + code);
        }

        conn.disconnect();
        return new ProbeResult(resolved, fileSize, supportsRange, fileName);
    }

    public static long downloadChunk(String urlString, long startByte, long endByte,
                                      RandomAccessFile raf, AtomicLong chunkProgress,
                                      DownloadInfo info) throws IOException {
        int attempt = 0;
        IOException lastError = null;

        while (attempt < MAX_RETRIES) {
            try {
                return downloadChunkOnce(urlString, startByte, endByte, raf, chunkProgress, info);
            } catch (IOException e) {
                lastError = e;
                attempt++;
                if (attempt >= MAX_RETRIES || info.getStatus() != DownloadInfo.Status.DOWNLOADING) {
                    throw lastError;
                }
                chunkProgress.set(0);
                sleep(attempt * 1000L);
            }
        }
        throw lastError;
    }

    private static long downloadChunkOnce(String urlString, long startByte, long endByte,
                                           RandomAccessFile raf, AtomicLong chunkProgress,
                                           DownloadInfo info) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("Chunk download failed, server returned HTTP " + code);
        }

        long chunkSize = endByte - startByte + 1;
        long totalRead = 0;

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()
                    || info.getStatus() == DownloadInfo.Status.PAUSED
                    || info.getStatus() == DownloadInfo.Status.CANCELLED) {
                    return totalRead;
                }

                synchronized (raf) {
                    raf.seek(startByte + totalRead);
                    raf.write(buffer, 0, bytesRead);
                }

                totalRead += bytesRead;
                chunkProgress.addAndGet(bytesRead);
                info.addDownloadedBytes(bytesRead);
            }
        } finally {
            conn.disconnect();
        }

        if (totalRead < chunkSize && code == HttpURLConnection.HTTP_OK) {
            return totalRead;
        }
        return totalRead;
    }

    public static void downloadFull(String urlString, OutputStream out,
                                     AtomicLong progress, DownloadInfo info) throws IOException {
        int attempt = 0;
        IOException lastError = null;

        while (attempt < MAX_RETRIES) {
            try {
                downloadFullOnce(urlString, out, progress, info);
                return;
            } catch (IOException e) {
                lastError = e;
                attempt++;
                if (attempt >= MAX_RETRIES || info.getStatus() != DownloadInfo.Status.DOWNLOADING) {
                    throw lastError;
                }
                sleep(attempt * 1000L);
            }
        }
        throw lastError;
    }

    private static void downloadFullOnce(String urlString, OutputStream out,
                                          AtomicLong progress, DownloadInfo info) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("Download failed, server returned HTTP " + code);
        }

        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()
                    || info.getStatus() == DownloadInfo.Status.PAUSED
                    || info.getStatus() == DownloadInfo.Status.CANCELLED) {
                    return;
                }

                out.write(buffer, 0, bytesRead);
                progress.addAndGet(bytesRead);
                info.addDownloadedBytes(bytesRead);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String resolveRedirect(String urlString) throws IOException {
        String current = urlString;

        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-0");

            int code = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            conn.disconnect();

            if (code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307 || code == 308) {
                if (location == null) {
                    throw new IOException("Redirect with no Location header at " + current);
                }
                if (!location.startsWith("http")) {
                    location = new URL(new URL(current), location).toString();
                }
                current = location;
            } else if (code == HttpURLConnection.HTTP_OK
                       || code == HttpURLConnection.HTTP_PARTIAL) {
                return current;
            } else {
                throw new IOException("Server returned HTTP " + code + " at " + current);
            }
        }
        throw new IOException("Too many redirects, last URL: " + current);
    }

    public static String extractFileName(String urlString) {
        try {
            String path = new URL(urlString).getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                return "download";
            }
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.isEmpty()) {
                return "download";
            }
            int queryIdx = name.indexOf('?');
            if (queryIdx >= 0) {
                name = name.substring(0, queryIdx);
            }
            return name;
        } catch (Exception e) {
            return "download";
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}