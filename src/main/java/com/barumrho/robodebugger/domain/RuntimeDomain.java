package com.barumrho.robodebugger.domain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.barumrho.robodebugger.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RuntimeDomain extends Domain {
    private SQLiteDatabase mDatabase;

    public RuntimeDomain(Debugger debugger, SQLiteDatabase database) {
        super(debugger);
        mDatabase = database;
    }

    @Override
    public String getDomainName() {
        return "Runtime";
    }

    @Override
    public Map<String, Object> respond(String method, Map<String, Object> params) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;

        if ("getProperties".equals(method)) {
            String objectId = (String) params.get("objectId");
            String[] parts = objectId.split("\\.");
            String table = parts[0];
            String id = parts[1];

            HashMap<String, Object> result = new HashMap<String, Object>();
            response.put("result", result);
            result.put("result", getObject(table, id));
        }

        response.put("error", error);
        return response;
    }


    private ArrayList<Map<String, Object>> getObject(String table, String id) {
        ArrayList<Map<String, Object>> columns  = new ArrayList<Map<String, Object>>();

        Cursor cursor = mDatabase.query(table, null, "_id = ?", new String[] {id}, null, null, null);
        if (cursor.moveToFirst()) {
            for (String columnName : cursor.getColumnNames()) {
                HashMap<String, Object> column = new HashMap<String, Object>();
                HashMap<String, Object> value = new HashMap<String, Object>();
                column.put("configurable", false);
                column.put("enumerable", true);
                column.put("name", columnName);
                column.put("value", value);
                column.put("wasThrown", false);
                column.put("writable", false);

                int index = cursor.getColumnIndex(columnName);
                value.put("className", Integer.toString(cursor.getType(index)));
                value.put("type", "string");
                value.put("value", cursor.getString(index));

                columns.add(column);
            }
        }

        return columns;
    }
}
