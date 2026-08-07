package com.dontpress.here;

import android.content.Context;
import android.content.SharedPreferences;

public final class GameState {
    private static final String PREFS = "dont_press_here_state";
    private final SharedPreferences prefs;

    public GameState(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getUnlockedLevel() { return prefs.getInt("unlocked_level", 1); }
    public void setUnlockedLevel(int level) { prefs.edit().putInt("unlocked_level", Math.max(1, level)).apply(); }

    public int getCompletedCount() { return prefs.getInt("completed_count", 0); }
    public void setCompletedCount(int value) { prefs.edit().putInt("completed_count", value).apply(); }

    public int getTotalMistakes() { return prefs.getInt("mistakes", 0); }
    public void addMistake() { prefs.edit().putInt("mistakes", getTotalMistakes() + 1).apply(); }

    public int getForbiddenPresses() { return prefs.getInt("forbidden_presses", 0); }
    public void addForbiddenPress() { prefs.edit().putInt("forbidden_presses", getForbiddenPresses() + 1).apply(); }

    public int getPatienceWins() { return prefs.getInt("patience_wins", 0); }
    public void addPatienceWin() { prefs.edit().putInt("patience_wins", getPatienceWins() + 1).apply(); }

    public int getSecretCount() { return prefs.getInt("secrets", 0); }
    public void unlockSecret(int secretId) {
        String key = "secret_" + secretId;
        if (!prefs.getBoolean(key, false)) {
            prefs.edit()
                    .putBoolean(key, true)
                    .putInt("secrets", getSecretCount() + 1)
                    .apply();
        }
    }

    public boolean hasSecret(int secretId) { return prefs.getBoolean("secret_" + secretId, false); }

    public int getMascotTaps() { return prefs.getInt("mascot_taps", 0); }
    public void addMascotTap() { prefs.edit().putInt("mascot_taps", getMascotTaps() + 1).apply(); }

    public void reset() { prefs.edit().clear().apply(); }
}
