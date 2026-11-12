package com.example.myandroidapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// 폰에 저장된 동작(motions 테이블)을 관리한다. hub_slot 은 허브 슬롯 번호(0~2), 허브에 없으면 -1.
public class MotionDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "commandware.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "motions";

    public MotionDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "type TEXT NOT NULL, "
                + "nodes TEXT NOT NULL, "
                + "takes INTEGER NOT NULL, "
                + "base TEXT NOT NULL, "
                + "sens INTEGER NOT NULL, "
                + "sim TEXT NOT NULL, "
                + "hub_slot INTEGER NOT NULL DEFAULT -1)");

        // 처음 설치했을 때 목업과 같은 예시 동작 4개
        seed(db, "오른팔 들기", "pose", "02,03", 10, "stand", 85, "arm", 0);
        seed(db, "바른 앉은 자세", "fix", "01", 5, "sit", 75, "lean", 1);
        seed(db, "손 흔들기", "pose", "02,03", 7, "stand", 85, "none", -1);
        seed(db, "어깨 으쓱", "pose", "01,02", 3, "sit", 85, "none", -1);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    private void seed(SQLiteDatabase db, String name, String type, String nodes, int takes,
                      String base, int sens, String sim, int hubSlot) {
        AppState.Motion m = new AppState.Motion();
        m.name = name;
        m.type = type;
        m.nodes = new ArrayList<>(Arrays.asList(nodes.split(",")));
        m.takes = takes;
        m.base = base;
        m.sens = sens;
        m.sim = sim;
        m.hubSlot = hubSlot;
        db.insert(TABLE, null, toValues(m));
    }

    public List<AppState.Motion> loadAll() {
        List<AppState.Motion> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "_id");
        while (c.moveToNext()) {
            AppState.Motion m = new AppState.Motion();
            m.id = c.getLong(c.getColumnIndexOrThrow("_id"));
            m.name = c.getString(c.getColumnIndexOrThrow("name"));
            m.type = c.getString(c.getColumnIndexOrThrow("type"));
            String nodes = c.getString(c.getColumnIndexOrThrow("nodes"));
            m.nodes = nodes.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(nodes.split(",")));
            m.takes = c.getInt(c.getColumnIndexOrThrow("takes"));
            m.base = c.getString(c.getColumnIndexOrThrow("base"));
            m.sens = c.getInt(c.getColumnIndexOrThrow("sens"));
            m.sim = c.getString(c.getColumnIndexOrThrow("sim"));
            m.hubSlot = c.getInt(c.getColumnIndexOrThrow("hub_slot"));
            list.add(m);
        }
        c.close();
        return list;
    }

    public long insert(AppState.Motion m) {
        return getWritableDatabase().insert(TABLE, null, toValues(m));
    }

    public void update(AppState.Motion m) {
        getWritableDatabase().update(TABLE, toValues(m), "_id = ?", new String[]{String.valueOf(m.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id = ?", new String[]{String.valueOf(id)});
    }

    private ContentValues toValues(AppState.Motion m) {
        ContentValues v = new ContentValues();
        v.put("name", m.name);
        v.put("type", m.type);
        v.put("nodes", TextUtils.join(",", m.nodes));
        v.put("takes", m.takes);
        v.put("base", m.base);
        v.put("sens", m.sens);
        v.put("sim", m.sim);
        v.put("hub_slot", m.hubSlot);
        return v;
    }
}
