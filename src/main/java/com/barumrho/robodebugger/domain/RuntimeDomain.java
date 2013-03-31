package com.barumrho.robodebugger.domain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.barumrho.robodebugger.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RuntimeDomain extends Domain {
    private HashMap<String, SQLiteDatabase> mDatabaseMap = new HashMap<String, SQLiteDatabase>();

    public RuntimeDomain(Debugger debugger) {
        super(debugger);
    }

    public static String getDomainName() {
        return "Runtime";
    }

    public void addDatabase(String name, SQLiteDatabase database) {
        mDatabaseMap.put(name, database);
    }

    @Override
    public Map<String, Object> respond(String method, Map<String, Object> params) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;

        if ("getProperties".equals(method)) {
            String objectId = (String) params.get("objectId");
            String[] parts = objectId.split("\\.");
            String databaseName = parts[0];
            String table = parts[1];
            String id = parts[2];

            HashMap<String, Object> result = new HashMap<String, Object>();
            response.put("result", result);
            result.put("result", getObject(databaseName, table, id));
        }

        response.put("error", error);
        return response;
    }


    private ArrayList<Map<String, Object>> getObject(String databaseName, String table, String id) {
        ArrayList<Map<String, Object>> columns  = new ArrayList<Map<String, Object>>();

        SQLiteDatabase database = mDatabaseMap.get(databaseName);
        Cursor cursor = database.query(table, null, "_id = ?", new String[] {id}, null, null, null);
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
