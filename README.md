# RoboDebugger

PonyDebugger client for Android

## Features
- Database inspection through IndexDB UI

## How to Use

```java
Context context = ...;
SQLiteDatabase db = ...;

if (BuildConfig.DEBUG) {
    Debugger debugger = new Debugger();
    debugger.configureAppMetadata(context);
    debugger.addSQLiteDatabase("Database", db);

    // Use Bonjour to discover the ponyd server
    debugger.autoconnect();

    // Specify the WebSocket
    debugger.connect("ws://127.0.0.1:9000/device");
}
```

