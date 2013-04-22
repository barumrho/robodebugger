# RoboDebugger

PonyDebugger client for Android

## Features
- Database inspection through IndexDB UI

## Installation
RoboDebugger is not yet available on the Maven repository, but you
can intall it on your local repository.

```
$ mvn install
```

Add RoboDebugger as a Maven dependency:

```
<dependency>
    <groupId>com.barumrho</groupId>
    <artifactId>robodebugger</artifactId>
    <version>0.1-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

RoboDebugger depends on PonyDebugger's `ponyd` server. To install,
please follow the [PonyDebugger Quick Start]
(https://github.com/square/PonyDebugger#quick-start).

Once you have `ponyd` installed, run it with the following command,
so that your Android device can discover your `ponyd` instance.

```

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

