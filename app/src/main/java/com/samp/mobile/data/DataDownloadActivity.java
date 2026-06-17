package com.samp.mobile.data;

import android.app.ProgressDialog;
import android.content.Intent;

import com.samp.mobile.R;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

public class DataDownloadActivity extends AppCompatActivity {
    private static final String TAG = "DataDL";
    private TextView tvStatus, tvCurrentFile, tvProgress;
    private ProgressBar progressBar;
    private Button btnRetry;
    private View contentView, errorView;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(getLayoutResId());

        handler = new Handler(Looper.getMainLooper());

        tvStatus = findViewById(R.id.tv_download_status);
        tvCurrentFile = findViewById(R.id.tv_current_file);
        tvProgress = findViewById(R.id.tv_progress);
        progressBar = findViewById(R.id.progress_bar);
        btnRetry = findViewById(R.id.btn_retry);
        contentView = findViewById(R.id.download_content);
        errorView = findViewById(R.id.error_content);

        btnRetry.setOnClickListener(v -> startDownload());
        startDownload();
    }

    private int getLayoutResId() {
        return getResources().getIdentifier("activity_data_download", "layout", getPackageName());
    }

    private void setStatus(final String text) {
        handler.post(() -> tvStatus.setText(text));
    }

    private void setCurrentFile(final String text) {
        handler.post(() -> {
            tvCurrentFile.setText(text);
            tvCurrentFile.setVisibility(View.VISIBLE);
        });
    }

    private void setProgress(final int current, final int total) {
        handler.post(() -> {
            progressBar.setMax(total);
            progressBar.setProgress(current);
            tvProgress.setText(current + " / " + total + " files");
        });
    }

    private void showError(final String message) {
        handler.post(() -> {
            contentView.setVisibility(View.GONE);
            errorView.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tv_error_message)).setText(message);
        });
    }

    private void startDownload() {
        contentView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        tvStatus.setText("Connecting...");
        tvCurrentFile.setVisibility(View.GONE);
        tvProgress.setText("");

        new Thread(this::doDownload).start();
    }

    private void doDownload() {
        try {
            setStatus("Downloading manifest...");
            String jsonStr = downloadUrlToString(DataConstants.MANIFEST_URL);
            JSONObject manifest = new JSONObject(jsonStr);
            JSONObject files = manifest.getJSONObject("files");
            int totalFiles = files.length();

            File dataDir = getExternalFilesDir(null);
            if (dataDir == null) {
                showError("Storage not available");
                return;
            }

            setStatus("Downloading game data...");
            File zipFile = new File(getCacheDir(), "gs_data_download.zip");
            downloadFile(DataConstants.DATA_ZIP_URL, zipFile);

            setStatus("Extracting files...");
            progressBar.setIndeterminate(false);
            progressBar.setMax(totalFiles);

            int extracted = extractZip(zipFile, dataDir, files);

            zipFile.delete();

            setStatus("Verifying files...");
            progressBar.setIndeterminate(true);
            tvCurrentFile.setVisibility(View.GONE);

            int verified = 0;
            Iterator<String> keys = files.keys();
            while (keys.hasNext()) {
                String path = keys.next();
                JSONObject info = files.getJSONObject(path);
                File f = new File(dataDir, path);
                if (!f.exists() || !DataVerifier.sha256(f).equals(info.getString("sha256"))) {
                    final String failedFile = path;
                    handler.post(() -> showError("Verification failed: " + failedFile));
                    return;
                }
                verified++;
                final int v = verified;
                handler.post(() -> tvProgress.setText("Verifying " + v + " / " + totalFiles));
            }

            handler.post(() -> {
                tvStatus.setText("All files ready!");
                tvProgress.setText("Completed successfully");
                progressBar.setIndeterminate(false);
                progressBar.setProgress(progressBar.getMax());

                Toast.makeText(DataDownloadActivity.this, "Data installed successfully", Toast.LENGTH_LONG).show();

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(DataDownloadActivity.this, com.samp.mobile.game.SAMP.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }, 1500);
            });

        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            showError("Download failed: " + e.getMessage());
        }
    }

    private int extractZip(File zipFile, File destDir, JSONObject manifest) throws Exception {
        JSONObject files = manifest.getJSONObject("files");
        int count = 0;
        int total = files.length();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    new File(destDir, name).mkdirs();
                    continue;
                }

                String relativePath = name;
                if (relativePath.startsWith("files/")) {
                    relativePath = relativePath.substring(6);
                }

                final String displayName = relativePath;
                setCurrentFile(displayName);

                File outFile = new File(destDir, relativePath);
                outFile.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                }
                zis.closeEntry();

                count++;
                setProgress(count, total);
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
        progressBar.setIndeterminate(false);

        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(output)) {
            byte[] buf = new byte[65536];
            int downloaded = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                fos.write(buf, 0, n);
                downloaded += n;
                if (totalSize > 0) {
                    final int d = downloaded;
                    final int t = totalSize;
                    handler.post(() -> {
                        progressBar.setMax(t);
                        progressBar.setProgress(d);
                        tvProgress.setText(formatSize(d) + " / " + formatSize(t));
                    });
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

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Download?")
                .setMessage("Game data is required to play. Are you sure you want to exit?")
                .setPositiveButton("Exit", (d, w) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .setNegativeButton("Continue", null)
                .show();
    }
}
