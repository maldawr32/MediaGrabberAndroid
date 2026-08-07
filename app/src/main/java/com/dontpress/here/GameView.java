package com.dontpress.here;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class GameView extends View implements SensorEventListener {
    private enum Screen { HOME, LEVELS, CHAPTER, PLAYING, RESULT, FINISHED }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final GameState state;
    private final List<LevelSpec> levels;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private ToneGenerator tones;
    private boolean audioDisabled;
    private boolean renderFailed;

    private Screen screen = Screen.HOME;
    private int levelIndex = 0;
    private LevelSpec level;

    private float w, h, density;
    private float lastTouchX, lastTouchY;
    private final RectF redButton = new RectF();
    private final RectF targetZone = new RectF();
    private final RectF mascotRect = new RectF();
    private final RectF primaryHomeButton = new RectF();
    private final RectF secondaryHomeButton = new RectF();

    private long levelStart;
    private long actionDownAt;
    private long lastTapAt;
    private long pendingSuccessAt;
    private long greenAt;
    private long stableSince;
    private long multiStart;
    private long resultAt;
    private long flashAt;
    private int counter;
    private int mistakesThisLevel;
    private int shakeCount;
    private int memoryInputIndex;
    private int reverseExpected;
    private boolean pointerOnButton;
    private boolean dragging;
    private boolean fingerHeld;
    private boolean levelSucceeded;
    private boolean sensorAvailable;
    private boolean introShown;
    private String resultLine = "";
    private String transientLine = "";
    private long transientUntil;

    private float downX, downY;
    private float dragOffsetX, dragOffsetY;
    private float accelX, accelY, accelZ;
    private float lastAccelX, lastAccelY, lastAccelZ;
    private float gravityMagnitude = 9.8f;
    private long lastShakeAt;
    private float movingPhase;
    private int tinyXSeed;
    private int tinyYSeed;

    private int[] memorySequence = new int[0];
    private final RectF[] memoryPads = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] numberPads = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private final int[] numberValues = {2, 5, 1, 4, 3};

    private static final int BG = Color.rgb(11, 13, 18);
    private static final int PANEL = Color.rgb(24, 28, 38);
    private static final int PANEL_2 = Color.rgb(34, 39, 52);
    private static final int TEXT = Color.rgb(242, 244, 248);
    private static final int MUTED = Color.rgb(163, 171, 190);
    private static final int RED = Color.rgb(229, 57, 53);
    private static final int RED_DARK = Color.rgb(176, 36, 33);
    private static final int GREEN = Color.rgb(61, 186, 112);
    private static final int GOLD = Color.rgb(255, 198, 66);
    private static final int CYAN = Color.rgb(68, 203, 232);

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        setKeepScreenOn(true);
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setTextDirection(View.TEXT_DIRECTION_RTL);
        density = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        state = new GameState(context);
        levels = LevelCatalog.create();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorAvailable = accelerometer != null;
        // Audio is initialized lazily on the first sound request. Creating audio resources
        // during Activity startup caused compatibility problems on a few OEM devices.
        tones = null;
        audioDisabled = false;
        renderFailed = false;
        postInvalidateOnAnimation();
    }

    public void onResumeGame() {
        if (sensorAvailable && sensorManager != null && accelerometer != null) {
            try {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable t) {
                sensorAvailable = false;
                CrashReporter.record(getContext(), t, "sensor-register");
            }
        }
        if (!renderFailed) postInvalidateOnAnimation();
    }

    public void onPauseGame() {
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Throwable ignored) { }
        }
    }

    public boolean handleBack() {
        if (screen == Screen.PLAYING || screen == Screen.RESULT || screen == Screen.CHAPTER || screen == Screen.LEVELS || screen == Screen.FINISHED) {
            screen = Screen.HOME;
            invalidate();
            return true;
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Throwable ignored) { }
        }
        if (tones != null) {
            try { tones.release(); } catch (Throwable ignored) { }
            tones = null;
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width;
        h = height;
        setupGeometry();
    }

    private void setupGeometry() {
        float bw = Math.min(w * .64f, dp(310));
        float bh = Math.min(h * .13f, dp(105));
        redButton.set(w / 2f - bw / 2f, h * .59f - bh / 2f, w / 2f + bw / 2f, h * .59f + bh / 2f);
        float tw = Math.min(w * .55f, dp(260));
        targetZone.set(w / 2f - tw / 2f, h * .78f - dp(50), w / 2f + tw / 2f, h * .78f + dp(50));
        mascotRect.set(w / 2f - dp(52), h * .25f - dp(52), w / 2f + dp(52), h * .25f + dp(52));
        primaryHomeButton.set(w * .13f, h * .57f, w * .87f, h * .67f);
        secondaryHomeButton.set(w * .13f, h * .70f, w * .87f, h * .79f);

        float cx = w / 2f;
        float cy = h * .60f;
        float gap = dp(78);
        float r = dp(28);
        memoryPads[0].set(cx-gap-r, cy-gap-r, cx-gap+r, cy-gap+r);
        memoryPads[1].set(cx+gap-r, cy-gap-r, cx+gap+r, cy-gap+r);
        memoryPads[2].set(cx-gap-r, cy+gap-r, cx-gap+r, cy+gap+r);
        memoryPads[3].set(cx+gap-r, cy+gap-r, cx+gap+r, cy+gap+r);

        float nr = dp(27);
        float[] xs = {0.22f, 0.50f, 0.78f, 0.34f, 0.66f};
        float[] ys = {0.55f, 0.48f, 0.55f, 0.69f, 0.69f};
        for (int i = 0; i < 5; i++) {
            float x = w * xs[i], y = h * ys[i];
            numberPads[i].set(x-nr, y-nr, x+nr, y+nr);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (renderFailed) {
            drawEmergencyScreen(c);
            return;
        }
        try {
            long now = SystemClock.uptimeMillis();
            drawBackground(c, now);

            if (screen == Screen.HOME) drawHome(c, now);
            else if (screen == Screen.LEVELS) drawLevelSelect(c, now);
            else if (screen == Screen.CHAPTER) drawChapter(c, now);
            else if (screen == Screen.PLAYING) drawGame(c, now);
            else if (screen == Screen.RESULT) drawResult(c, now);
            else drawFinished(c, now);

            if (transientUntil > now && !transientLine.isEmpty()) drawToast(c, transientLine);
            postInvalidateOnAnimation();
        } catch (Throwable t) {
            renderFailed = true;
            CrashReporter.record(getContext(), t, "GameView.onDraw");
            drawEmergencyScreen(c);
        }
    }

    private void drawEmergencyScreen(Canvas c) {
        try {
            c.drawColor(BG);
            Paint emergency = new Paint(Paint.ANTI_ALIAS_FLAG);
            emergency.setColor(Color.WHITE);
            emergency.setTextAlign(Paint.Align.CENTER);
            emergency.setTypeface(Typeface.create("sans", Typeface.BOLD));
            emergency.setTextSize(sp(24));
            c.drawText("اللعبة اشتغلت بوضع الأمان", Math.max(1, w) / 2f, Math.max(dp(90), h * .42f), emergency);
            emergency.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            emergency.setTextSize(sp(14));
            emergency.setColor(MUTED);
            c.drawText("تم حفظ خطأ الرسم محليًا بدل إغلاق التطبيق.", Math.max(1, w) / 2f, Math.max(dp(130), h * .49f), emergency);
        } catch (Throwable ignored) {
            // Nothing else is safe to render.
        }
    }

    private void drawBackground(Canvas c, long now) {
        c.drawColor(BG);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 9; i++) {
            float phase = (now / 1000f) * (0.08f + i * .011f) + i * 1.9f;
            float x = (float) ((Math.sin(phase) * .42 + .5) * w);
            float y = (float) (((Math.cos(phase * .8f + i) * .42 + .5) * h));
            int alpha = 10 + (i % 3) * 4;
            p.setColor(Color.argb(alpha, 255, 255, 255));
            c.drawCircle(x, y, dp(35 + (i % 4) * 12), p);
        }
    }

    private void drawHome(Canvas c, long now) {
        drawTopStatus(c);
        drawMascot(c, mascotRect.centerX(), mascotRect.centerY(), dp(45), now, false);
        text(c, "ممنوع تكبس هون", w/2f, h*.39f, sp(31), TEXT, true);
        text(c, "لعبة ألغاز وخدع — أوفلاين بالكامل", w/2f, h*.435f, sp(15), MUTED, false);

        drawRoundedButton(c, primaryHomeButton, RED, state.getUnlockedLevel() > 1 ? "كمّل من المرحلة " + state.getUnlockedLevel() : "ابدأ... إذا بتجرؤ");
        drawRoundedButton(c, secondaryHomeButton, PANEL_2, "اختيار المراحل");

        String stats = "مكتمل " + state.getCompletedCount() + "/" + levels.size() + "   •   أسرار " + state.getSecretCount() + "/3";
        text(c, stats, w/2f, h*.845f, sp(14), MUTED, false);
        text(c, "بدون إنترنت • بدون حساب • التقدم محفوظ على الجهاز", w/2f, h*.90f, sp(12), Color.rgb(112,121,140), false);
    }

    private void drawTopStatus(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(130, 255,255,255));
        c.drawCircle(dp(28), dp(28), dp(4), p);
        c.drawCircle(dp(43), dp(28), dp(4), p);
        c.drawCircle(dp(58), dp(28), dp(4), p);
    }

    private void drawLevelSelect(Canvas c, long now) {
        text(c, "اختيار المراحل", w/2f, dp(62), sp(26), TEXT, true);
        text(c, "كل 8 مراحل = فصل جديد", w/2f, dp(92), sp(13), MUTED, false);

        int cols = 5;
        float margin = w * .08f;
        float gap = dp(10);
        float cell = (w - margin*2 - gap*(cols-1)) / cols;
        float top = dp(130);
        int unlocked = state.getUnlockedLevel();
        for (int i = 0; i < levels.size(); i++) {
            int row = i / cols, col = i % cols;
            float left = margin + col*(cell+gap);
            float t = top + row*(cell+gap);
            RectF r = new RectF(left, t, left+cell, t+cell);
            boolean open = (i+1) <= unlocked;
            p.setColor(open ? (i+1 == unlocked ? RED_DARK : PANEL_2) : Color.rgb(18,21,29));
            c.drawRoundRect(r, dp(13), dp(13), p);
            stroke.setColor(open ? Color.argb(70,255,255,255) : Color.argb(25,255,255,255));
            c.drawRoundRect(r, dp(13), dp(13), stroke);
            text(c, open ? String.valueOf(i+1) : "×", r.centerX(), r.centerY()+sp(6), sp(18), open ? TEXT : MUTED, true);
        }
        text(c, "اضغط زر الرجوع للعودة", w/2f, h-dp(30), sp(12), MUTED, false);
    }

    private void drawChapter(Canvas c, long now) {
        int chapter = levelIndex / 8 + 1;
        String[] names = {"الزر الكذّاب", "الموبايل داخل اللعبة", "اللعبة تتذكّرك", "دمج الحركات", "آخر الأعصاب"};
        String[] sub = {
                "كل ما تعرفه عن الأزرار رح نستخدمه ضدك.",
                "من هلق الجهاز نفسه صار جزءًا من الحل.",
                "كل أخطائك محفوظة محليًا... وأنا منتبه.",
                "الحركة الواحدة ما عادت تكفي.",
                "وصلت بعيد. لا تتوقع مني الرحمة."
        };
        text(c, "الفصل " + chapter, w/2f, h*.29f, sp(17), GOLD, true);
        text(c, names[Math.min(chapter-1, names.length-1)], w/2f, h*.39f, sp(34), TEXT, true);
        drawWrappedText(c, sub[Math.min(chapter-1, sub.length-1)], w/2f, h*.47f, w*.78f, sp(16), MUTED, dp(25));
        RectF go = new RectF(w*.18f, h*.64f, w*.82f, h*.74f);
        drawRoundedButton(c, go, RED, "يلا");
    }

    private void drawGame(Canvas c, long now) {
        if (level == null) return;
        updateTimedLogic(now);

        text(c, "مرحلة " + (levelIndex+1) + " / " + levels.size(), w/2f, dp(38), sp(12), MUTED, true);
        text(c, level.name, w/2f, dp(72), sp(23), TEXT, true);
        drawWrappedText(c, level.instruction, w/2f, dp(109), w*.82f, sp(16), TEXT, dp(24));

        drawMascot(c, mascotRect.centerX(), mascotRect.centerY(), dp(39), now, true);
        drawSpeech(c, adaptiveCharacterLine(), mascotRect.centerX(), mascotRect.bottom + dp(24));

        switch (level.type) {
            case MEMORY: drawMemory(c, now); break;
            case REVERSE_ORDER: drawReverseOrder(c); break;
            case FIND_TINY: drawTinyTarget(c, now); break;
            default: drawMainMechanic(c, now); break;
        }

        drawProgressHint(c, now);
    }

    private void drawMainMechanic(Canvas c, long now) {
        if (level.type == LevelSpec.Type.DRAG_TO_ZONE) {
            if (levelIndex == 29 && !dragging) {
                float tw = targetZone.width();
                float movingCenter = w/2f + (float)Math.sin(now/520f) * w*.20f;
                targetZone.offsetTo(movingCenter - tw/2f, h*.78f - dp(50));
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(35, 68, 203, 232));
            c.drawRoundRect(targetZone, dp(24), dp(24), p);
            stroke.setColor(CYAN);
            stroke.setStrokeWidth(dp(2));
            c.drawRoundRect(targetZone, dp(24), dp(24), stroke);
            text(c, "حط الزر هون", targetZone.centerX(), targetZone.centerY()+sp(5), sp(14), CYAN, true);
        }

        if (level.type == LevelSpec.Type.KEEP_STILL || level.type == LevelSpec.Type.DONT_TOUCH) {
            long elapsed = Math.max(0, now - stableSince);
            float progress = level.timeMs <= 0 ? 0 : Math.min(1f, elapsed / (float) level.timeMs);
            drawRingTimer(c, w/2f, h*.61f, dp(56), progress);
            text(c, String.format(Locale.US, "%.1f", Math.max(0, (level.timeMs-elapsed)/1000f)), w/2f, h*.61f+sp(6), sp(22), TEXT, true);
            if (level.type == LevelSpec.Type.DONT_TOUCH) return;
        }

        if (level.type == LevelSpec.Type.TAP_WHEN_GREEN) {
            boolean isGreen = now >= greenAt;
            drawRedButton(c, isGreen ? GREEN : RED, isGreen ? "الآن!" : "لا تكبس");
            return;
        }

        if (level.type == LevelSpec.Type.TILT) {
            float center = w/2f;
            float offset = clamp(accelX / 6f, -1f, 1f) * w*.25f;
            float bw = redButton.width();
            redButton.offsetTo(center + offset - bw/2f, redButton.top);
            drawRedButton(c, RED, level.value < 0 ? "ميّل لليسار" : "ميّل لليمين");
            return;
        }

        if (level.type == LevelSpec.Type.CHASE) {
            movingPhase += .012f;
            float bob = (float)Math.sin(movingPhase*2.2f) * dp(4);
            redButton.offset(0, bob);
            drawRedButton(c, RED, "امسكني");
            redButton.offset(0, -bob);
            return;
        }

        if (level.type == LevelSpec.Type.SHAKE || level.type == LevelSpec.Type.HOLD_AND_SHAKE) {
            drawRedButton(c, RED, level.type == LevelSpec.Type.HOLD_AND_SHAKE ? "امسك + هز" : "هزّني");
            float power = Math.min(1f, shakeCount / (float)Math.max(1, level.value));
            RectF bar = new RectF(w*.22f, h*.76f, w*.78f, h*.78f);
            p.setColor(PANEL_2); c.drawRoundRect(bar, dp(8), dp(8), p);
            RectF fill = new RectF(bar.left, bar.top, bar.left + bar.width()*power, bar.bottom);
            p.setColor(GOLD); c.drawRoundRect(fill, dp(8), dp(8), p);
            return;
        }

        if (level.type == LevelSpec.Type.MULTI_TOUCH) {
            drawRedButton(c, RED, "إصبع" + (level.value > 2 ? " × " + level.value : "ين"));
            return;
        }

        if (level.type == LevelSpec.Type.TAP_OUTSIDE) {
            drawRedButton(c, RED_DARK, "لا تكبسني");
            return;
        }

        if (level.type == LevelSpec.Type.SWIPE) {
            drawRedButton(c, RED, level.value < 0 ? "← اسحب" : "اسحب →");
            return;
        }

        if (level.type == LevelSpec.Type.LONG_PRESS) {
            drawRedButton(c, RED, fingerHeld ? "لا تترك..." : "اضغط مطوّل");
            return;
        }

        if (level.type == LevelSpec.Type.DOUBLE_TAP) {
            drawRedButton(c, RED, "مرتين بسرعة");
            return;
        }

        if (level.type == LevelSpec.Type.TAP_COUNT) {
            drawRedButton(c, RED, counter + " / " + level.value);
            return;
        }

        drawRedButton(c, RED, "ممنوع تكبس هون");
    }

    private void drawRedButton(Canvas c, int color, String label) {
        // Avoid switching View layer type from inside onDraw(). A simple painted shadow is
        // cheaper and more reliable across GPU/OEM combinations.
        p.setStyle(Paint.Style.FILL);
        RectF shadowRect = new RectF(redButton);
        shadowRect.offset(0, dp(7));
        p.setColor(Color.argb(70, 0, 0, 0));
        c.drawRoundRect(shadowRect, dp(28), dp(28), p);

        p.setColor(color);
        c.drawRoundRect(redButton, dp(28), dp(28), p);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.argb(80,255,255,255));
        c.drawRoundRect(redButton, dp(28), dp(28), stroke);
        text(c, label, redButton.centerX(), redButton.centerY()+sp(7), sp(20), Color.WHITE, true);
    }

    private void drawTinyTarget(Canvas c, long now) {
        float x = dp(35) + (tinyXSeed % Math.max(1, (int)(w-dp(70))));
        float y = h*.45f + (tinyYSeed % Math.max(1, (int)(h*.32f)));
        float pulse = dp(1.5f) * (float)Math.sin(now/260f);
        p.setColor(RED);
        c.drawCircle(x, y, dp(8)+pulse, p);
        p.setColor(Color.argb(50,229,57,53));
        c.drawCircle(x, y, dp(17)+pulse, p);
    }

    private void drawMemory(Canvas c, long now) {
        int showIndex = getMemoryShowIndex(now);
        boolean showing = now - levelStart < memorySequence.length * 700L + 650L;
        for (int i=0;i<4;i++) {
            boolean active = showing && showIndex >= 0 && memorySequence[showIndex] == i;
            p.setColor(active ? GOLD : PANEL_2);
            c.drawRoundRect(memoryPads[i], dp(18), dp(18), p);
            stroke.setColor(active ? Color.WHITE : Color.argb(60,255,255,255));
            c.drawRoundRect(memoryPads[i], dp(18), dp(18), stroke);
            text(c, String.valueOf(i+1), memoryPads[i].centerX(), memoryPads[i].centerY()+sp(6), sp(18), active ? BG : TEXT, true);
        }
        if (showing) text(c, "شوف بس...", w/2f, h*.79f, sp(14), GOLD, true);
        else text(c, "دورك: " + memoryInputIndex + "/" + memorySequence.length, w/2f, h*.79f, sp(14), TEXT, true);
    }

    private void drawReverseOrder(Canvas c) {
        for (int i=0;i<5;i++) {
            p.setColor(numberValues[i] >= reverseExpected ? RED_DARK : PANEL_2);
            c.drawOval(numberPads[i], p);
            text(c, String.valueOf(numberValues[i]), numberPads[i].centerX(), numberPads[i].centerY()+sp(6), sp(19), TEXT, true);
        }
        text(c, "التالي: " + reverseExpected, w/2f, h*.82f, sp(15), MUTED, true);
    }

    private void drawProgressHint(Canvas c, long now) {
        String hint = "";
        if (level.type == LevelSpec.Type.TAP_OUTSIDE) hint = counter + " / " + level.value;
        else if (level.type == LevelSpec.Type.CHASE) hint = "انمسك " + counter + " / " + level.value;
        else if (level.type == LevelSpec.Type.SHAKE || level.type == LevelSpec.Type.HOLD_AND_SHAKE) hint = "هزات " + shakeCount + " / " + level.value;
        else if (mistakesThisLevel > 0) hint = "محاولات فاشلة: " + mistakesThisLevel;
        if (!hint.isEmpty()) text(c, hint, w/2f, h*.90f, sp(13), MUTED, false);
        if (!sensorAvailable && usesSensor(level.type)) text(c, "الحساس غير متوفر: تم تفعيل بديل باللمس/الوقت", w/2f, h*.94f, sp(11), GOLD, false);
    }

    private void drawResult(Canvas c, long now) {
        boolean ok = levelSucceeded;
        drawMascot(c, w/2f, h*.27f, dp(52), now, false);
        text(c, ok ? "نجحت" : "مسكتك!", w/2f, h*.42f, sp(36), ok ? GREEN : RED, true);
        drawWrappedText(c, resultLine, w/2f, h*.49f, w*.78f, sp(17), TEXT, dp(26));

        RectF next = new RectF(w*.15f, h*.66f, w*.85f, h*.76f);
        drawRoundedButton(c, next, ok ? GREEN : RED, ok ? (levelIndex+1 >= levels.size() ? "شوف النهاية" : "المرحلة التالية") : "جرّب مرة ثانية");
        text(c, "أخطاءك الكلية: " + state.getTotalMistakes(), w/2f, h*.84f, sp(12), MUTED, false);
    }

    private void drawFinished(Canvas c, long now) {
        drawMascot(c, w/2f, h*.24f, dp(56), now, false);
        text(c, "خلصت اللعبة؟", w/2f, h*.39f, sp(34), GOLD, true);
        text(c, "لا. بس خلصت النسخة الأولى.", w/2f, h*.45f, sp(20), TEXT, true);
        drawWrappedText(c, "اللعبة حفظت إنك وصلت للنهاية، وعدد غلطاتك، وقديش كنت مستعجل. فيك ترجع للمراحل وتكتشف الأسرار الثلاثة.", w/2f, h*.52f, w*.80f, sp(16), MUTED, dp(25));
        RectF r = new RectF(w*.16f, h*.70f, w*.84f, h*.80f);
        drawRoundedButton(c, r, PANEL_2, "ارجع للمراحل");
        text(c, "الأسرار: " + state.getSecretCount() + "/3", w/2f, h*.87f, sp(14), GOLD, true);
    }

    private void drawMascot(Canvas c, float x, float y, float r, long now, boolean gameFace) {
        float bob = (float)Math.sin(now/500f) * dp(2.5f);
        y += bob;
        p.setColor(Color.rgb(246, 247, 250));
        c.drawCircle(x, y, r, p);
        p.setColor(PANEL_2);
        float eyeShiftX = clamp((lastTouchX-x)/w, -.22f, .22f) * r;
        float eyeShiftY = clamp((lastTouchY-y)/h, -.16f, .16f) * r;
        c.drawCircle(x-r*.35f+eyeShiftX, y-r*.13f+eyeShiftY, r*.11f, p);
        c.drawCircle(x+r*.35f+eyeShiftX, y-r*.13f+eyeShiftY, r*.11f, p);
        stroke.setStrokeWidth(Math.max(dp(2), r*.055f));
        stroke.setColor(PANEL_2);
        RectF mouth = new RectF(x-r*.30f, y+r*.06f, x+r*.30f, y+r*.34f);
        if (gameFace && mistakesThisLevel > 0) c.drawArc(mouth, 200, 140, false, stroke);
        else c.drawArc(mouth, 20, 140, false, stroke);
        mascotRect.set(x-r, y-r, x+r, y+r);
    }

    private void drawSpeech(Canvas c, String line, float cx, float top) {
        RectF bubble = new RectF(w*.11f, top, w*.89f, top+dp(63));
        p.setColor(PANEL);
        c.drawRoundRect(bubble, dp(18), dp(18), p);
        drawWrappedText(c, line, cx, top+dp(17), bubble.width()-dp(28), sp(13), MUTED, dp(19));
    }

    private void drawRingTimer(Canvas c, float cx, float cy, float r, float progress) {
        stroke.setStrokeWidth(dp(8));
        stroke.setColor(PANEL_2);
        c.drawCircle(cx, cy, r, stroke);
        stroke.setColor(progress > .75f ? GREEN : GOLD);
        RectF rr = new RectF(cx-r, cy-r, cx+r, cy+r);
        c.drawArc(rr, -90, 360*progress, false, stroke);
        stroke.setStrokeWidth(dp(2));
    }

    private void drawRoundedButton(Canvas c, RectF r, int color, String label) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(r, dp(22), dp(22), p);
        stroke.setColor(Color.argb(60,255,255,255));
        c.drawRoundRect(r, dp(22), dp(22), stroke);
        text(c, label, r.centerX(), r.centerY()+sp(6), sp(17), TEXT, true);
    }

    private void drawToast(Canvas c, String s) {
        RectF r = new RectF(w*.12f, h*.91f-dp(28), w*.88f, h*.91f+dp(18));
        p.setColor(Color.argb(235, 38, 43, 57));
        c.drawRoundRect(r, dp(16), dp(16), p);
        text(c, s, r.centerX(), r.centerY()+sp(5), sp(12), TEXT, true);
    }

    private void updateTimedLogic(long now) {
        if (level == null || screen != Screen.PLAYING) return;

        if (pendingSuccessAt > 0 && now >= pendingSuccessAt) {
            pendingSuccessAt = 0;
            succeed("تمام. التزمت بالعدد... هالمرة.");
            return;
        }

        if (level.type == LevelSpec.Type.DONT_TOUCH) {
            if (stableSince == 0) stableSince = levelStart;
            if (now - stableSince >= level.timeMs) {
                state.addPatienceWin();
                succeed(state.getPatienceWins() >= 2 ? "إنت مو مستعجل متل أول مرة. لاحظت." : "أخيرًا واحد بيعرف ما يعمل شي.");
            }
        }

        if (level.type == LevelSpec.Type.KEEP_STILL) {
            if (!sensorAvailable) {
                if (stableSince == 0) stableSince = levelStart;
            }
            if (stableSince > 0 && now - stableSince >= level.timeMs) {
                state.addPatienceWin();
                succeed("ثابت. بشكل مريب بصراحة.");
            }
        }

        if (level.type == LevelSpec.Type.TILT && sensorAvailable) {
            if ((level.value < 0 && accelX > 5.1f) || (level.value > 0 && accelX < -5.1f)) {
                succeed("إيه! مرات الحل هو تحريك العالم، مو الزر.");
            }
        }

        if (level.type == LevelSpec.Type.MULTI_TOUCH && multiStart > 0) {
            long required = Math.max(250, level.timeMs);
            if (now - multiStart >= required) {
                succeed("تمام. واضح إن عندك أصابع زيادة للحالات الطارئة.");
            }
        }

        if (level.type == LevelSpec.Type.MEMORY) {
            // no-op; phase is derived from time.
        }
    }

    private String adaptiveCharacterLine() {
        if (level == null) return "";
        if (mistakesThisLevel >= 2) return "عم تجرّب عشوائي؟ أنا عم عدّ.";
        int totalMistakes = state.getTotalMistakes();
        int forbidden = state.getForbiddenPresses();
        if (levelIndex > 14 && forbidden > 10) return "بعرفك: كلمة «ممنوع» عندك معناها «جرّب فورًا».";
        if (levelIndex > 22 && totalMistakes <= 5) return "قليل أخطاء... صرت ما بحب هدوءك.";
        if (levelIndex > 22 && totalMistakes > 14) return "نفسك الطويل حلو، بس سجل الأخطاء عندي أطول.";
        return level.characterLine;
    }

    private boolean usesSensor(LevelSpec.Type type) {
        return type == LevelSpec.Type.SHAKE || type == LevelSpec.Type.TILT || type == LevelSpec.Type.KEEP_STILL || type == LevelSpec.Type.HOLD_AND_SHAKE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        long now = SystemClock.uptimeMillis();

        if (screen == Screen.HOME) return handleHomeTouch(e, now);
        if (screen == Screen.LEVELS) return handleLevelsTouch(e, now);
        if (screen == Screen.CHAPTER) return handleChapterTouch(e, now);
        if (screen == Screen.RESULT) return handleResultTouch(e, now);
        if (screen == Screen.FINISHED) return handleFinishedTouch(e, now);
        if (screen != Screen.PLAYING || level == null) return true;

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            actionDownAt = now;
            pointerOnButton = redButton.contains(downX, downY);
            fingerHeld = pointerOnButton;
            if (pointerOnButton) {
                dragOffsetX = downX - redButton.left;
                dragOffsetY = downY - redButton.top;
            }

            if (level.type == LevelSpec.Type.DONT_TOUCH) {
                fail("قلتلك لا تلمس شي. حرفيًا ولا شي.");
                return true;
            }

            if (level.type == LevelSpec.Type.KEEP_STILL) {
                stableSince = now;
                showTransient("لا تلمس كمان. بس رح أعطيك فرصة.");
            }

            if (level.type == LevelSpec.Type.DRAG_TO_ZONE && pointerOnButton) dragging = true;
            if (level.type == LevelSpec.Type.MULTI_TOUCH) multiStart = 0;
        }

        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (level.type == LevelSpec.Type.DRAG_TO_ZONE && dragging && e.getPointerCount() == 1) {
                float left = clamp(e.getX() - dragOffsetX, dp(8), w - redButton.width() - dp(8));
                float top = clamp(e.getY() - dragOffsetY, h*.36f, h - redButton.height() - dp(18));
                redButton.offsetTo(left, top);
            }
            if (level.type == LevelSpec.Type.MULTI_TOUCH) {
                int needed = level.value;
                if (e.getPointerCount() >= needed && multiTouchPositionValid(e, needed)) {
                    if (multiStart == 0) multiStart = now;
                    long required = Math.max(250, level.timeMs);
                    if (now - multiStart >= required) succeed("تمام. واضح إن عندك أصابع زيادة للحالات الطارئة.");
                } else multiStart = 0;
            }
        }

        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float upX = e.getX(), upY = e.getY();
            long held = now - actionDownAt;
            fingerHeld = false;

            if (level.type == LevelSpec.Type.DRAG_TO_ZONE) {
                dragging = false;
                if (RectF.intersects(redButton, targetZone) && targetZone.contains(redButton.centerX(), redButton.centerY())) {
                    succeed("سحبتني بدل ما تكبسني. تطور ممتاز.");
                } else if (pointerOnButton) {
                    resetButtonPosition();
                    failSoft("مو هون.");
                }
                return true;
            }

            if (level.type == LevelSpec.Type.SWIPE) {
                float dx = upX - downX;
                float dy = upY - downY;
                boolean horizontal = Math.abs(dx) > dp(120) && Math.abs(dx) > Math.abs(dy)*1.8f;
                boolean direction = level.value < 0 ? dx < 0 : dx > 0;
                if (horizontal && direction) succeed("سحبة نظيفة."); else failSoft("الاتجاه غلط أو السحبة قصيرة.");
                return true;
            }

            if (level.type == LevelSpec.Type.LONG_PRESS) {
                if (pointerOnButton && redButton.contains(upX, upY) && held >= level.timeMs) succeed("ما فلّتت. منيح.");
                else if (pointerOnButton) failSoft("تركت بكير.");
                return true;
            }

            if (level.type == LevelSpec.Type.TAP_RED && pointerOnButton && redButton.contains(upX, upY)) {
                state.addForbiddenPress();
                succeed("طبعًا كبست. كنت متأكد.");
                return true;
            }

            if (level.type == LevelSpec.Type.TAP_COUNT && pointerOnButton && redButton.contains(upX, upY)) {
                counter++;
                state.addForbiddenPress();
                clickFeedback();
                if (counter == level.value) pendingSuccessAt = now + 520;
                else if (counter > level.value) fail("قلت بالضبط. الزيادة محسوبة عليك.");
                return true;
            }

            if (level.type == LevelSpec.Type.TAP_OUTSIDE) {
                if (!redButton.contains(upX, upY)) {
                    counter++;
                    clickFeedback();
                    if (counter >= level.value) succeed("أخيرًا اكتشفت إن الشاشة أكبر من زر.");
                } else {
                    state.addForbiddenPress();
                    fail("هذا بالضبط المكان اللي قلتلك ما تكبس عليه.");
                }
                return true;
            }

            if (level.type == LevelSpec.Type.DOUBLE_TAP && pointerOnButton && redButton.contains(upX, upY)) {
                if (lastTapAt > 0 && now-lastTapAt <= 430) {
                    state.addForbiddenPress(); state.addForbiddenPress();
                    succeed("دبل كبسة محسوبة.");
                    lastTapAt = 0;
                } else {
                    lastTapAt = now;
                    clickFeedback();
                }
                return true;
            }

            if (level.type == LevelSpec.Type.CHASE && pointerOnButton && redButton.contains(upX, upY)) {
                counter++;
                clickFeedback();
                if (counter >= level.value) succeed("مسكتني. ما كنت متوقع كل هالإصرار.");
                else moveButtonRandomly();
                return true;
            }

            if (level.type == LevelSpec.Type.TAP_WHEN_GREEN && pointerOnButton && redButton.contains(upX, upY)) {
                if (now >= greenAt) succeed("توقيت ممتاز."); else fail("استعجلت. الأحمر كان فخ واضح.");
                return true;
            }

            if (level.type == LevelSpec.Type.FIND_TINY) {
                float tx = dp(35) + (tinyXSeed % Math.max(1, (int)(w-dp(70))));
                float ty = h*.45f + (tinyYSeed % Math.max(1, (int)(h*.32f)));
                if (distance(upX, upY, tx, ty) <= dp(26)) succeed("لقيته! ما كان لازم يكون كبير حتى يكون مهم.");
                else failSoft("مو هون.");
                return true;
            }

            if (level.type == LevelSpec.Type.MEMORY) {
                if (isMemoryInputPhase(now)) handleMemoryTap(upX, upY);
                else failSoft("قلتلك شوف بس.");
                return true;
            }

            if (level.type == LevelSpec.Type.REVERSE_ORDER) {
                handleReverseTap(upX, upY);
                return true;
            }

            if (!sensorAvailable && (level.type == LevelSpec.Type.SHAKE || level.type == LevelSpec.Type.TILT || level.type == LevelSpec.Type.HOLD_AND_SHAKE)) {
                // Accessibility fallback for sensorless emulator/device.
                counter++;
                if (counter >= Math.max(1, level.value)) succeed("الحساس مو موجود، فقبلت البديل.");
                return true;
            }
        }

        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP && level.type == LevelSpec.Type.MULTI_TOUCH) {
            if (e.getPointerCount() - 1 < level.value) multiStart = 0;
        }

        if (e.getActionMasked() == MotionEvent.ACTION_UP && level.type == LevelSpec.Type.MULTI_TOUCH) {
            multiStart = 0;
        }

        if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            fingerHeld = false;
            multiStart = 0;
        }
        return true;
    }

    private boolean handleHomeTouch(MotionEvent e, long now) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        float x=e.getX(), y=e.getY();
        if (mascotRect.contains(x,y)) {
            state.addMascotTap();
            int t = state.getMascotTaps();
            if (t == 5) { state.unlockSecret(1); showTransient("سر 1/3: الشخصية ما بتحب الهدوء."); secretTone(); }
            else if (t == 15) { state.unlockSecret(2); showTransient("سر 2/3: 15 كبسة على راسي؟ جد؟"); secretTone(); }
            else if (t == 30) { state.unlockSecret(3); showTransient("سر 3/3: أنت رسميًا المشكلة هون."); secretTone(); }
            else showTransient("آخ! " + t);
            return true;
        }
        if (primaryHomeButton.contains(x,y)) {
            int unlocked = Math.min(state.getUnlockedLevel(), levels.size());
            startLevel(unlocked-1, true);
        } else if (secondaryHomeButton.contains(x,y)) {
            screen = Screen.LEVELS;
        }
        return true;
    }

    private boolean handleLevelsTouch(MotionEvent e, long now) {
        if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
        int cols=5;
        float margin=w*.08f, gap=dp(10), cell=(w-margin*2-gap*(cols-1))/cols, top=dp(130);
        for (int i=0;i<levels.size();i++) {
            int row=i/cols, col=i%cols;
            RectF r=new RectF(margin+col*(cell+gap), top+row*(cell+gap), margin+col*(cell+gap)+cell, top+row*(cell+gap)+cell);
            if (r.contains(e.getX(),e.getY()) && (i+1)<=state.getUnlockedLevel()) {
                startLevel(i, true);
                return true;
            }
        }
        return true;
    }

    private boolean handleChapterTouch(MotionEvent e, long now) {
        if (e.getActionMasked()!=MotionEvent.ACTION_UP) return true;
        RectF go = new RectF(w*.18f, h*.64f, w*.82f, h*.74f);
        if (go.contains(e.getX(),e.getY())) beginLevel();
        return true;
    }

    private boolean handleResultTouch(MotionEvent e, long now) {
        if (e.getActionMasked()!=MotionEvent.ACTION_UP) return true;
        if (mascotRect.contains(e.getX(),e.getY())) {
            state.addMascotTap();
            showTransient("لا تستغل شاشة الفوز لتكبسني.");
            return true;
        }
        RectF next = new RectF(w*.15f, h*.66f, w*.85f, h*.76f);
        if (!next.contains(e.getX(),e.getY())) return true;
        if (!levelSucceeded) {
            startLevel(levelIndex, false);
        } else if (levelIndex+1 >= levels.size()) {
            screen = Screen.FINISHED;
        } else {
            startLevel(levelIndex+1, true);
        }
        return true;
    }

    private boolean handleFinishedTouch(MotionEvent e, long now) {
        if (e.getActionMasked()!=MotionEvent.ACTION_UP) return true;
        RectF r = new RectF(w*.16f, h*.70f, w*.84f, h*.80f);
        if (r.contains(e.getX(),e.getY())) screen = Screen.LEVELS;
        return true;
    }

    private void startLevel(int index, boolean allowChapter) {
        levelIndex = Math.max(0, Math.min(index, levels.size()-1));
        level = levels.get(levelIndex);
        if (allowChapter && levelIndex % 8 == 0) {
            screen = Screen.CHAPTER;
        } else {
            beginLevel();
        }
    }

    private void beginLevel() {
        level = levels.get(levelIndex);
        screen = Screen.PLAYING;
        levelStart = SystemClock.uptimeMillis();
        stableSince = (level.type == LevelSpec.Type.DONT_TOUCH || level.type == LevelSpec.Type.KEEP_STILL) ? levelStart : 0;
        actionDownAt = 0;
        lastTapAt = 0;
        pendingSuccessAt = 0;
        counter = 0;
        mistakesThisLevel = 0;
        shakeCount = 0;
        memoryInputIndex = 0;
        reverseExpected = 5;
        dragging = false;
        fingerHeld = false;
        levelSucceeded = false;
        resultLine = "";
        tinyXSeed = 71 + random.nextInt(10000);
        tinyYSeed = 113 + random.nextInt(10000);
        greenAt = levelStart + 1800 + random.nextInt(2200);
        prepareMemory();
        resetButtonPosition();
        if (level.type == LevelSpec.Type.CHASE) moveButtonRandomly();
        introShown = true;
        clickFeedbackSoft();
    }

    private void prepareMemory() {
        if (level.type != LevelSpec.Type.MEMORY) {
            memorySequence = new int[0];
            return;
        }
        memorySequence = new int[Math.max(1, level.value)];
        for (int i=0;i<memorySequence.length;i++) {
            int next = random.nextInt(4);
            if (i>0 && next==memorySequence[i-1]) next=(next+1+random.nextInt(3))%4;
            memorySequence[i]=next;
        }
    }

    private int getMemoryShowIndex(long now) {
        long elapsed = now - levelStart;
        int index = (int)(elapsed / 700L);
        if (index < 0 || index >= memorySequence.length) return -1;
        long within = elapsed % 700L;
        return within < 420 ? index : -1;
    }

    private boolean isMemoryInputPhase(long now) {
        return now - levelStart >= memorySequence.length * 700L + 650L;
    }

    private void handleMemoryTap(float x, float y) {
        int pad=-1;
        for (int i=0;i<4;i++) if (memoryPads[i].contains(x,y)) { pad=i; break; }
        if (pad<0) { failSoft("الأرقام الأربعة بس."); return; }
        if (pad == memorySequence[memoryInputIndex]) {
            memoryInputIndex++;
            clickFeedback();
            if (memoryInputIndex >= memorySequence.length) succeed("ذاكرتك مزعجة... ممتازة يعني.");
        } else {
            fail("لا. التسلسل كان غير هيك.");
        }
    }

    private void handleReverseTap(float x, float y) {
        for (int i=0;i<5;i++) {
            if (numberPads[i].contains(x,y)) {
                if (numberValues[i] == reverseExpected) {
                    reverseExpected--;
                    clickFeedback();
                    if (reverseExpected <= 0) succeed("من الكبير للصغير. أخيرًا بتسمع التعليمات.");
                } else fail("العكس يعني 5، 4، 3، 2، 1.");
                return;
            }
        }
        failSoft("اختار رقم.");
    }

    private boolean multiTouchPositionValid(MotionEvent e, int needed) {
        if (needed >= 4) return true;
        for (int i=0;i<Math.min(e.getPointerCount(), needed);i++) {
            if (!redButton.contains(e.getX(i), e.getY(i))) return false;
        }
        return true;
    }

    private void moveButtonRandomly() {
        float bw = Math.min(w*.48f, dp(240));
        float bh = dp(82);
        float left = dp(18) + random.nextFloat() * Math.max(dp(1), w-bw-dp(36));
        float topMin = h*.42f;
        float topMax = h*.78f;
        float top = topMin + random.nextFloat()*Math.max(dp(1), topMax-topMin-bh);
        redButton.set(left, top, left+bw, top+bh);
    }

    private void resetButtonPosition() {
        float bw = Math.min(w * .64f, dp(310));
        float bh = Math.min(h * .13f, dp(105));
        redButton.set(w / 2f - bw / 2f, h * .59f - bh / 2f, w / 2f + bw / 2f, h * .59f + bh / 2f);
        float tw = Math.min(w * .55f, dp(260));
        targetZone.set(w / 2f - tw / 2f, h * .78f - dp(50), w / 2f + tw / 2f, h * .78f + dp(50));
    }

    private void succeed(String line) {
        if (screen != Screen.PLAYING) return;
        levelSucceeded = true;
        resultLine = line;
        resultAt = SystemClock.uptimeMillis();
        screen = Screen.RESULT;
        int currentUnlocked = state.getUnlockedLevel();
        if (levelIndex+1 >= currentUnlocked && currentUnlocked < levels.size()) {
            state.setUnlockedLevel(levelIndex+2);
        }
        if (state.getCompletedCount() < levelIndex+1) state.setCompletedCount(levelIndex+1);
        successFeedback();
    }

    private void fail(String line) {
        if (screen != Screen.PLAYING) return;
        mistakesThisLevel++;
        state.addMistake();
        levelSucceeded = false;
        resultLine = line + personalizedFailureTail();
        resultAt = SystemClock.uptimeMillis();
        screen = Screen.RESULT;
        failFeedback();
    }

    private void failSoft(String line) {
        mistakesThisLevel++;
        state.addMistake();
        showTransient(line);
        failFeedbackSoft();
        if (mistakesThisLevel >= 4) {
            fail("أربع محاولات عشوائية كفاية.");
        }
    }

    private String personalizedFailureTail() {
        int m = state.getTotalMistakes();
        if (m == 1) return " أول غلطة. رح أتذكرها.";
        if (m > 20) return " بالمناسبة صار عندك " + m + " غلطة محفوظة محليًا.";
        if (state.getForbiddenPresses() > 8) return " واضح إن الأحمر عندك مغناطيس.";
        return "";
    }

    private void showTransient(String s) {
        transientLine = s;
        transientUntil = SystemClock.uptimeMillis() + 1500;
    }

    private void clickFeedback() {
        safeHaptic(HapticFeedbackConstants.KEYBOARD_TAP);
        safeTone(ToneGenerator.TONE_PROP_BEEP, 55);
    }

    private void clickFeedbackSoft() {
        safeHaptic(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void successFeedback() {
        safeHaptic(HapticFeedbackConstants.CONFIRM);
        safeTone(ToneGenerator.TONE_PROP_ACK, 130);
    }

    private void failFeedback() {
        safeHaptic(HapticFeedbackConstants.REJECT);
        safeTone(ToneGenerator.TONE_PROP_NACK, 140);
    }

    private void failFeedbackSoft() {
        safeHaptic(HapticFeedbackConstants.LONG_PRESS);
        safeTone(ToneGenerator.TONE_PROP_NACK, 55);
    }

    private void secretTone() {
        safeHaptic(HapticFeedbackConstants.CONFIRM);
        safeTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 180);
    }

    private void safeHaptic(int effect) {
        try { performHapticFeedback(effect); } catch (Throwable ignored) { }
    }

    private ToneGenerator ensureTones() {
        if (audioDisabled) return null;
        if (tones != null) return tones;
        try {
            tones = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);
            return tones;
        } catch (Throwable t) {
            audioDisabled = true;
            CrashReporter.record(getContext(), t, "tone-init");
            return null;
        }
    }

    private void safeTone(int toneType, int durationMs) {
        ToneGenerator generator = ensureTones();
        if (generator == null) return;
        try { generator.startTone(toneType, durationMs); }
        catch (Throwable t) {
            audioDisabled = true;
            try { generator.release(); } catch (Throwable ignored) { }
            tones = null;
            CrashReporter.record(getContext(), t, "tone-play");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null || event.values.length < 3) return;
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        accelX = event.values[0];
        accelY = event.values[1];
        accelZ = event.values[2];
        float mag = (float)Math.sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ);
        gravityMagnitude = gravityMagnitude*.88f + mag*.12f;
        long now = SystemClock.uptimeMillis();

        float delta = Math.abs(accelX-lastAccelX) + Math.abs(accelY-lastAccelY) + Math.abs(accelZ-lastAccelZ);
        lastAccelX = accelX; lastAccelY = accelY; lastAccelZ = accelZ;

        if (screen == Screen.PLAYING && level != null) {
            if (level.type == LevelSpec.Type.KEEP_STILL) {
                if (delta > 2.35f) {
                    stableSince = now;
                    if (now - flashAt > 600) {
                        flashAt = now;
                        showTransient("تحرك! العداد رجع.");
                    }
                } else if (stableSince == 0) stableSince = now;
            }

            boolean shake = (Math.abs(mag - 9.8f) > 5.3f || delta > 13.5f) && now-lastShakeAt > 300;
            if (shake) {
                lastShakeAt = now;
                if (level.type == LevelSpec.Type.SHAKE || (level.type == LevelSpec.Type.HOLD_AND_SHAKE && fingerHeld)) {
                    shakeCount++;
                    clickFeedbackSoft();
                    if (shakeCount >= level.value) succeed(level.type == LevelSpec.Type.HOLD_AND_SHAKE ? "مسكت وهزّيت بنفس الوقت. ممتاز." : "وصلت الهزات.");
                } else if (level.type == LevelSpec.Type.HOLD_AND_SHAKE) {
                    showTransient("لازم تضل ضاغط على الزر.");
                }
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(bold ? Typeface.create("sans", Typeface.BOLD) : Typeface.create("sans", Typeface.NORMAL));
        c.drawText(s, x, y, p);
    }

    private void drawWrappedText(Canvas c, String s, float cx, float top, float maxWidth, float size, int color, float lineHeight) {
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(color);
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        float y = top;
        for (String word : words) {
            String test = line.length()==0 ? word : line + " " + word;
            if (p.measureText(test) > maxWidth && line.length()>0) {
                c.drawText(line.toString(), cx, y, p);
                y += lineHeight;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length()>0) c.drawText(line.toString(), cx, y, p);
    }

    private float dp(float v) { return v*density; }
    private float sp(float v) { return v*getResources().getDisplayMetrics().scaledDensity; }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    private static float distance(float x1,float y1,float x2,float y2) { float dx=x1-x2,dy=y1-y2; return (float)Math.sqrt(dx*dx+dy*dy); }
}
