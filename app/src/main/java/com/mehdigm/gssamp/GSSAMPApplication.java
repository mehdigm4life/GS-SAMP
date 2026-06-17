package com.mehdigm.gssamp;

import android.app.Application;
import android.util.Log;

import com.mehdigm.gssamp.logger.AppLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class GSSAMPApplication extends Application {
    @Override
    public void onCreate() {
        createSettingsIni();
        super.onCreate();
        AppLogger.start(this);
    }

    private void createSettingsIni() {
        try {
            File dir = getExternalFilesDir("SAMP");
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();

            File ini = new File(dir, "settings.ini");
            if (ini.exists()) return;

            String content = ""
                + "[client]\n"
                + "name=Player\n"
                + "password=\n"
                + "servernumber=1\n"
                + "\n"
                + "[debug]\n"
                + "debug=false\n"
                + "online=true\n"
                + "\n"
                + "[gui]\n"
                + "Font=arial.ttf\n"
                + "FontSize=30.0\n"
                + "FontOutline=2\n"
                + "ChatPosX=325.0\n"
                + "ChatPosY=25.0\n"
                + "ChatSizeX=1150.0\n"
                + "ChatSizeY=220.0\n"
                + "ChatMaxMessages=6\n"
                + "PassengerUseTexture=true\n"
                + "PassengerTextureSize=30.0\n"
                + "PassengerTexturePosX=120.0\n"
                + "PassengerTexturePosY=430.0\n"
                + "Dialog=true\n"
                + "VoiceChatEnable=true\n"
                + "VoiceChatKey=66\n"
                + "VoiceChatSize=30.0\n"
                + "VoiceChatPosX=1520.0\n"
                + "VoiceChatPosY=480.0\n"
                + "androidkeyboard=false\n";

            try (FileOutputStream fos = new FileOutputStream(ini)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e("GSSAMP", "Failed to create settings.ini", e);
        }
    }
}
