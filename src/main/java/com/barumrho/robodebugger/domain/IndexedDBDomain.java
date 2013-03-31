package com.barumrho.robodebugger.domain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.barumrho.robodebugger.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IndexedDBDomain extends Domain {
    private Integer mRequestId;
    private SQLiteDatabase mDatabase;
    private ArrayList<String> mTableNames = new ArrayList<String>();

    public IndexedDBDomain(Debugger debugger, SQLiteDatabase database) {
        super(debugger);
        mDatabase = database;

        Cursor cursor = mDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                mTableNames.add(cursor.getString(cursor.getColumnIndex("name")));
                cursor.moveToNext();
            }
        }
    }

    @Override
    public String getDomainName() {
        return "IndexedDB";
    }

    @Override
    public Map<String, Object> respond(String method, Map<String, Object> params) {
        Object requestId = params.get("requestId");
        if (requestId != null) {
            mRequestId = (Integer) requestId;
        }

        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;
        if ("requestDatabaseNamesForFrame".equals(method)) {
            broadcastDatabaseNames();
        } else if ("requestDatabase".equals(method)) {
            broadcastDatabase();
        } else if ("requestData".equals(method)) {
            String tableName = (String) params.get("objectStoreName");
            Integer pageSize = (Integer) params.get("pageSize");
            Integer skipCount = (Integer) params.get("skipCount");
            broadcastTable(tableName, pageSize, skipCount);
        }

        response.put("error", error);
        return response;
    }

    private void broadcastDatabaseNames() {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        HashMap<String, Object> databases = new HashMap<String, Object>();
        ArrayList<String> names = new ArrayList<String>();
        event.put("method", "IndexedDB.databaseNamesLoaded");
        event.put("params", params);
        params.put("requestId", mRequestId);
        params.put("securityOriginWithDatabaseNames", databases);
        databases.put("databaseNames", names);
        databases.put("securityOrigin", "com.freshbooks.android");
        names.add("FreshBooks");

        mDebugger.sendEvent(event);
    }

    private void broadcastDatabase() {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        HashMap<String, Object> database = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> objectStores = new ArrayList<HashMap<String, Object>>();
        event.put("method", "IndexedDB.databaseLoaded");
        event.put("params", params);
        params.put("requestId", mRequestId);
        params.put("databaseWithObjectStores", database);
        database.put("name", "FreshBooks");
        database.put("version", "N/A");
        database.put("objectStores", objectStores);


        HashMap<String, Object> keyPath = new HashMap<String, Object>();
        keyPath.put("string", "_id");
        keyPath.put("type", "string");
        for (String table : mTableNames) {
            HashMap<String, Object> store = new HashMap<String, Object>();
            store.put("autoIncrement", false);
            store.put("indexes", new ArrayList());
            store.put("keyPath", keyPath);
            store.put("name", table);

            objectStores.add(store);
        }

        mDebugger.sendEvent(event);
    }

    private void broadcastTable(String table, Integer pageSize, Integer skipCount) {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> objectStoreDataEntries = new ArrayList<HashMap<String, Object>>();
        event.put("method", "IndexedDB.objectStoreDataLoaded");
        event.put("params", params);

        params.put("requestId", mRequestId);
        params.put("objectStoreDataEntries", objectStoreDataEntries);

        Cursor cursor = mDatabase.query(table, null, null, null, null, null, "_id", Integer.toString(pageSize));
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                HashMap<String, Object> entry = new HashMap<String, Object>();

                for (String columnName : cursor.getColumnNames()) {
                    HashMap<String, Object> key = new HashMap<String, Object>();
                }

                HashMap<String, Object> key = new HashMap<String, Object>();
                HashMap<String, Object> primaryKey = new HashMap<String, Object>();
                HashMap<String, Object> value = new HashMap<String, Object>();

                String id = cursor.getString(cursor.getColumnIndex("_id"));

                objectStoreDataEntries.add(entry);
                entry.put("key", key);
                entry.put("primaryKey", primaryKey);
                entry.put("value", value);

                key.put("string", id);
                key.put("type", "string");

                primaryKey.put("string", id);
                primaryKey.put("type", "string");

                value.put("className", table);
                value.put("description", table);
                value.put("objectId", table + "." + id);
                value.put("type", "object");

                cursor.moveToNext();
            }
        }

        mDebugger.sendEvent(event);
    }
}
