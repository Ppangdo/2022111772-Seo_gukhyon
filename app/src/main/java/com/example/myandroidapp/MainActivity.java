package com.example.myandroidapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;

// 커맨드웨어 등록 앱: 아래 탭으로 A~E 화면(프래그먼트)을 바꾼다
public class MainActivity extends AppCompatActivity implements AppState.Listener {

    private AppState s;
    private BottomNavigationView nav;
    private TextView chipHub;
    private String shownTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        s = AppState.get(this);
        chipHub = findViewById(R.id.chip_hub);
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
        s.addListener(this);
        onStateChanged();
    }

    @Override
    protected void onStop() {
        s.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged() {
        chipHub.setText("허브 연결됨 · 노드 " + s.nodes.size() + "개");
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
