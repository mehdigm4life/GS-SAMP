package com.samp.mobile.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.samp.mobile.R;
import com.samp.mobile.game.SAMP;

public class DataDownloadActivity extends AppCompatActivity {
    private TextView tvStatus, tvCurrentFile, tvProgress;
    private ProgressBar progressBar;
    private Button btnRetry;
    private View contentView, errorView;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_data_download);

        tvStatus = findViewById(R.id.tv_download_status);
        tvCurrentFile = findViewById(R.id.tv_current_file);
        tvProgress = findViewById(R.id.tv_progress);
        progressBar = findViewById(R.id.progress_bar);
        btnRetry = findViewById(R.id.btn_retry);
        contentView = findViewById(R.id.download_content);
        errorView = findViewById(R.id.error_content);

        btnRetry.setOnClickListener(v -> startDownload());

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadService.BROADCAST_PROGRESS.equals(action)) {
                    String status = intent.getStringExtra(DownloadService.EXTRA_STATUS);
                    int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                    int total = intent.getIntExtra(DownloadService.EXTRA_TOTAL, 0);
                    updateUI(status, progress, total);
                } else if (DownloadService.BROADCAST_COMPLETE.equals(action)) {
                    onDownloadComplete();
                } else if (DownloadService.BROADCAST_ERROR.equals(action)) {
                    String msg = intent.getStringExtra(DownloadService.EXTRA_STATUS);
                    showError(msg);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.BROADCAST_PROGRESS);
        filter.addAction(DownloadService.BROADCAST_COMPLETE);
        filter.addAction(DownloadService.BROADCAST_ERROR);
        registerReceiver(receiver, filter);

        startDownload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        }
    }

    private void startDownload() {
        contentView.setVisibility(View.VISIBLE);
        errorView.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        tvStatus.setText("Starting download...");
        tvCurrentFile.setVisibility(View.GONE);
        tvProgress.setText("");

        Intent intent = new Intent(this, DownloadService.class);
        intent.setAction(DownloadService.ACTION_START);
        startForegroundService(intent);
    }

    private void updateUI(final String status, final int progress, final int total) {
        runOnUiThread(() -> {
            tvStatus.setText(status);
            if (total > 0) {
                progressBar.setIndeterminate(false);
                progressBar.setMax(total);
                progressBar.setProgress(progress);
                if (status.startsWith("Downloading") && total <= 100) {
                    tvProgress.setText(progress + "%");
                } else {
                    tvProgress.setText(progress + " / " + total);
                }
            } else {
                progressBar.setIndeterminate(true);
                tvProgress.setText("");
            }
        });
    }

    private void onDownloadComplete() {
        tvStatus.setText("All files ready!");
        tvProgress.setText("Completed successfully");
        progressBar.setIndeterminate(false);
        progressBar.setProgress(progressBar.getMax());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(DataDownloadActivity.this, SAMP.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1500);
    }

    private void showError(final String message) {
        runOnUiThread(() -> {
            contentView.setVisibility(View.GONE);
            errorView.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tv_error_message)).setText(message);
        });
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Download?")
                .setMessage("Game data is required to play. Are you sure you want to exit?")
                .setPositiveButton("Exit", (d, w) -> {
                    Intent intent = new Intent(this, DownloadService.class);
                    intent.setAction(DownloadService.ACTION_CANCEL);
                    startForegroundService(intent);
                    finishAffinity();
                    System.exit(0);
                })
                .setNegativeButton("Continue", null)
                .show();
    }
}
