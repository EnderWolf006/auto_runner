package com.xposed;

import static androidx.core.app.ActivityCompat.startActivityForResult;
import static androidx.core.content.ContextCompat.registerReceiver;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    Context ctx;
    static final float startStep = 16f;

    // 存储所有注册的传感器监听器
    private static CopyOnWriteArrayList<ListenerInfo> stepListeners = new CopyOnWriteArrayList<>();
    private static Handler mainHandler;
    private static boolean isTimerRunning = false;

    // 监听器信息类
    static class ListenerInfo {
        SensorEventListener listener;
        Sensor sensor;
        Object sensorManager;

        ListenerInfo(SensorEventListener listener, Sensor sensor, Object sensorManager) {
            this.listener = listener;
            this.sensor = sensor;
            this.sensorManager = sensorManager;
        }
    }

    private static XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences("com.example.auto_runner", path);
        return pref.getFile().canRead() ? pref : null;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        Log.i("xposed", "handleLoadPackage: " + loadPackageParam.packageName);

        // Hook registerListener 来捕获传感器监听器
        hookRegisterListener(loadPackageParam);

        // Hook unregisterListener 来移除监听器
        hookUnregisterListener(loadPackageParam);

        // Hook 原有的 dispatchSensorEvent
        final Class<?> sensorEL = findClass("android.hardware.SystemSensorManager$SensorEventQueue",
                loadPackageParam.classLoader);
        XposedBridge.hookAllMethods(sensorEL, "dispatchSensorEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                int intValue = (Integer) param.args[0];
                Field declaredField = param.thisObject.getClass().getDeclaredField("mSensorsEvents");
                declaredField.setAccessible(true);
                Sensor sensor = ((SensorEvent) ((SparseArray<?>) declaredField.get(param.thisObject))
                        .get(intValue)).sensor;
                int sensortype = sensor.getType();
//                Log.i("xposed", "sensortype: " + sensortype);

                if (sensortype == 19 || sensortype == 18) {
                    float newStep = getStep();
                    ((float[]) param.args[1])[0] = newStep;
                    Log.w("xposed", "newStep= " + newStep);
                }
            }

        });
    }

    float getStep(){
        int nowStep;
        XSharedPreferences pref = getPref("auto_runner");
        nowStep = pref.getInt("step", -1);
        return startStep + nowStep;
    }

    // Hook SensorManager.registerListener
    private void hookRegisterListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> sensorManagerClass = findClass("android.hardware.SystemSensorManager", lpparam.classLoader);

            // registerListener 有多个重载方法，Hook 主要的那个
            XposedBridge.hookAllMethods(sensorManagerClass, "registerListenerImpl", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    SensorEventListener listener = (SensorEventListener) param.args[0];
                    Sensor sensor = (Sensor) param.args[1];
                    if (sensor != null) {
                        Log.i("xposed", "registerListener" + sensor.getType());
                    }
                    if (sensor != null && (sensor.getType() == Sensor.TYPE_STEP_COUNTER ||
                            sensor.getType() == Sensor.TYPE_STEP_DETECTOR)) {
                        Log.i("xposed", "Captured step sensor listener registration: type=" + sensor.getType());
                        stepListeners.add(new ListenerInfo(listener, sensor, param.thisObject));

                        // 启动定时器（如果还没启动）
                        startSensorEventTimer(lpparam.classLoader);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e("xposed", "Error hooking registerListener", t);
        }
    }

    // Hook SensorManager.unregisterListener
    private void hookUnregisterListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> sensorManagerClass = findClass("android.hardware.SystemSensorManager", lpparam.classLoader);

            XposedBridge.hookAllMethods(sensorManagerClass, "unregisterListenerImpl", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    SensorEventListener listener = (SensorEventListener) param.args[0];
                    Sensor sensor = (Sensor) param.args[1];

                    // 从列表中移除
                    stepListeners.removeIf(info -> info.listener == listener &&
                            (sensor == null || info.sensor == sensor));
                    Log.i("xposed", "Removed listener, remaining: " + stepListeners.size());
                }
            });
        } catch (Throwable t) {
            Log.e("xposed", "Error hooking unregisterListener", t);
        }
    }

    // 启动定时器，每秒触发传感器事件
    private void startSensorEventTimer(ClassLoader classLoader) {
        if (isTimerRunning) {
            return;
        }

        isTimerRunning = true;

        // 确保在主线程创建 Handler
        if (mainHandler == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mainHandler = new Handler(Looper.getMainLooper());
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    mainHandler = new Handler(Looper.getMainLooper());
                    scheduleNextEvent(classLoader);
                });
                return;
            }
        }

        scheduleNextEvent(classLoader);
        Log.i("xposed", "Sensor event timer started");
    }

    // 调度下一次事件
    private void scheduleNextEvent(ClassLoader classLoader) {
        if (mainHandler != null) {
            mainHandler.postDelayed(() -> {
                triggerSensorEvents(classLoader);
                scheduleNextEvent(classLoader);
            }, 1000); // 每秒触发一次
        }
    }

    // 触发传感器事件
    private void triggerSensorEvents(ClassLoader classLoader) {
        if (stepListeners.isEmpty()) {
            Log.d("xposed", "No listeners to trigger");
            return;
        }

        Log.i("xposed", "Triggering sensor events for " + stepListeners.size() + " listeners");

        for (ListenerInfo info : stepListeners) {
            try {
                // 创建一个假的 SensorEvent
                SensorEvent event = createSensorEvent(info.sensor, classLoader);
                if (event != null) {
                    // 调用监听器的 onSensorChanged
                    info.listener.onSensorChanged(event);
                    Log.d("xposed", "Triggered event for sensor type: " + info.sensor.getType());
                }
            } catch (Throwable t) {
                Log.e("xposed", "Error triggering sensor event", t);
            }
        }
    }

    // 创建 SensorEvent 对象
    private SensorEvent createSensorEvent(Sensor sensor, ClassLoader classLoader) {
        try {
            // SensorEvent 没有公共构造函数，需要通过反射创建
            Constructor<SensorEvent> constructor = SensorEvent.class.getDeclaredConstructor(int.class);
            constructor.setAccessible(true);
            SensorEvent event = constructor.newInstance(1); // values 数组大小为 1

            // 设置 sensor 字段
            Field sensorField = SensorEvent.class.getField("sensor");
            sensorField.setAccessible(true);
            sensorField.set(event, sensor);

            // 设置 values 字段
            Field valuesField = SensorEvent.class.getField("values");
            valuesField.setAccessible(true);
            float[] values = (float[]) valuesField.get(event);

            // 根据传感器类型设置值
            if (sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                // TYPE_STEP_COUNTER: 提供设备重启以来的步数
                values[0] = getStep();
                Log.w("xposed", "newStep= " + values[0] + " from TYPE_STEP_COUNTER");

            } else if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                // TYPE_STEP_DETECTOR: 每次检测到步伐触发，值总是 1.0
                values[0] = getStep();
                Log.w("xposed", "newStep= " + values[0] + " from TYPE_STEP_DETECTOR");
            }

            // 设置 timestamp
            Field timestampField = SensorEvent.class.getField("timestamp");
            timestampField.setAccessible(true);
            timestampField.setLong(event, System.nanoTime());

            // 设置 accuracy
            Field accuracyField = SensorEvent.class.getField("accuracy");
            accuracyField.setAccessible(true);
            accuracyField.setInt(event, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);

            return event;
        } catch (Throwable t) {
            Log.e("xposed", "Error creating SensorEvent", t);
            return null;
        }
    }
}