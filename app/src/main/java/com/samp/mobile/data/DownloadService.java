package com.samp.mobile.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.samp.mobile.game.SAMP;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DownloadService extends Service {
    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_START = "com.samp.mobile.action.START_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.samp.mobile.action.CANCEL_DOWNLOAD";
    public static final String BROADCAST_PROGRESS = "com.samp.mobile.broadcast.DOWNLOAD_PROGRESS";
    public static final String BROADCAST_COMPLETE = "com.samp.mobile.broadcast.DOWNLOAD_COMPLETE";
    public static final String BROADCAST_ERROR = "com.samp.mobile.broadcast.DOWNLOAD_ERROR";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_TOTAL = "total";

    private NotificationManager notificationManager;
    private volatile boolean cancelled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            cancelled = false;
            startForeground(NOTIFICATION_ID, buildNotification("Starting download...", 0, 0, false));
            new Thread(this::doDownload).start();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void doDownload() {
        try {
            updateNotification("Downloading manifest...", 0, 0, true);
            broadcastProgress("Downloading manifest...", 0, 0);

            String jsonStr = downloadUrlToString(DataConstants.MANIFEST_URL);
            JSONObject manifest = new JSONObject(jsonStr);
            JSONObject files = manifest.getJSONObject("files");
            int totalFiles = files.length();

            if (cancelled) return;

            updateNotification("Downloading game data...", 0, 100, false);
            broadcastProgress("Downloading game data...", 0, 100);

            File dataDir = getExternalFilesDir(null);
            if (dataDir == null) {
                showError("Storage not available");
                return;
            }

            File zipFile = new File(getCacheDir(), "gs_data_download.zip");
            downloadFile(DataConstants.DATA_ZIP_URL, zipFile);

            if (cancelled) { zipFile.delete(); return; }

            updateNotification("Extracting files...", 0, totalFiles, false);
            broadcastProgress("Extracting files...", 0, totalFiles);

            int extracted = extractZip(zipFile, dataDir, totalFiles);
            zipFile.delete();

            if (cancelled) return;

            updateNotification("Verifying files...", 0, totalFiles, true);
            broadcastProgress("Verifying files...", 0, totalFiles);

            int verified = 0;
            Iterator<String> keys = files.keys();
            while (keys.hasNext()) {
                if (cancelled) return;
                String path = keys.next();
                JSONObject info = files.getJSONObject(path);
                File f = new File(dataDir, path);
                if (!f.exists() || !sha256(f).equals(info.getString("sha256"))) {
                    showError("Verification failed: " + path);
                    return;
                }
                verified++;
                String msg = "Verifying: " + verified + "/" + totalFiles;
                updateNotification(msg, verified, totalFiles, false);
                broadcastProgress(msg, verified, totalFiles);
            }

            onDownloadComplete();

        } catch (Exception e) {
            showError("Download failed: " + e.getMessage());
        }
    }

    private void onDownloadComplete() {
        Intent launchIntent = new Intent(this, SAMP.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Download Complete")
                .setContentText("Tap to launch SA:MP Mobile")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOngoing(false);

        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.notify(NOTIFICATION_ID, builder.build());

        Intent intent = new Intent(BROADCAST_COMPLETE);
        sendBroadcast(intent);

        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void showError(String message) {
        Intent intent = new Intent(BROADCAST_ERROR);
        intent.putExtra(EXTRA_STATUS, message);
        sendBroadcast(intent);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Download Failed")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true)
                .setOngoing(false);

        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.notify(NOTIFICATION_ID, builder.build());

        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void updateNotification(String text, int progress, int total, boolean indeterminate) {
        Notification notification = buildNotification(text, progress, total, indeterminate);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void broadcastProgress(String status, int progress, int total) {
        Intent intent = new Intent(BROADCAST_PROGRESS);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_PROGRESS, progress);
        intent.putExtra(EXTRA_TOTAL, total);
        sendBroadcast(intent);
    }

    private Notification buildNotification(String text, int progress, int total, boolean indeterminate) {
        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading Game Data")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(total, progress, indeterminate)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent tapIntent = new Intent(this, DataDownloadActivity.class);
            tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent tapPi = PendingIntent.getActivity(this, 0, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(tapPi);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Game data download progress");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private int extractZip(File zipFile, File destDir, int totalFiles) throws Exception {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (cancelled) return count;
                String name = entry.getName();
                if (entry.isDirectory()) {
                    new File(destDir, name).mkdirs();
                    continue;
                }
                String relativePath = name;
                if (relativePath.startsWith("files/")) {
                    relativePath = relativePath.substring(6);
                }
                File outFile = new File(destDir, relativePath);
                outFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                }
                zis.closeEntry();
                count++;
                String msg = "Extracting: " + count + "/" + totalFiles;
                updateNotification(msg, count, totalFiles, false);
                broadcastProgress(msg, count, totalFiles);
            }
        }
        return count;
    }

    private String downloadUrlToString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        conn.disconnect();
        return out.toString("UTF-8");
    }

    private void downloadFile(String urlStr, File output) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);

        int totalSize = conn.getContentLength();

        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(output)) {
            byte[] buf = new byte[65536];
            int downloaded = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelled) { conn.disconnect(); return; }
                fos.write(buf, 0, n);
                downloaded += n;
                if (totalSize > 0) {
                    int pct = (int) ((long) downloaded * 100 / totalSize);
                    String msg = "Downloading: " + pct + "% (" + formatSize(downloaded) + "/" + formatSize(totalSize) + ")";
                    updateNotification(msg, downloaded, totalSize, false);
                    broadcastProgress(msg, downloaded, totalSize);
                }
            }
        }
        conn.disconnect();
    }

    private String formatSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024f);
        return String.format("%.1f MB", bytes / 1048576f);
    }

    private String sha256(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[65536];
            int n;
            while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
            fis.close();
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
