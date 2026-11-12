package com.example.myandroidapp;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

// 보조자가 화면을 보지 않아도 알 수 있게 삐 소리와 진동으로 알린다
class Feedback {

    private final ToneGenerator tone;
    private final Vibrator vibrator;

    Feedback(Context context) {
        ToneGenerator t;
        try {
            t = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        } catch (RuntimeException e) {
            t = null; // 일부 기기·에뮬레이터는 소리 장치를 만들지 못한다
        }
        tone = t;
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // 카운트다운 한 번
    void tick() {
        beep(ToneGenerator.TONE_PROP_BEEP, 80);
    }

    // 녹화·측정 시작
    void start() {
        beep(ToneGenerator.TONE_PROP_BEEP2, 200);
        vibrate(new long[]{0, 120});
    }

    // 정상 끝, 포즈 발동
    void done() {
        beep(ToneGenerator.TONE_PROP_ACK, 250);
        vibrate(new long[]{0, 60, 80, 60});
    }

    // 경고 기록, 자세 이탈
    void warn() {
        beep(ToneGenerator.TONE_PROP_NACK, 400);
        vibrate(new long[]{0, 300, 120, 300});
    }

    private void beep(int type, int ms) {
        if (tone != null) tone.startTone(type, ms);
    }

    private void vibrate(long[] pattern) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }
}
