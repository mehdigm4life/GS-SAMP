package com.wardrumstudios.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppLogger {
    private static final String TAG = "AppLogger";
    private static final String FOLDER_NAME = "GS SAMP";
    private static final String LOG_FILE_NAME = "logcat.txt";
    private static Process process;

    public static void start() {
        if (process != null) return;

        File dir = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        final File logFile = new File(dir, LOG_FILE_NAME);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "logcat",
                    "-v", "time",
                    "-f", logFile.getAbsolutePath(),
                    "-r", "10240",
                    "-n", "5"
            );
            pb.redirectErrorStream(true);
            process = pb.start();
            Log.i(TAG, "Logcat started: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to start logcat", e);
        }
    }

    public static void stop() {
        if (process != null) {
            process.destroy();
            process = null;
            Log.i(TAG, "Logcat stopped");
        }
    }

    public static void write(String message) {
        writeToFile(message);
    }

    public static void write(String tag, String message) {
        writeToFile("[" + tag + "] " + message);
    }

    private static void writeToFile(String message) {
        File dir = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File logFile = new File(dir, LOG_FILE_NAME);
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());

        try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
            String line = "[" + timeStamp + "] " + message + "\n";
            fos.write(line.getBytes());
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}
