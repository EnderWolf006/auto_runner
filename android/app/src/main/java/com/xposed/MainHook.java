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
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
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
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainHook implements IXposedHookLoadPackage {
    Context ctx;
    public static Map<String, String> sensorData = null;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences("com.example.auto_runner", path);
        return pref.getFile().canRead() ? pref : null;
    }

    // 随机小数
    public static float randomFloat(double min, double max) {
        return (float) (Math.random() * (max - min) + min);
    }

    Map<Integer, Map<String, Float>[]> dataBoundary = new HashMap<>();


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        Log.i("xposed", "handleLoadPackage: " + loadPackageParam.packageName);
//        if (loadPackageParam.packageName.equals("com.bxkj.student")) return;
        final Class<?> sensorEL = findClass("android.hardware.SystemSensorManager$SensorEventQueue", loadPackageParam.classLoader);
        XposedBridge.hookAllMethods(sensorEL, "dispatchSensorEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {


                int intValue = (Integer) param.args[0];
                Field declaredField = param.thisObject.getClass().getDeclaredField("mSensorsEvents");
                declaredField.setAccessible(true);
                Sensor sensor = ((SensorEvent) ((SparseArray<?>) declaredField.get(param.thisObject)).get(intValue)).sensor;
                int sensortype = sensor.getType();
                Log.i("xposed", "sensortype: " + sensortype);


//                int arrayLength = 8;
//                float[] floats = (float[]) param.args[1];
//
//                for (int j = 0; j < arrayLength; j++) {
//                    if (dataBoundary.containsKey(sensortype)) {
//                        Map<String, Float>[] maps = dataBoundary.get(sensortype);
//                        if (maps[j].containsKey("min")) {
//                            if (maps[j].get("min") > floats[j]) {
//                                maps[j].put("min", floats[j]);
//                            }
//                        } else {
//                            maps[j].put("min", floats[j]);
//                        }
//
//                        if (maps[j].containsKey("max")) {
//                            if (maps[j].get("max") < floats[j]) {
//                                maps[j].put("max", floats[j]);
//                            }
//                        } else {
//                            maps[j].put("max", floats[j]);
//                        }
//                    } else {
//                        Map<String, Float>[] maps = new Map[arrayLength];
//                        for (int k = 0; k < arrayLength; k++) {
//                            Map<String, Float> map = new HashMap<>();
//                            map.put("min", Float.MAX_VALUE);
//                            map.put("max", Float.MIN_VALUE);
//                            maps[k] = map;
//                        }
//                        Map<String, Float> map = new HashMap<>();
//                        map.put("min", floats[j]);
//                        map.put("max", floats[j]);
//                        maps[j] = map;
//                        dataBoundary.put(sensortype, maps);
//                    }
//                }
//
//                for (Map.Entry<Integer, Map<String, Float>[]> entry : dataBoundary.entrySet()) {
//                    Log.w("xposed", "key = " + entry.getKey());
//                    Map<String, Float>[] value = entry.getValue();
//                    for (int i = 0; i < value.length; i++) {
//                        Log.w("xposed", "value[" + i + "] = " + value[i]);
//
//                    }
//                }


                if (sensortype == 1) {
                    ((float[]) param.args[1])[0] = randomFloat(-0.17896658, -0.17178397);
                    ((float[]) param.args[1])[1] = randomFloat(0.30825347, 0.31603462);
                    ((float[]) param.args[1])[2] = randomFloat(9.804854, 9.838972);

                } else if (sensortype == 3) {
                    ((float[]) param.args[1])[0] = randomFloat(0.1875, 359.73438);
                    ((float[]) param.args[1])[1] = randomFloat(-179.8125, 179.82812);
                    ((float[]) param.args[1])[2] = randomFloat(-88.5625, 87.296875);
                } else if (sensortype == 11) {
                    ((float[]) param.args[1])[0] = randomFloat(-0.79822946, 0.8127403);
                    ((float[]) param.args[1])[1] = randomFloat(-0.6897241, 0.7955777);
                    ((float[]) param.args[1])[2] = randomFloat(-0.91179925, 0.88732475);
                    ((float[]) param.args[1])[3] = randomFloat(0.012713679, 0.99990004);
                    ((float[]) param.args[1])[4] = randomFloat(0.012713679, 0.99990004);
                    ((float[]) param.args[1])[5] = randomFloat(0.012713679, 0.99990004);
                    ((float[]) param.args[1])[6] = randomFloat(0.012713679, 0.99990004);
                    ((float[]) param.args[1])[7] = randomFloat(0.012713679, 0.99990004);
                    ((float[]) param.args[1])[8] = randomFloat(0.012713679, 0.99990004);
                } else if (sensortype == 19 || sensortype == 18) {
                    int nowStep;
                    XSharedPreferences pref = getPref("auto_runner");
                    nowStep = pref.getInt("step", -1);

                    Log.i("xposed", "beforeHookedMethod: ");
                    Log.w("xposed", "nowStep = " + nowStep);
                    float step = ((float[]) param.args[1])[0];
                    Log.w("xposed", "start=" + step);
                    float newStep = step + nowStep;
                    Log.w("xposed", "newStep= " + newStep);
                    ((float[]) param.args[1])[0] = newStep;
                    Log.w("xposed", "end= " + ((float[]) param.args[1])[0]);
                }

            }

        });
    }
}