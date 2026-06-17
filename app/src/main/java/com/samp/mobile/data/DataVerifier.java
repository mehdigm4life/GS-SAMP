package com.samp.mobile.data;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DataVerifier {
    private static final String TAG = "DataVerifier";

    public interface Callback {
        void onVerificationResult(boolean allOk, List<String> missingFiles);
    }

    public static void fetchManifestAndVerify(Context context, Callback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(DataConstants.MANIFEST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);

                InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                in.close();
                conn.disconnect();

                String jsonStr = out.toString("UTF-8");
                JSONObject manifest = new JSONObject(jsonStr);
                JSONObject files = manifest.getJSONObject("files");

                File dataDir = context.getExternalFilesDir(null);
                List<String> missing = new ArrayList<>();

                Iterator<String> keys = files.keys();
                while (keys.hasNext()) {
                    String path = keys.next();
                    JSONObject fileInfo = files.getJSONObject(path);
                    String expectedHash = fileInfo.getString("sha256");
                    long expectedSize = fileInfo.getLong("size");

                    File localFile = new File(dataDir, path);
                    if (!localFile.exists() || localFile.length() != expectedSize) {
                        missing.add(path);
                        continue;
                    }

                    String actualHash = sha256(localFile);
                    if (!expectedHash.equals(actualHash)) {
                        missing.add(path);
                    }
                }

                callback.onVerificationResult(missing.isEmpty(), missing);
            } catch (Exception e) {
                Log.e(TAG, "Verification failed", e);
                callback.onVerificationResult(false, new ArrayList<>());
            }
        }).start();
    }

    public static boolean quickCheck(Context context) {
        File dataDir = context.getExternalFilesDir(null);
        if (dataDir == null || !dataDir.exists()) return false;
        File texdb = new File(dataDir, "texdb");
        File text = new File(dataDir, "Text");
        return texdb.exists() && texdb.isDirectory() && text.exists() && text.isDirectory();
    }

    public static String sha256(File file) {
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
