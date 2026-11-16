package com.example.myandroidapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;

// 폰 자체 센서를 노드 하나처럼 쓰는 바인딩 서비스(14장 서비스).
// 화면(MainActivity)이 bindService 하는 동안만 센서를 등록하고, unbind 하면 해제한다.
// 폰을 오른 아래팔에 길게 댔다고 보고, 폰 세로축(y축)이 '위쪽'과 이루는 각도(0~180°)를 계산해 알려 준다.
public class SensorService extends Service implements SensorEventListener {

    public interface Callback {
        void onTilt(float deg);
    }

    public class LocalBinder extends Binder {
        SensorService getService() {
            return SensorService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    private SensorManager sm;
    private Sensor sensor;
    private Callback callback;
    private final float[] rot = new float[9];
    private final float[] up = new float[3];
    private boolean hasUp;

    // 쓸 수 있는 센서: 게임 회전 벡터(자이로+가속도, 자기장 영향 없음) > 중력 > 가속도
    static Sensor pick(SensorManager sm) {
        if (sm == null) return null;
        int[] types = {Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER};
        for (int t : types) {
            Sensor s = sm.getDefaultSensor(t);
            if (s != null) return s;
        }
        return null;
    }

    static boolean available(Context context) {
        return pick((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)) != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = pick(sm);
    }

    @Override
    public IBinder onBind(Intent intent) {
        hasUp = false;
        if (sensor != null) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sm.unregisterListener(this);
        callback = null;
        return false;
    }

    @Override
    public void onDestroy() {
        sm.unregisterListener(this);
        super.onDestroy();
    }

    void setCallback(Callback cb) {
        callback = cb;
    }

    String sensorName() {
        if (sensor == null) return "없음";
        switch (sensor.getType()) {
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return "게임 회전 벡터";
            case Sensor.TYPE_GRAVITY:
                return "중력";
            default:
                return "가속도";
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x, y, z;
        float alpha; // 저역통과 필터 계수: 작을수록 부드럽지만 늦다
        if (e.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            // 회전 행렬의 셋째 행 = 세계 좌표의 '위쪽'을 기기 좌표로 나타낸 것
            SensorManager.getRotationMatrixFromVector(rot, e.values);
            x = rot[6];
            y = rot[7];
            z = rot[8];
            alpha = 0.3f;
        } else {
            // 가만히 있을 때 가속도(중력) 센서 값은 위쪽을 가리킨다
            x = e.values[0];
            y = e.values[1];
            z = e.values[2];
            alpha = e.sensor.getType() == Sensor.TYPE_GRAVITY ? 0.3f : 0.15f;
        }
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-3f) return;
        x /= len;
        y /= len;
        z /= len;
        if (!hasUp) {
            up[0] = x;
            up[1] = y;
            up[2] = z;
            hasUp = true;
        } else {
            up[0] += alpha * (x - up[0]);
            up[1] += alpha * (y - up[1]);
            up[2] += alpha * (z - up[2]);
        }
        float n = (float) Math.sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]);
        float cos = Math.max(-1f, Math.min(1f, up[1] / n));
        // 폰 세로축이 위쪽과 이루는 각도. 팔을 내리면 약 180°(또는 0°), 들면 90° → 0°(또는 180°) 쪽으로 바뀐다
        float tilt = (float) Math.toDegrees(Math.acos(cos));
        if (callback != null) callback.onTilt(tilt);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
