package com.barumrho.robodebugger.domain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.barumrho.robodebugger.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IndexedDBDomain extends Domain {
    private HashMap<String, SQLiteDatabase> mDatabaseMap = new HashMap<String, SQLiteDatabase>();

    public IndexedDBDomain(Debugger debugger) {
        super(debugger);
    }

    public static String getDomainName() {
        return "IndexedDB";
    }

    public void addDatabase(String name, SQLiteDatabase database) {
        mDatabaseMap.put(name, database);
    }

    @Override
    public Map<String, Object> respond(String method, Map<String, Object> params) {
        Integer requestId = (Integer) params.get("requestId");

        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;
        if ("requestDatabaseNamesForFrame".equals(method)) {
            broadcastDatabaseNames(requestId);
        } else if ("requestDatabase".equals(method)) {
            String name = (String) params.get("databaseName");
            broadcastDatabase(requestId, name);
        } else if ("requestData".equals(method)) {
            String name = (String) params.get("databaseName");
            String tableName = (String) params.get("objectStoreName");
            Integer pageSize = (Integer) params.get("pageSize");
            Integer skipCount = (Integer) params.get("skipCount");
            broadcastTable(requestId, name, tableName, pageSize, skipCount);
        }

        response.put("error", error);
        return response;
    }

    private void broadcastDatabaseNames(Integer requestId) {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        HashMap<String, Object> databases = new HashMap<String, Object>();
        ArrayList<String> names = new ArrayList<String>();

        event.put("method", "IndexedDB.databaseNamesLoaded");
        event.put("params", params);
        params.put("requestId", requestId);
        params.put("securityOriginWithDatabaseNames", databases);
        databases.put("securityOrigin", mDebugger.getMetadata().get(Debugger.APP_ID_KEY));
        databases.put("databaseNames", names);
        names.addAll(mDatabaseMap.keySet());

        mDebugger.sendEvent(event);
    }

    private void broadcastDatabase(Integer requestId, String databaseName) {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        HashMap<String, Object> databaseObject = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> objectStores = new ArrayList<HashMap<String, Object>>();
        event.put("method", "IndexedDB.databaseLoaded");
        event.put("params", params);
        params.put("requestId", requestId);
        params.put("databaseWithObjectStores", databaseObject);
        databaseObject.put("name", databaseName);
        databaseObject.put("version", "N/A");
        databaseObject.put("objectStores", objectStores);

        HashMap<String, Object> keyPath = new HashMap<String, Object>();
        keyPath.put("string", "_id");
        keyPath.put("type", "string");

        SQLiteDatabase database = mDatabaseMap.get(databaseName);

        Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                HashMap<String, Object> store = new HashMap<String, Object>();
                store.put("autoIncrement", false);
                store.put("indexes", new ArrayList());
                store.put("keyPath", keyPath);
                store.put("name", cursor.getString(cursor.getColumnIndex("name")));

                objectStores.add(store);
                cursor.moveToNext();
            }
        }

        mDebugger.sendEvent(event);
    }

    private void broadcastTable(Integer requestId, String databaseName, String table, Integer pageSize, Integer skipCount) {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> objectStoreDataEntries = new ArrayList<HashMap<String, Object>>();
        event.put("method", "IndexedDB.objectStoreDataLoaded");
        event.put("params", params);

        params.put("requestId", requestId);
        params.put("objectStoreDataEntries", objectStoreDataEntries);

        SQLiteDatabase database = mDatabaseMap.get(databaseName);
        Cursor cursor = database.query(table, null, null, null, null, null, "_id", Integer.toString(pageSize));
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
                value.put("objectId", databaseName + "." + table + "." + id);
                value.put("type", "object");

                cursor.moveToNext();
            }
        }

        mDebugger.sendEvent(event);
    }
}
