package com.dontpress.here;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Local-only crash recorder. No network, analytics, or server is used.
 * The report is stored in private SharedPreferences so startup failures are diagnosable.
 */
public final class CrashReporter {
    private static final String TAG = "DontPressHere";
    private static final String PREFS = "dont_press_here_crash";
    private static volatile boolean installed;

    private CrashReporter() { }

    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        final Context appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            record(appContext, throwable, "uncaught:" + thread.getName());
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    public static void record(Context context, Throwable throwable, String phase) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            pw.flush();
            String stack = sw.toString();
            if (stack.length() > 12000) stack = stack.substring(0, 12000);
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("phase", phase == null ? "unknown" : phase)
                    .putString("stack", stack)
                    .putLong("time", System.currentTimeMillis())
                    .apply();
            Log.e(TAG, "Crash in " + phase, throwable);
        } catch (Throwable ignored) {
            // Crash reporting must never cause a second crash.
        }
    }

    public static String getLastCrash(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String phase = prefs.getString("phase", "");
            String stack = prefs.getString("stack", "");
            if (stack == null || stack.isEmpty()) return "";
            return "Phase: " + phase + "\n\n" + stack;
        } catch (Throwable ignored) {
            return "";
        }
    }
}
