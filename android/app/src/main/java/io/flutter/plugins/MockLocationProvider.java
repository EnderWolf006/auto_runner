package io.flutter.plugins;

import android.annotation.TargetApi;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.Random;

public class MockLocationProvider {
    private static String providerName;
    private static String networkName;
    private static Context ctx;
    private static boolean inited = false;
    private static LocationManager lm = null;


    public static void initMockLocationProvider(Context ctx) {
        MockLocationProvider.providerName = LocationManager.GPS_PROVIDER;
        MockLocationProvider.networkName = LocationManager.NETWORK_PROVIDER;
        MockLocationProvider.ctx = ctx;



    }
    private static void init(){
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        lm.addTestProvider(providerName, false, true, true, true, true, true, true, ProviderProperties.POWER_USAGE_LOW,ProviderProperties.ACCURACY_FINE);
        lm.setTestProviderEnabled(providerName, true);
        lm.setTestProviderStatus(
                providerName,
                LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
        );
        lm.addTestProvider(networkName, false, true, true, true, true, true, true, ProviderProperties.POWER_USAGE_LOW,ProviderProperties.ACCURACY_FINE);
        lm.setTestProviderEnabled(networkName, true);
        lm.setTestProviderStatus(
                networkName,
                LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
        );
        inited = true;
    }

    public static void pushLocation(double lat, double lon) {
        if (!inited) {
            init();
        }
        Bundle bundle = new Bundle();
        bundle.putInt("satellites", 7);

        Location mockLocation = new Location(providerName);
        mockLocation.setLatitude(lat);
        mockLocation.setLongitude(lon);
        mockLocation.setAltitude(Math.random() * 100);
        mockLocation.setTime(System.currentTimeMillis());
        mockLocation.setAccuracy(1.0f);
        mockLocation.setBearing(new Random().nextInt(360) * 1.0f);
        mockLocation.setSpeed(0F);
        mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mockLocation.setExtras(bundle);
        lm.setTestProviderLocation(providerName, mockLocation);

        Location mockLocation2 = new Location(networkName);
        mockLocation2.setLatitude(lat);
        mockLocation2.setLongitude(lon);
        mockLocation2.setAltitude(Math.random() * 100);
        mockLocation2.setTime(System.currentTimeMillis());
        mockLocation2.setAccuracy(1.0f);
        mockLocation2.setBearing(new Random().nextInt(360) * 1.0f);
        mockLocation2.setSpeed(0F);
        mockLocation2.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mockLocation2.setExtras(bundle);
        lm.setTestProviderLocation(networkName, mockLocation2);
        lm.setTestProviderLocation(networkName, mockLocation);


    }

    public static void shutdown() {
        if (lm != null) {
            lm.removeTestProvider(providerName);
            lm.removeTestProvider(networkName);
            lm.setTestProviderEnabled(providerName, false);
            lm.setTestProviderEnabled(networkName, false);
        }
    }
}