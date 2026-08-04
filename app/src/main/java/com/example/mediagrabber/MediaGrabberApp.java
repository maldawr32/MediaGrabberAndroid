package com.example.mediagrabber;

import android.app.Application;
import android.util.Log;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;

public class MediaGrabberApp extends Application {
    private static final String TAG = "MediaGrabberApp";
    private static boolean engineReady;

    @Override
    public void onCreate() {
        super.onCreate();
        Thread initializer = new Thread(() -> {
            try {
                ensureEngineReady(this);
            } catch (YoutubeDLException error) {
                Log.e(TAG, "Engine initialization failed", error);
            }
        }, "media-engine-init");
        initializer.start();
    }

    public static synchronized void ensureEngineReady(Application application)
            throws YoutubeDLException {
        if (engineReady) {
            return;
        }
        YoutubeDL.getInstance().init(application);
        FFmpeg.getInstance().init(application);
        engineReady = true;
    }
}
