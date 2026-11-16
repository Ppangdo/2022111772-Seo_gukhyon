package com.example.myandroidapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

// 커맨드웨어 등록 앱: 아래 탭으로 A~E 화면(프래그먼트)을 바꾼다
public class MainActivity extends AppCompatActivity implements AppState.Listener {

    private AppState s;
    private BottomNavigationView nav;
    private TextView chipHub, chipMode;
    private String shownTab;

    // 폰 센서 서비스 바인딩: 화면이 보이고(onStart~onStop) 폰 센서 모드일 때만 묶는다
    private boolean visible, bound;
    private SensorService sensorService;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            sensorService = ((SensorService.LocalBinder) binder).getService();
            sensorService.setCallback(deg -> s.onPhoneTilt(deg));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            sensorService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        s = AppState.get(this);
        chipHub = findViewById(R.id.chip_hub);
        chipMode = findViewById(R.id.chip_mode);
        chipMode.setOnClickListener(v -> s.togglePhoneMode());
        nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            String tab = tabFor(item.getItemId());
            if (!tab.equals(s.tab)) s.go(tab);
            return true;
        });
        showTab(s.tab);
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        s.addListener(this);
        onStateChanged();
    }

    @Override
    protected void onStop() {
        visible = false;
        syncSensor();
        s.removeListener(this);
        super.onStop();
    }

    private void syncSensor() {
        boolean want = visible && s.phoneMode;
        if (want && !bound) {
            bound = bindService(new Intent(this, SensorService.class), connection, BIND_AUTO_CREATE);
        } else if (!want && bound) {
            if (sensorService != null) sensorService.setCallback(null);
            unbindService(connection);
            sensorService = null;
            bound = false;
        }
    }

    @Override
    public void onStateChanged() {
        chipHub.setText("허브 연결 · 노드 " + s.nodes.size());
        chipMode.setText(s.phoneMode ? "폰 센서" : "시뮬레이션");
        chipMode.setBackgroundResource(s.phoneMode ? R.drawable.bg_chip_ok : R.drawable.bg_chip_warn);
        chipMode.setTextColor(ContextCompat.getColor(this, s.phoneMode ? R.color.cw_ok_dark : R.color.cw_warn_dark));
        chipMode.setContentDescription((s.phoneMode ? "폰 센서 모드" : "시뮬레이션 모드") + ", 눌러서 바꾸기");
        syncSensor();
        if (!s.tab.equals(shownTab)) showTab(s.tab);
    }

    @Override
    public void onTick() {
    }

    private void showTab(String tab) {
        shownTab = tab;
        Fragment f;
        switch (tab) {
            case "B":
                f = new RecordFragment();
                break;
            case "C":
                f = new MotionListFragment();
                break;
            case "D":
                f = new LiveTestFragment();
                break;
            case "E":
                f = new OutputFragment();
                break;
            default:
                f = new NodeFragment();
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
        int id = itemFor(tab);
        if (nav.getSelectedItemId() != id) nav.setSelectedItemId(id);
    }

    private static String tabFor(int itemId) {
        if (itemId == R.id.tab_b) return "B";
        if (itemId == R.id.tab_c) return "C";
        if (itemId == R.id.tab_d) return "D";
        if (itemId == R.id.tab_e) return "E";
        return "A";
    }

    private static int itemFor(String tab) {
        switch (tab) {
            case "B":
                return R.id.tab_b;
            case "C":
                return R.id.tab_c;
            case "D":
                return R.id.tab_d;
            case "E":
                return R.id.tab_e;
            default:
                return R.id.tab_a;
        }
    }
}
