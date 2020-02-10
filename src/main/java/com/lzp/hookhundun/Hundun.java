package com.lzp.hookhundun;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.logging.HttpLoggingInterceptor;

public class Hundun implements IXposedHookLoadPackage {
    static final String TAG = "Test";
    private Activity mTopActivity;
    private Application mApplication;

    private static int mVideoCount = 0;
    private static List<CourseVideo> mCourseVideos;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null) return;

        if (lpparam.packageName.equals("com.hundun.yanxishe")) {
            Log.e(TAG, "hook hundun");

            XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    mTopActivity = (Activity) param.getResult();
                }
            });

            XposedHelpers.findAndHookConstructor("com.hundun.yanxishe.application.RealApplication", lpparam.classLoader, Class.forName("android.app.Application"), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    mApplication = (Application) param.args[0];

                    mCourseVideos = new ArrayList<>();

                    IntentFilter filter = new IntentFilter();
                    filter.addAction("com.test.test.print");
                    mApplication.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            printVideo();
                        }
                    }, filter);
                    Log.e(TAG, "register broadcast");
                }
            });

//            XposedHelpers.findAndHookMethod("okhttp3.internal.platform.AndroidPlatform", lpparam.classLoader, "log", int.class, String.class, Throwable.class, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    String message = (String) param.args[1];
//                    File file = new File(mApplication.getExternalFilesDir("HttpLog"), "log.txt");
//                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
//                        writer.write(message);
//                        writer.newLine();
//                    }
//                }
//            });

            XposedHelpers.findAndHookMethod("okhttp3.logging.HttpLoggingInterceptor", lpparam.classLoader, "intercept", "okhttp3.Interceptor$Chain", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    Log.e(TAG, "hook HttpLoggingInterceptor.intercept");
                    Object response = param.getResult();
                    Object request = XposedHelpers.callMethod(response, "request");
                    Object httpUrl = XposedHelpers.callMethod(request, "url");
                    String url = (String) XposedHelpers.callMethod(httpUrl, "toString");
//                    Log.e(TAG, "list viedo request url=" + url);
                    if (url.startsWith("https://course.hundun.cn/video/list_video_url")) {
//                        Log.e(TAG, "list_video_url");
                        Object body = XposedHelpers.callMethod(response, "body");
                        Object source = XposedHelpers.callMethod(body, "source");
                        XposedHelpers.callMethod(source, "request", Long.MAX_VALUE);
                        Object buffer = XposedHelpers.callMethod(source, "buffer");
                        Object newBuffer = XposedHelpers.callMethod(buffer, "clone");
                        Charset UTF8 = Charset.forName("UTF-8");
                        String strBody = (String) XposedHelpers.callMethod(newBuffer, "readString", UTF8);
                        Log.e(TAG, "body=" + strBody);

                        JSONObject jsonObject = new JSONObject(strBody);
                        JSONObject data = jsonObject.getJSONObject("data");
                        JSONArray video_list = data.getJSONArray("video_list");
                        int len = video_list.length();
                        CourseVideo courseVideo = new CourseVideo();
                        for (int i = 0; i < len; i++) {
                            JSONObject video = video_list.getJSONObject(i);
                            String hd = video.getString("hd_url");
                            courseVideo.add(decodeHd(hd, lpparam));
                        }
                        mCourseVideos.add(courseVideo);

                        mVideoCount += courseVideo.hds.size();
                        Log.e(TAG, "收到" + len + "个视频" + ",共有" + mVideoCount + "个视频");
                    }
                }
            });


            XposedHelpers.findAndHookConstructor("com.hundun.connect.e", lpparam.classLoader, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Log.e(TAG, "create retrofit");
                    try {
                        Object retrofit = XposedHelpers.getObjectField(param.thisObject, "a");

                        Object client = XposedHelpers.callMethod(retrofit, "callFactory");
                        Object clientBuilder = XposedHelpers.callMethod(client, "newBuilder");

                        Class logInterceptorClz = XposedHelpers.findClass("okhttp3.logging.HttpLoggingInterceptor", lpparam.classLoader);
                        Object logInterceptor = XposedHelpers.newInstance(logInterceptorClz);

                        Class levelClz = XposedHelpers.findClass("okhttp3.logging.HttpLoggingInterceptor$Level", lpparam.classLoader);
                        Object bodyLevel = levelClz.getEnumConstants()[3];
                        XposedHelpers.callMethod(logInterceptor, "setLevel", bodyLevel);

                        clientBuilder = XposedHelpers.callMethod(clientBuilder, "addInterceptor", logInterceptor);
                        Object newClient = XposedHelpers.callMethod(clientBuilder, "build");

                        Object retrofitBuilder = XposedHelpers.callMethod(retrofit, "newBuilder");
                        retrofitBuilder = XposedHelpers.callMethod(retrofitBuilder, "client", newClient);
                        Object newRetrofit = XposedHelpers.callMethod(retrofitBuilder, "build");
                        XposedHelpers.setObjectField(param.thisObject, "a", newRetrofit);

                        Log.e(TAG, "create retrofit success");
                    } catch (Throwable e) {
                        Log.e(TAG, "hook retrofit error", e);
                    }
                }
            });

//            XposedHelpers.findAndHookMethod("com.hundun.yanxishe.modules.course.projector.entity.VideoUrl", lpparam.classLoader, "getDecrypt_Hd_url", new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    String hdUrl = (String) param.getResult();
//                    Log.e(TAG, "hdUrl=" + hdUrl);
//                }
//            });
//
//            XposedHelpers.findAndHookMethod("com.hundun.yanxishe.modules.course.projector.entity.VideoUrl", lpparam.classLoader, "getDecrypt_Sd_url", new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    String sdUrl = (String) param.getResult();
//                    Log.e(TAG, "sdUrl=" + sdUrl);
//                }
//            });
//
//            XposedHelpers.findAndHookMethod("com.hundun.yanxishe.modules.course.projector.entity.VideoUrl", lpparam.classLoader, "getDecrypt_Ad_url", new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    String adUrl = (String) param.getResult();
//                    Log.e(TAG, "adUrl=" + adUrl);
//                }
//            });
        }
    }

    private String decodeHd(String hd, XC_LoadPackage.LoadPackageParam lparam) {
//        com.hundun.yanxishe.modules.me.b.a.b().i()
        try {
            Class clzA = XposedHelpers.findClass("com.hundun.yanxishe.modules.me.b.a", lparam.classLoader);
            Object tmp = XposedHelpers.callStaticMethod(clzA, "b");
            Object key = XposedHelpers.callMethod(tmp, "i");

//            a.a(this.ad_url, com.hundun.yanxishe.modules.me.b.a.b().i());
            Class clzDecoder = XposedHelpers.findClass("com.hundun.yanxishe.modules.download.d.a", lparam.classLoader);
            return (String) XposedHelpers.callStaticMethod(clzDecoder, "a", hd, key);
        } catch (Throwable t) {
            Log.e(TAG, "decoder error", t);
        }
        return "";
    }

    private void printVideo() {
        Log.e(TAG, "receive broadcast");
        for (CourseVideo courseVideo : mCourseVideos) {
            for (String hd : courseVideo.hds) {
                Log.e(TAG, hd);
            }
        }
        mCourseVideos.clear();
        mVideoCount = 0;
    }

    public static class MyLog implements HttpLoggingInterceptor.Logger {
        @Override
        public void log(String message) {
            Log.e(TAG, message);
        }
    }
}
