package com.mehdigm.gssamp;

import android.app.Application;

import com.mehdigm.gssamp.logger.AppLogger;

public class GSSAMPApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.start(this);
    }
}
