package com.lzp.hookhundun;

public class Log {
    public static void e(String tag, String msg) {
        android.util.Log.e(tag, "=================" + msg + "===================");
    }

    public static void e(String tag, String msg, Throwable throwable) {
        android.util.Log.e(tag, "===================" + msg + "====================", throwable);
    }
}
