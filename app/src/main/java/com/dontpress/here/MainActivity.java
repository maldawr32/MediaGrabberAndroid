package com.dontpress.here;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashReporter.install(getApplicationContext());

        try {
            gameView = new GameView(this);
            setContentView(gameView);
            // Apply immersive mode only after the decor view is attached. Some OEM builds are
            // less tolerant when WindowInsetsController is used too early during onCreate().
            getWindow().getDecorView().post(this::applyImmersiveModeSafely);
        } catch (Throwable t) {
            CrashReporter.record(this, t, "MainActivity.onCreate");
            showStartupError(t);
        }
    }

    private void applyImmersiveModeSafely() {
        try {
            Window window = getWindow();
            window.setStatusBarColor(Color.rgb(11, 13, 18));
            window.setNavigationBarColor(Color.rgb(11, 13, 18));
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        } catch (Throwable t) {
            // Fullscreen is cosmetic. Never let it crash the game.
            CrashReporter.record(this, t, "immersive-mode");
        }
    }

    private void showStartupError(Throwable throwable) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(Color.rgb(11, 13, 18));

        TextView title = new TextView(this);
        title.setText("صار خطأ عند تشغيل اللعبة");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText("بدل ما يطفي التطبيق، تم حفظ الخطأ محليًا.\n\n" +
                throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage()) +
                "\n\nإذا ظهرت هذه الشاشة، أرسل صورة منها أو Logcat.");
        body.setTextColor(Color.LTGRAY);
        body.setTextSize(15);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, 32, 0, 0);
        root.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveModeSafely();
        if (gameView != null) gameView.onResumeGame();
    }

    @Override
    protected void onPause() {
        if (gameView != null) gameView.onPauseGame();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (gameView != null && gameView.handleBack()) return;
        super.onBackPressed();
    }
}
