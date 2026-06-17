package com.mehdigm.compiler.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppLogger {
    private static final String LOG_FILE_NAME = "logcat.log";
    private static final long MAX_LOG_SIZE = 2L * 1024 * 1024;
    private static final String TAG = "GSCompiler";
    private static final String FOLDER_NAME = "GS SAMP";

    private static File logFile;
    private static ExecutorService executor;
    private static BlockingQueue<LogEntry> queue = new LinkedBlockingQueue<>();
    private static AtomicBoolean enabled = new AtomicBoolean(false);
    private static AtomicBoolean started = new AtomicBoolean(false);
    private static Thread.UncaughtExceptionHandler defaultExceptionHandler;

    private static class LogEntry {
        char level;
        String tag;
        String msg;
        long time;

        LogEntry(char level, String tag, String msg, long time) {
            this.level = level;
            this.tag = tag;
            this.msg = msg;
            this.time = time;
        }
    }

    public static void start(Context context) {
        if (started.getAndSet(true)) return;
        enabled.set(true);

        logFile = getLogFile(context);

        if (defaultExceptionHandler == null) {
            installCrashHandler();
        }

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AppLogger-Writer");
            t.setDaemon(true);
            return t;
        });

        executor.submit(() -> {
            LogEntry lastEntry = null;
            int repeatCount = 0;
            try {
                while (true) {
                    LogEntry entry = queue.take();
                    if (lastEntry != null
                            && entry.level == lastEntry.level
                            && entry.tag.equals(lastEntry.tag)
                            && entry.msg.equals(lastEntry.msg)) {
                        repeatCount++;
                        continue;
                    }
                    if (repeatCount > 0 && lastEntry != null) {
                        String summary = lastEntry.msg + " (repeated " + (repeatCount + 1) + " times)";
                        writeEntry(new LogEntry(lastEntry.level, lastEntry.tag, summary, lastEntry.time));
                    }
                    writeEntry(entry);
                    lastEntry = entry;
                    repeatCount = 0;
                }
            } catch (InterruptedException e) {
                if (repeatCount > 0 && lastEntry != null) {
                    String summary = lastEntry.msg + " (repeated " + (repeatCount + 1) + " times)";
                    writeEntry(new LogEntry(lastEntry.level, lastEntry.tag, summary, lastEntry.time));
                }
                Thread.currentThread().interrupt();
            }
        });

        executor.submit(() -> {
            logSystemInfo(context);
            i(TAG, "AppLogger started");
        });
    }

    public static void stop() {
        if (!started.get()) return;
        enabled.set(false);
        started.set(false);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void installCrashHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String stackTrace = stackTraceToString(throwable);
            String msg = "UNCAUGHT EXCEPTION on " + thread.getName() + " (" + thread.getId() + ")\n" + stackTrace;
            Log.e("CRASH", msg);
            writeEntryImmediate(new LogEntry('C', "CRASH", msg, System.currentTimeMillis()));
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private static void logSystemInfo(Context context) {
        i(TAG, "=== System Info ===");
        i(TAG, "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        i(TAG, "Board: " + Build.BOARD + ", Hardware: " + Build.HARDWARE);
        i(TAG, "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        i(TAG, "ABIs: " + String.join(", ", Build.SUPPORTED_ABIS));
        i(TAG, "Memory class: " + getMemoryClass(context) + "MB");
        try {
            android.content.pm.PackageInfo pkg = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long vc;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vc = pkg.getLongVersionCode();
            } else {
                vc = pkg.versionCode;
            }
            i(TAG, "App version: " + (pkg.versionName != null ? pkg.versionName : "unknown") + " (" + vc + ")");
        } catch (Exception ignored) {}
        Runtime rt = Runtime.getRuntime();
        i(TAG, "JVM max memory: " + (rt.maxMemory() / 1024 / 1024) + "MB");
        i(TAG, "JVM total memory: " + (rt.totalMemory() / 1024 / 1024) + "MB");
        i(TAG, "JVM free memory: " + (rt.freeMemory() / 1024 / 1024) + "MB");
        i(TAG, "=== End System Info ===");
    }

    private static int getMemoryClass(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return am.getMemoryClass();
        } catch (Exception e) {
            return 0;
        }
    }

    private static File getLogFile(Context context) {
        try {
            File base = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
            if (base.exists() || base.mkdirs()) {
                return new File(base, LOG_FILE_NAME);
            }
        } catch (Exception ignored) {}
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    private static void writeEntry(LogEntry entry) {
        if (logFile == null) return;
        String time = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(entry.time));
        String line = time + " " + entry.level + "/" + entry.tag + ": " + entry.msg + "\n";
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line);
            fw.flush();
            trimIfNeeded(logFile);
        } catch (Exception ignored) {}
    }

    private static void writeEntryImmediate(LogEntry entry) {
        if (logFile == null) return;
        String time = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(entry.time));
        String line = time + " " + entry.level + "/" + entry.tag + ": " + entry.msg + "\n";
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line);
            fw.flush();
        } catch (Exception ignored) {}
    }

    private static void trimIfNeeded(File file) {
        if (!file.exists() || file.length() < MAX_LOG_SIZE) return;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String content = sb.toString();
            int halfSize = (int) (MAX_LOG_SIZE / 2);
            String trimmed = content.substring(Math.max(0, content.length() - halfSize));
            try (FileWriter fw = new FileWriter(file, false)) {
                fw.write(trimmed);
                fw.flush();
            }
        } catch (Exception ignored) {}
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        if (enabled.get()) queue.offer(new LogEntry('D', tag, msg, System.currentTimeMillis()));
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        if (enabled.get()) queue.offer(new LogEntry('I', tag, msg, System.currentTimeMillis()));
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        if (enabled.get()) queue.offer(new LogEntry('W', tag, msg, System.currentTimeMillis()));
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        if (enabled.get()) queue.offer(new LogEntry('E', tag, msg, System.currentTimeMillis()));
    }

    public static File getLogPath(Context context) {
        return getLogFile(context);
    }

    public static String getLogContent(Context context) {
        try {
            File f = getLogFile(context);
            if (f.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString();
            }
            return "No logs available";
        } catch (Exception e) {
            return "No logs available";
        }
    }

    public static void clearLogs(Context context) {
        try {
            File f = getLogFile(context);
            if (f.exists()) {
                try (FileWriter fw = new FileWriter(f, false)) {
                    fw.write("");
                    fw.flush();
                }
            }
        } catch (Exception ignored) {}
    }
}
