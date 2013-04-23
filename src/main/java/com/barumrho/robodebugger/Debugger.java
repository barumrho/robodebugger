package com.barumrho.robodebugger;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
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

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Debugger extends WebSocketConnectionHandler {
    public static final String APP_ID_KEY = "app_id";
    public static final String APP_NAME_KEY = "app_name";
    public static final String APP_VERSION_KEY = "app_version";
    public static final String APP_BUILD_KEY = "app_build";
    public static final String APP_ICON_BASE_64_KEY = "app_icon_base64";
    public static final String DEVICE_ID_KEY = "device_id";
    public static final String DEVICE_MODEL_KEY = "device_model";
    public static final String DEVICE_NAME_KEY = "device_name";

    private static final String TAG = "ROBODEBUGGER";
    private static final String SERVICE_NAME = "_ponyd._tcp.local.";

    private String mWebSocketUri;
    private WebSocketConnection mConnection;
    private boolean mIsConnected;
    private boolean mIsAutoconnectEnabled;
    private ObjectMapper mMapper = new ObjectMapper();
    private HashMap<String, Domain> mDomains = new HashMap<String, Domain>();
    private HashMap<String, String> mMetadata;
    private Handler mHandler;
    private JmDNS mJmDNS;

    public Debugger() {
        super();
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
        mMetadata.put(APP_ID_KEY, packageName);

        if (packageInfo != null) {
            mMetadata.put(APP_NAME_KEY, context.getString(packageInfo.applicationInfo.labelRes));
            mMetadata.put(APP_VERSION_KEY, packageInfo.versionName);
            mMetadata.put(APP_BUILD_KEY, Integer.toString(packageInfo.versionCode));

            ByteArrayOutputStream iconOutput = new ByteArrayOutputStream();
            Drawable icon = packageInfo.applicationInfo.loadIcon(context.getPackageManager());
            ((BitmapDrawable) icon).getBitmap().compress(Bitmap.CompressFormat.PNG, 0, iconOutput);
            mMetadata.put(APP_ICON_BASE_64_KEY, Base64.encodeToString(iconOutput.toByteArray(), Base64.DEFAULT));
        }
        mMetadata.put(DEVICE_ID_KEY, Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        mMetadata.put(DEVICE_MODEL_KEY, "Android " + Build.VERSION.RELEASE);
        mMetadata.put(DEVICE_NAME_KEY, Build.MANUFACTURER + " " + Build.MODEL);
    }

    public HashMap<String, String> getMetadata() {
        return mMetadata;
    }

    public void addSQLiteDatabase(String name, SQLiteDatabase database) {
        RuntimeDomain runtimeDomain = (RuntimeDomain) getDomain(RuntimeDomain.getDomainName());
        if (runtimeDomain == null) {
            runtimeDomain = new RuntimeDomain(this);
            addDomain(RuntimeDomain.getDomainName(), runtimeDomain);
        }
        runtimeDomain.addDatabase(name, database);

        IndexedDBDomain indexedDBDomain = (IndexedDBDomain) getDomain(IndexedDBDomain.getDomainName());
        if (indexedDBDomain == null) {
            indexedDBDomain = new IndexedDBDomain(this);
            addDomain(IndexedDBDomain.getDomainName(), indexedDBDomain);
        }
        indexedDBDomain.addDatabase(name, database);


        PageDomain pageDomain = (PageDomain) getDomain(PageDomain.getDomainName());
        if (pageDomain == null) {
            addDomain(PageDomain.getDomainName(), new PageDomain(this));
        }
    }

    public void addDomain(String domainName, Domain domain) {
        mDomains.put(domainName, domain);
    }

    public Domain getDomain(String domainName) {
        return mDomains.get(domainName);
    }

    public void connect(String webSocketUri) {
        if (mIsConnected) {
            Log.d(TAG, "Already connected to " + mWebSocketUri);
            return;
        }

        mIsConnected = true;
        mWebSocketUri = webSocketUri;
        try {
            Log.d(TAG, "Connecting to " + webSocketUri);
            WebSocketConnection connection = new WebSocketConnection();
            mConnection = connection;
            mConnection.connect(mWebSocketUri, this);
        } catch (WebSocketException e) {
            Log.d(TAG, e.toString());
            mIsConnected = false;
        }
    }

    public void autoconnect() {
        Log.i(TAG, "Autoconnecting...");
        mIsAutoconnectEnabled = true;
        mHandler = new Handler();

        Thread discoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mJmDNS = JmDNS.create();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (mJmDNS == null) {
                    Log.e(TAG, "Could not get an instance of JmDNS");
                    return;
                }

                mJmDNS.addServiceListener(SERVICE_NAME, new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent serviceEvent) {
                        Log.d(TAG, "Service added: " + serviceEvent.toString());
                        serviceEvent.getDNS().requestServiceInfo(SERVICE_NAME, serviceEvent.getName());
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent serviceEvent) {
                        Log.d(TAG, "Service removed: " + serviceEvent.toString());
                    }

                    @Override
                    public void serviceResolved(ServiceEvent serviceEvent) {
                        Log.d(TAG, "Service resolved: " + serviceEvent.toString());

                        final String socketUri = "ws://" + serviceEvent.getInfo().getHostAddresses()[0] + ":" + serviceEvent.getInfo().getPort() + "/device";
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                connect(socketUri);
                            }
                        });
                    }
                });

                Log.i(TAG, "Looking for PonyDebugger service...");
                mJmDNS.list(SERVICE_NAME);

                while (!mIsConnected) {
                }

                mHandler = null;
                try {
                    mJmDNS.close();
                } catch (IOException e) {
                    Log.e(TAG, "IOException while closing JmDNS.");
                }
            }
        });

        discoveryThread.start();
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
                response.put("error", "Unknown domain " + domain);
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
        mIsConnected = false;

        if (mIsAutoconnectEnabled) {
            autoconnect();
        }
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
