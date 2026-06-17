package com.samp.mobile.launcher.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.samp.mobile.data.DataDownloadActivity;
import com.samp.mobile.data.DataVerifier;
import com.samp.mobile.game.SAMP;

public class LauncherActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DataVerifier.quickCheck(this)) {
            startSAMP();
        } else {
            showDataMissingDialog();
        }
    }

    private void startSAMP() {
        Intent intent = new Intent(this, SAMP.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDataMissingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = LayoutInflater.from(this).inflate(
                getResources().getIdentifier("dialog_data_missing", "layout", getPackageName()), null);
        builder.setView(view);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        view.findViewById(getResources().getIdentifier("btn_download", "id", getPackageName()))
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(this, DataDownloadActivity.class);
                    startActivity(intent);
                    finish();
                });

        view.findViewById(getResources().getIdentifier("btn_exit", "id", getPackageName()))
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    finishAndRemoveTask();
                    System.exit(0);
                });

        dialog.show();
    }
}
