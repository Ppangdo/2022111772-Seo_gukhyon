package com.example.myandroidapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

// E. 출력 연결 (다음 단계에서 구현할 화면의 안내만)
public class OutputFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_output, container, false);
        bind(root.findViewById(R.id.out_sound), "소리 모듈 #1", "스피커 · 트랙 1~3", "시연에서 사용", true);
        bind(root.findViewById(R.id.out_motor), "모터 포트", "서보 · 진동 모터", "예정", false);
        bind(root.findViewById(R.id.out_led), "LED 포트", "조명 · 표시등", "예정", false);
        bind(root.findViewById(R.id.out_key), "PC 키 입력", "블루투스 키보드로 인식", "예정", false);
        return root;
    }

    private void bind(View row, String name, String desc, String state, boolean active) {
        row.setBackground(active
                ? Ui.round(requireContext(), Ui.SURFACE, Ui.LINE, 1, 12)
                : Ui.round(requireContext(), Ui.SURFACE, Ui.BTN_LINE, 1, 12));
        ((TextView) row.findViewById(R.id.out_name)).setText(name);
        ((TextView) row.findViewById(R.id.out_desc)).setText(desc);
        TextView st = row.findViewById(R.id.out_state);
        st.setText(state);
        st.setTextColor(active ? Ui.OK_DARK : Ui.MUTED);
        row.setAlpha(active ? 1f : 0.8f);
    }
}
