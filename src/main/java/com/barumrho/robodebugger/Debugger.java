package com.barumrho.robodebugger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import com.barumrho.robodebugger.domain.Domain;
import com.barumrho.robodebugger.domain.IndexedDBDomain;
import com.barumrho.robodebugger.domain.PageDomain;
import com.barumrho.robodebugger.domain.RuntimeDomain;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketConnectionHandler;
import de.tavendo.autobahn.WebSocketException;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class Debugger extends WebSocketConnectionHandler {
    private static final String TAG = "ROBODEBUGGER";
    private String mWebSocketUri;
    private WebSocketConnection mConnection;
    private ObjectMapper mMapper = new ObjectMapper();
    private HashMap<String, Domain> mDomains = new HashMap<String, Domain>();
    private HashMap<String, String> mMetadata;

    public Debugger(String webSocketUri) {
        super();
        mWebSocketUri = webSocketUri;
    }

    public void configureAppMetadata(Context context) {
        String packageName = context.getPackageName();
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not find package info");
        }

        mMetadata = new HashMap<String, String>();
        mMetadata.put("app_id", packageName);

        if (packageInfo != null) {
            mMetadata.put("app_name", context.getString(packageInfo.applicationInfo.labelRes));
            mMetadata.put("app_version", packageInfo.versionName);
            mMetadata.put("app_build", Integer.toString(packageInfo.versionCode));

            ByteArrayOutputStream iconOutput = new ByteArrayOutputStream();
            Drawable icon = packageInfo.applicationInfo.loadIcon(context.getPackageManager());
            ((BitmapDrawable) icon).getBitmap().compress(Bitmap.CompressFormat.PNG, 0, iconOutput);
            mMetadata.put("app_icon_base64", Base64.encodeToString(iconOutput.toByteArray(), Base64.DEFAULT));
        }
        mMetadata.put("device_id", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        mMetadata.put("device_model", "Android " + Build.VERSION.RELEASE);
        mMetadata.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
    }

    public void addSQLiteDatabase(SQLiteDatabase database) {
        addDomain(new RuntimeDomain(this, database));
        addDomain(new IndexedDBDomain(this, database));
        addDomain(new PageDomain(this));
    }

    public void addDomain(Domain domain) {
        mDomains.put(domain.getDomainName(), domain);
    }

    public void connect() {
        try {
            WebSocketConnection connection = new WebSocketConnection();
            mConnection = connection;
            mConnection.connect(mWebSocketUri, this);
        } catch (WebSocketException e) {
            Log.d(TAG, e.toString());
        }
    }

    @Override
    public void onOpen() {
        Log.d(TAG, "Status: Connected to " + mWebSocketUri);
        HashMap<String, Object> registration = new HashMap<String, Object>();
        registration.put("method", "Gateway.registerDevice");
        registration.put("params", mMetadata);

        try {
            mConnection.sendTextMessage(mMapper.writeValueAsString(registration));
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    @Override
    public void onTextMessage(String payload) {
        try {
            Map<String, Object> map = mMapper.readValue(payload, Map.class);
            Integer id = (Integer) map.get("id");
            String command = (String) map.get("method");
            String[] methodParts = command.split("\\.");
            String domain = null;
            String method = null;
            if (methodParts.length == 2) {
                domain = methodParts[0];
                method = methodParts[1];
            }
            Map<String, Object> params = (Map<String, Object>) map.get("params");

            Log.d(TAG, Integer.toString(id) + ": " + payload);

            if (domain == null || !mDomains.containsKey(domain)) {
                HashMap<String, Object> response = new HashMap<String, Object>();
                response.put("id", id);
                response.put("error", "unknown domain" + domain);
                mConnection.sendTextMessage(mMapper.writeValueAsString(response));
            } else {
                Domain handler = mDomains.get(domain);
                Map<String, Object> response = handler.respond(method, params);
                response.put("id", id);

                Log.d(TAG, response.toString());
                mConnection.sendTextMessage(mMapper.writeValueAsString(response));
            }

        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    @Override
    public void onClose(int code, String reason) {
        Log.d(TAG, "Connection lost.");
    }

    public void sendEvent(Map<String, Object> event) {
        try {
            Log.d(TAG, event.toString());
            mConnection.sendTextMessage(mMapper.writeValueAsString(event));
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }
}
