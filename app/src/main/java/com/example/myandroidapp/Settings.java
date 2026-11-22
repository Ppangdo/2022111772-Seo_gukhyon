package com.example.myandroidapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

// 화면 설정(글자 크기, 밝은·어두운 화면). SharedPreferences 에 저장해 앱을 다시 켜도 유지한다.
final class Settings {

    private static final String FILE = "cw_settings";
    private static final String KEY_TEXT = "text_size";
    private static final String KEY_THEME = "theme";

    // 글자 크기: 작게 / 보통 / 크게. 기기 글자 크기 설정에 곱한다(크게 = 디자인 기준 크기)
    static final String[] TEXT_NAMES = {"작게", "보통", "크게"};
    private static final float[] TEXT_SCALES = {0.8f, 0.9f, 1.0f};
    static final int TEXT_DEFAULT = 1;

    // 화면: 기기 설정 따름 / 밝게 / 어둡게
    static final String[] THEME_NAMES = {"기기 설정", "밝게", "어둡게"};
    private static final int[] THEME_MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES};

    private Settings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int textSize(Context c) {
        int v = prefs(c).getInt(KEY_TEXT, TEXT_DEFAULT);
        return v >= 0 && v < TEXT_SCALES.length ? v : TEXT_DEFAULT;
    }

    static void setTextSize(Context c, int v) {
        prefs(c).edit().putInt(KEY_TEXT, v).apply();
    }

    static int theme(Context c) {
        int v = prefs(c).getInt(KEY_THEME, 0);
        return v >= 0 && v < THEME_MODES.length ? v : 0;
    }

    static void setTheme(Context c, int v) {
        prefs(c).edit().putInt(KEY_THEME, v).apply();
        applyTheme(c);
    }

    // 바뀌면 AppCompat 이 화면(액티비티)을 다시 만든다
    static void applyTheme(Context c) {
        int mode = THEME_MODES[theme(c)];
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
    }

    // 액티비티의 기본 Context 를 글자 배율이 적용된 것으로 바꾼다(attachBaseContext 에서 사용)
    static Context wrap(Context base) {
        float k = TEXT_SCALES[textSize(base)];
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.fontScale = c.fontScale * k;
        return base.createConfigurationContext(c);
    }
}
