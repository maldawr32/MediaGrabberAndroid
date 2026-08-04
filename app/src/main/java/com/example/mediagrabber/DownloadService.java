package com.example.mediagrabber;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.jvm.functions.Function3;

public class DownloadService extends Service {
    public static final String ACTION_DOWNLOAD = BuildConfig.APPLICATION_ID + ".action.DOWNLOAD";
    public static final String ACTION_CANCEL = BuildConfig.APPLICATION_ID + ".action.CANCEL";
    public static final String ACTION_EVENT = BuildConfig.APPLICATION_ID + ".event.DOWNLOAD";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_END = "end";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_PROGRESS = "progress";

    public static final String STATE_RUNNING = "running";
    public static final String STATE_SUCCESS = "success";
    public static final String STATE_ERROR = "error";
    public static final String STATE_CANCELED = "canceled";

    private static final String CHANNEL_ID = "media_downloads";
    private static final int NOTIFICATION_ID = 2207;
    private static final String PROCESS_ID = "media-grabber-download";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long lastNotificationUpdate;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID);
            sendEvent(STATE_CANCELED, 0, "تم إلغاء التنزيل");
            return START_NOT_STICKY;
        }
        if (ACTION_DOWNLOAD.equals(action) && running.compareAndSet(false, true)) {
            startForeground(NOTIFICATION_ID, buildProgressNotification(0, "جاري التجهيز…", true));
            executor.submit(() -> executeDownload(intent));
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    private void executeDownload(Intent intent) {
        File jobDir = null;
        try {
            acquireWakeLock();
            sendEvent(STATE_RUNNING, 0, "جاري تجهيز محرك التنزيل…");
            MediaGrabberApp.ensureEngineReady(getApplication());

            String url = requireExtra(intent, EXTRA_URL);
            String format = defaultString(intent.getStringExtra(EXTRA_FORMAT), "mp4");
            String quality = defaultString(intent.getStringExtra(EXTRA_QUALITY), "best");
            String start = defaultString(intent.getStringExtra(EXTRA_START), "");
            String end = defaultString(intent.getStringExtra(EXTRA_END), "");

            jobDir = createJobDirectory();
            YoutubeDLRequest request = buildRequest(url, format, quality, start, end, jobDir);

            Function3<Float, Long, String, Unit> callback = (progress, eta, line) -> {
                int value = Math.max(0, Math.min(100, Math.round(progress)));
                String message = readableProgress(value, eta, line);
                sendEvent(STATE_RUNNING, value, message);
                maybeUpdateNotification(value, message);
                return Unit.INSTANCE;
            };

            YoutubeDL.getInstance().execute(request, PROCESS_ID, callback);
            File result = findResultFile(jobDir, format);
            if (result == null) {
                throw new IOException("اكتمل الأمر لكن لم يتم العثور على الملف الناتج");
            }

            Uri exported = exportToDownloads(result);
            String successMessage = "تم الحفظ في Downloads/MediaGrabber/" + result.getName();
            sendEvent(STATE_SUCCESS, 100, successMessage);
            showFinishedNotification(exported, mimeType(result), successMessage);
        } catch (YoutubeDL.CanceledException canceled) {
            sendEvent(STATE_CANCELED, 0, "تم إلغاء التنزيل");
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception error) {
            String message = "فشل التنزيل: " + cleanError(error);
            sendEvent(STATE_ERROR, 0, message);
            showErrorNotification(message);
        } finally {
            if (jobDir != null) {
                deleteRecursively(jobDir);
            }
            running.set(false);
            releaseWakeLock();
            stopSelf();
        }
    }

    private YoutubeDLRequest buildRequest(
            String url,
            String format,
            String quality,
            String start,
            String end,
            File jobDir
    ) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--newline");
        request.addOption("--no-playlist");
        request.addOption("--no-mtime");
        request.addOption("--trim-filenames", 150);
        request.addOption("--output", new File(jobDir, "%(title).150B [%(id)s].%(ext)s").getAbsolutePath());

        if ("mp3".equals(format)) {
            request.addOption("--extract-audio");
            request.addOption("--audio-format", "mp3");
            request.addOption("--audio-quality", "0");
        } else if ("wav".equals(format)) {
            request.addOption("--extract-audio");
            request.addOption("--audio-format", "wav");
        } else if ("gif".equals(format)) {
            request.addOption("--recode-video", "gif");
            request.addOption(
                    "--postprocessor-args",
                    "ffmpeg:-vf fps=12,scale=720:-2:force_original_aspect_ratio=decrease:flags=lanczos"
            );
        } else {
            request.addOption("--format", mp4FormatSelector(quality));
            request.addOption("--merge-output-format", "mp4");
            request.addOption("--remux-video", "mp4");
        }

        String normalizedStart = TimeParser.normalize(start);
        String normalizedEnd = TimeParser.normalize(end);
        if (!normalizedStart.isEmpty() || !normalizedEnd.isEmpty()) {
            String sectionStart = normalizedStart.isEmpty() ? "00:00:00" : normalizedStart;
            String sectionEnd = normalizedEnd.isEmpty() ? "inf" : normalizedEnd;
            request.addOption("--download-sections", "*" + sectionStart + "-" + sectionEnd);
            request.addOption("--force-keyframes-at-cuts");
        }
        return request;
    }

    private static String mp4FormatSelector(String quality) {
        String height = "best".equals(quality) ? "" : "[height<=" + quality + "]";
        return "bestvideo*[ext=mp4]" + height
                + "+bestaudio[ext=m4a]/best[ext=mp4]" + height
                + "/best" + height;
    }

    private File createJobDirectory() throws IOException {
        File externalRoot = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File base = externalRoot != null ? externalRoot : getFilesDir();
        File root = new File(base, "jobs");
        File job = new File(root, UUID.randomUUID().toString());
        if (!job.mkdirs()) {
            throw new IOException("تعذر إنشاء مجلد العمل");
        }
        return job;
    }

    private File findResultFile(File directory, String requestedFormat) {
        File[] files = directory.listFiles(file -> file.isFile() && isCandidate(file, requestedFormat));
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0];
    }

    private static boolean isCandidate(File file, String requestedFormat) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".json")
                || name.endsWith(".webp") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".description")) {
            return false;
        }
        if ("mp3".equals(requestedFormat)) {
            return name.endsWith(".mp3");
        }
        if ("wav".equals(requestedFormat)) {
            return name.endsWith(".wav");
        }
        if ("gif".equals(requestedFormat)) {
            return name.endsWith(".gif");
        }
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm");
    }

    private Uri exportToDownloads(File source) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType(source));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MediaGrabber");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri target = resolver.insert(collection, values);
        if (target == null) {
            throw new IOException("تعذر إنشاء الملف في مجلد التنزيلات");
        }

        boolean completed = false;
        try (InputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(target, "w")) {
            if (output == null) {
                throw new IOException("تعذر فتح ملف الوجهة");
            }
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            completed = true;
        } finally {
            if (!completed) {
                resolver.delete(target, null, null);
            }
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(target, done, null, null);
        return target;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification buildProgressNotification(int progress, String message, boolean indeterminate) {
        Intent cancelIntent = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPending = PendingIntent.getService(
                this,
                41,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.notification_downloading))
                .setContentText(message)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
                .addAction(0, getString(R.string.cancel), cancelPending)
                .build();
    }

    private void maybeUpdateNotification(int progress, String message) {
        long now = System.currentTimeMillis();
        if (now - lastNotificationUpdate < 800 && progress < 100) {
            return;
        }
        lastNotificationUpdate = now;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildProgressNotification(progress, message, false));
    }

    private void showFinishedNotification(Uri uri, String mime, String message) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                42,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.notification_complete))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(openPending)
                .build();

        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID + 1, notification);
    }

    private void showErrorNotification(String message) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(R.string.notification_failed))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build();
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID + 2, notification);
    }

    private void sendEvent(String state, int progress, String message) {
        Intent event = new Intent(ACTION_EVENT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STATE, state)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(event);
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediaGrabber:download");
        wakeLock.acquire(6 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    private static String readableProgress(int progress, long eta, String line) {
        if (!TextUtils.isEmpty(line) && line.length() < 160) {
            return line.trim();
        }
        if (eta > 0) {
            return "التقدم " + progress + "% — المتبقي نحو " + eta + " ثانية";
        }
        return "التقدم " + progress + "%";
    }

    private static String mimeType(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        return "video/mp4";
    }

    private static String requireExtra(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("قيمة مطلوبة مفقودة: " + key);
        }
        return value;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String cleanError(Throwable error) {
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 220 ? message.substring(0, 220) + "…" : message;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
