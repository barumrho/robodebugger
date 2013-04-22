package com.barumrho.robodebugger.domain;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.barumrho.robodebugger.Debugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class IndexedDBDomain extends Domain {
    private HashMap<String, SQLiteDatabase> mDatabaseMap = new HashMap<String, SQLiteDatabase>();
    private HashMap<String, HashMap<String, String>> mPrimaryKeys = new HashMap<String, HashMap<String, String>>();

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
        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;
        Integer requestId = null;
        if (params != null) {
            requestId = (Integer) params.get("requestId");
        }

        if ("requestDatabaseNamesForFrame".equals(method)) {
            broadcastDatabaseNames(requestId);
        } else if ("requestDatabase".equals(method)) {
            String name = (String) params.get("databaseName");
            broadcastDatabase(requestId, name);
        } else if ("requestData".equals(method)) {
            String name = (String) params.get("databaseName");
            String tableName = (String) params.get("objectStoreName");
            String indexName = (String) params.get("indexName");
            Integer pageSize = (Integer) params.get("pageSize");
            Integer skipCount = (Integer) params.get("skipCount");
            broadcastTable(requestId, name, tableName, indexName, pageSize, skipCount);
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

        SQLiteDatabase database = mDatabaseMap.get(databaseName);
        HashMap<String, String> primaryKeyForTable = new HashMap<String, String>();
        mPrimaryKeys.put(databaseName, primaryKeyForTable);

        Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                HashMap<String, Object> store = new HashMap<String, Object>();
                HashMap<String, Object> mainKeyPath = null;
                ArrayList<HashMap<String, Object>> indexes = new ArrayList<HashMap<String, Object>>();

                String tableName = cursor.getString(cursor.getColumnIndex("name"));
                Cursor columnsCursor = database.rawQuery("pragma table_info(" + tableName + ")", null);
                columnsCursor.moveToFirst();

                while (!columnsCursor.isAfterLast()) {
                    HashMap<String, Object> index = new HashMap<String, Object>();
                    HashMap<String, Object> keyPath = new HashMap<String, Object>();

                    String columnName = columnsCursor.getString(columnsCursor.getColumnIndex("name"));
                    index.put("name", columnName);
                    index.put("unique", false);
                    index.put("multiEntry", false);
                    index.put("keyPath", keyPath);
                    keyPath.put("type", "string");
                    keyPath.put("string", columnName);

                    indexes.add(index);

                    short pk = columnsCursor.getShort(columnsCursor.getColumnIndex("pk"));
                    if (pk == 1) {
                        mainKeyPath = keyPath;
                    }

                    columnsCursor.moveToNext();
                }

                if (mainKeyPath == null) {
                    mainKeyPath = indexes.get(0);
                }

                primaryKeyForTable.put(tableName, (String) mainKeyPath.get("string"));

                store.put("name", tableName);
                store.put("autoIncrement", false);
                store.put("indexes", indexes);
                store.put("keyPath", mainKeyPath);

                objectStores.add(store);
                cursor.moveToNext();
            }
        }

        mDebugger.sendEvent(event);
    }

    private void broadcastTable(Integer requestId, String databaseName, String table, String indexName, Integer pageSize, Integer skipCount) {
        HashMap<String, Object> event = new HashMap<String, Object>();
        HashMap<String, Object> params = new HashMap<String, Object>();
        ArrayList<HashMap<String, Object>> objectStoreDataEntries = new ArrayList<HashMap<String, Object>>();
        event.put("method", "IndexedDB.objectStoreDataLoaded");
        event.put("params", params);

        params.put("requestId", requestId);
        params.put("objectStoreDataEntries", objectStoreDataEntries);

        SQLiteDatabase database = mDatabaseMap.get(databaseName);
        String primaryKeyName = mPrimaryKeys.get(databaseName).get(table);
        indexName = (indexName != null && indexName.length() > 0) ? indexName : primaryKeyName;

        Cursor countCursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null);
        countCursor.moveToFirst();
        int count = countCursor.getInt(0);
        params.put("hasMore", count - (skipCount + pageSize) > 0);

        String limit = Integer.toString(skipCount) + ", " + Integer.toString(pageSize);
        Cursor cursor = database.query(table, null, null, null, null, null, indexName, limit);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                HashMap<String, Object> entry = new HashMap<String, Object>();
                HashMap<String, Object> key = new HashMap<String, Object>();
                HashMap<String, Object> primaryKey = new HashMap<String, Object>();
                HashMap<String, Object> value = new HashMap<String, Object>();

                String id = cursor.getString(cursor.getColumnIndex(primaryKeyName));
                objectStoreDataEntries.add(entry);
                entry.put("key", key);
                entry.put("primaryKey", primaryKey);
                entry.put("value", value);

                key.put("string", cursor.getString(cursor.getColumnIndex(indexName)));
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
