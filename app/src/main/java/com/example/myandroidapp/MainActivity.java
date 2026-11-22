package com.example.myandroidapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

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

    // 저장된 글자 크기(작게·보통·크게)를 화면 전체에 적용한다
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Settings.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.applyTheme(this); // 밝게·어둡게는 화면을 만들기 전에
        super.onCreate(savedInstanceState);
        Ui.init(this); // 지금 테마(밝은·어두운)의 색을 읽는다
        setContentView(R.layout.activity_main);
        s = AppState.get(this);
        chipHub = findViewById(R.id.chip_hub);
        chipMode = findViewById(R.id.chip_mode);
        chipMode.setOnClickListener(v -> showSettings());
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
        chipHub.setText("✓ 허브 · 노드 " + s.nodes.size());
        // 모드는 경고·정상 같은 상태가 아니라서 회색 칩(경고색이 늘 떠 있으면 진짜 경고에 무뎌진다)
        chipMode.setText(s.phoneMode ? "폰 센서 ▾" : "시뮬레이션 ▾");
        chipMode.setBackgroundResource(R.drawable.bg_chip_neutral);
        chipMode.setTextColor(ContextCompat.getColor(this, R.color.cw_ink));
        chipMode.setContentDescription((s.phoneMode ? "폰 센서 모드" : "시뮬레이션 모드") + ", 눌러서 바꾸기");
        syncSensor();
        if (!s.tab.equals(shownTab)) showTab(s.tab);
    }

    @Override
    public void onTick() {
    }

    // 헤더의 모드 칩(▾)을 누르면 여는 설정 대화상자(7장 대화상자): 센서 입력 / 글자 크기 / 화면
    private void showSettings() {
        View v = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        RadioGroup mode = v.findViewById(R.id.set_mode);
        RadioGroup text = v.findViewById(R.id.set_text);
        RadioGroup theme = v.findViewById(R.id.set_theme);
        TextView modeHint = v.findViewById(R.id.set_mode_hint);
        fill(mode, new String[]{"시뮬레이션", "폰 센서"}, s.phoneMode ? 1 : 0);
        fill(text, Settings.TEXT_NAMES, Settings.textSize(this));
        fill(theme, Settings.THEME_NAMES, Settings.theme(this));
        if (!s.hasPhoneSensor) {
            mode.getChildAt(1).setEnabled(false);
            modeHint.setText("이 기기에는 방향 센서가 없어 폰 센서를 쓸 수 없습니다.");
        } else {
            modeHint.setText("폰 센서: 폰을 오른 아래팔에 대고 실제로 움직여 시험합니다.");
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("화면·입력 설정")
                .setView(v)
                .setPositiveButton("닫기", null)
                .create();
        mode.setOnCheckedChangeListener((g, id) -> s.setPhoneMode(id == 1));
        text.setOnCheckedChangeListener((g, id) -> {
            Settings.setTextSize(this, id);
            dialog.dismiss();
            recreate(); // 글자 배율은 화면을 다시 만들어야 적용된다
        });
        theme.setOnCheckedChangeListener((g, id) -> {
            dialog.dismiss();
            Settings.setTheme(this, id); // 바뀌면 화면이 저절로 다시 만들어진다
        });
        dialog.show();
    }

    // 라디오 버튼의 id 를 0,1,2… 순서로 준다
    private void fill(RadioGroup group, String[] names, int checked) {
        for (int i = 0; i < names.length; i++) {
            RadioButton b = new RadioButton(this);
            b.setId(i);
            b.setText(names[i]);
            b.setTextSize(16);
            b.setMinHeight(Ui.dp(this, 48));
            b.setPadding(0, 0, Ui.dp(this, 12), 0);
            b.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            b.setChecked(i == checked);
            group.addView(b);
        }
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
