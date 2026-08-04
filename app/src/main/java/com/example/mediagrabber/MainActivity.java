package com.example.mediagrabber;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.yausername.youtubedl_android.YoutubeDL;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private EditText urlInput;
    private EditText startInput;
    private EditText endInput;
    private Spinner formatSpinner;
    private Spinner qualitySpinner;
    private LinearLayout qualityGroup;
    private CheckBox rightsCheck;
    private Button downloadButton;
    private Button cancelButton;
    private Button updateButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // The foreground service still runs if the user declines; the UI remains the progress surface.
            });

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra(DownloadService.EXTRA_STATE);
            String message = intent.getStringExtra(DownloadService.EXTRA_MESSAGE);
            int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);

            progressBar.setProgress(Math.max(0, Math.min(progress, 100)));
            if (!TextUtils.isEmpty(message)) {
                statusText.setText(message);
            }

            if (DownloadService.STATE_RUNNING.equals(state)) {
                setDownloading(true);
            } else if (DownloadService.STATE_SUCCESS.equals(state)
                    || DownloadService.STATE_ERROR.equals(state)
                    || DownloadService.STATE_CANCELED.equals(state)) {
                setDownloading(false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        configureSpinners();
        configureActions();
        consumeSharedUrl(getIntent());
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeSharedUrl(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(DownloadService.ACTION_EVENT);
        ContextCompat.registerReceiver(
                this,
                progressReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        unregisterReceiver(progressReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        urlInput = findViewById(R.id.urlInput);
        startInput = findViewById(R.id.startInput);
        endInput = findViewById(R.id.endInput);
        formatSpinner = findViewById(R.id.formatSpinner);
        qualitySpinner = findViewById(R.id.qualitySpinner);
        qualityGroup = findViewById(R.id.qualityGroup);
        rightsCheck = findViewById(R.id.rightsCheck);
        downloadButton = findViewById(R.id.downloadButton);
        cancelButton = findViewById(R.id.cancelButton);
        updateButton = findViewById(R.id.updateButton);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
    }

    private void configureSpinners() {
        String[] formats = {"MP4 فيديو", "MP3 صوت", "WAV صوت", "GIF متحرك"};
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                formats
        );
        formatSpinner.setAdapter(formatAdapter);

        String[] qualities = {"أفضل جودة", "1080p", "720p", "480p"};
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                qualities
        );
        qualitySpinner.setAdapter(qualityAdapter);

        formatSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            qualityGroup.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        }));
    }

    private void configureActions() {
        findViewById(R.id.pasteButton).setOnClickListener(view -> pasteFromClipboard());
        downloadButton.setOnClickListener(view -> startDownload());
        cancelButton.setOnClickListener(view -> cancelDownload());
        updateButton.setOnClickListener(view -> updateEngine());
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            toast("الحافظة فارغة");
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            urlInput.setText(text);
        }
    }

    private void startDownload() {
        String url = urlInput.getText().toString().trim();
        String start = startInput.getText().toString().trim();
        String end = endInput.getText().toString().trim();
        String format = formatCode(formatSpinner.getSelectedItemPosition());
        String quality = qualityCode(qualitySpinner.getSelectedItemPosition());

        if (!isHttpUrl(url)) {
            urlInput.setError("أدخل رابطًا يبدأ بـ http أو https");
            return;
        }
        if (!rightsCheck.isChecked()) {
            toast("يجب تأكيد امتلاكك حق تنزيل المحتوى");
            return;
        }
        try {
            long startSeconds = TimeParser.parseOptional(start);
            long endSeconds = TimeParser.parseOptional(end);
            if (startSeconds >= 0 && endSeconds >= 0 && endSeconds <= startSeconds) {
                throw new IllegalArgumentException("وقت النهاية يجب أن يكون بعد البداية");
            }
            if ("gif".equals(format)) {
                if (startSeconds < 0 || endSeconds < 0) {
                    throw new IllegalArgumentException("صيغة GIF تحتاج وقت بداية ونهاية");
                }
                if (endSeconds - startSeconds > 60) {
                    throw new IllegalArgumentException("مدة GIF القصوى 60 ثانية");
                }
            }
        } catch (IllegalArgumentException error) {
            toast(error.getMessage());
            return;
        }

        Intent serviceIntent = new Intent(this, DownloadService.class)
                .setAction(DownloadService.ACTION_DOWNLOAD)
                .putExtra(DownloadService.EXTRA_URL, url)
                .putExtra(DownloadService.EXTRA_FORMAT, format)
                .putExtra(DownloadService.EXTRA_QUALITY, quality)
                .putExtra(DownloadService.EXTRA_START, start)
                .putExtra(DownloadService.EXTRA_END, end);

        ContextCompat.startForegroundService(this, serviceIntent);
        progressBar.setProgress(0);
        statusText.setText("جاري بدء التنزيل…");
        setDownloading(true);
    }

    private void cancelDownload() {
        Intent cancelIntent = new Intent(this, DownloadService.class)
                .setAction(DownloadService.ACTION_CANCEL);
        startService(cancelIntent);
        statusText.setText("جاري إلغاء التنزيل…");
    }

    private void updateEngine() {
        updateButton.setEnabled(false);
        statusText.setText("جاري تحديث محرك المواقع…");
        executor.submit(() -> {
            try {
                MediaGrabberApp.ensureEngineReady(getApplication());
                YoutubeDL.UpdateStatus result = YoutubeDL.getInstance().updateYoutubeDL(
                        this,
                        YoutubeDL.UpdateChannel._STABLE
                );
                runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    if (result == YoutubeDL.UpdateStatus.DONE) {
                        statusText.setText("تم تحديث المحرك بنجاح");
                    } else {
                        statusText.setText("المحرك محدث بالفعل");
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    statusText.setText("تعذر تحديث المحرك: " + cleanError(error));
                });
            }
        });
    }

    private void setDownloading(boolean downloading) {
        downloadButton.setEnabled(!downloading);
        cancelButton.setEnabled(downloading);
        updateButton.setEnabled(!downloading);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void consumeSharedUrl(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (shared != null) {
            urlInput.setText(extractFirstUrl(shared.toString()));
        }
    }

    private static String extractFirstUrl(String text) {
        for (String part : text.split("\\s+")) {
            if (isHttpUrl(part)) {
                return part;
            }
        }
        return text.trim();
    }

    private static boolean isHttpUrl(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static String formatCode(int position) {
        return switch (position) {
            case 1 -> "mp3";
            case 2 -> "wav";
            case 3 -> "gif";
            default -> "mp4";
        };
    }

    private static String qualityCode(int position) {
        return switch (position) {
            case 1 -> "1080";
            case 2 -> "720";
            case 3 -> "480";
            default -> "best";
        };
    }

    private static String cleanError(Throwable error) {
        String message = error.getMessage();
        if (TextUtils.isEmpty(message)) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 180 ? message.substring(0, 180) + "…" : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
