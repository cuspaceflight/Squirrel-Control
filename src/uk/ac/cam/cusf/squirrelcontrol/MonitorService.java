package uk.ac.cam.cusf.squirrelcontrol;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class MonitorService extends Service {

    public final static String TAG = "SquirrelControl";

    public final static String SMS_RECEIVED = "uk.ac.cam.cusf.intent.SMS_RECEIVED";
    
    private final static long DELAY = 30000;

    ActivityManager activityManager;
    HashMap<String, String> serviceMonitor;

    Handler handler;
    
    BroadcastReceiver aliveListener, commandListener;
    long lastHeard = System.currentTimeMillis();

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        handler = new Handler();

        serviceMonitor = new HashMap<String, String>();
        serviceMonitor.put(SquirrelControl.LOG_SERVICE,
                SquirrelControl.LOG_ACTION);
        serviceMonitor.put(SquirrelControl.RADIO_SERVICE,
                SquirrelControl.RADIO_ACTION);
        
        aliveListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("uk.ac.cam.cusf.intent.ALIVE")) {
                    lastHeard = System.currentTimeMillis();
                    Log.i(TAG, "Alive");
                }
            }
        };

        IntentFilter actionChanged = new IntentFilter();
        actionChanged.addAction("uk.ac.cam.cusf.intent.ALIVE");
        registerReceiver(aliveListener, actionChanged);
        
        commandListener = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String command = intent.getStringExtra("command");
                if (command.equals("start")) {
                    handler.postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            SquirrelControl.enabledServices.put(SquirrelControl.LOG_SERVICE, true);
                            SquirrelControl.enabledServices.put(SquirrelControl.RADIO_SERVICE, true);
                            SquirrelControl.cameraStarted = true;
                        }
                        
                    }, 2000);

                } else if (command.equals("stop")) {
                    SquirrelControl.enabledServices.put(SquirrelControl.LOG_SERVICE, false);
                    SquirrelControl.enabledServices.put(SquirrelControl.RADIO_SERVICE, false);
                    SquirrelControl.cameraStarted = false;
                }
            }
            
        };
        
        IntentFilter command = new IntentFilter();
        command.addAction(SMS_RECEIVED);
        registerReceiver(commandListener, command);

    };

    public int onStartCommand(Intent intent, int flags, int startId) {

        Runnable checker = new Runnable() {
            @Override
            public void run() {
                check();
                handler.postDelayed(this, DELAY);
            }
        };
        handler.removeCallbacks(checker); // In case the Service is started
                                          // while it is already running
        handler.postDelayed(checker, DELAY);

        return START_STICKY;
    }

    private void check() {

        HashMap<String, String> map = copyHashMap(serviceMonitor);

        List<RunningServiceInfo> services = activityManager
                .getRunningServices(Integer.MAX_VALUE);
        for (RunningServiceInfo runningService : services) {
            if (map.containsKey(runningService.service.getClassName())) {
                map.remove(runningService.service.getClassName());
            }
        }
        for (String key : map.keySet()) {
            Boolean enabled = SquirrelControl.enabledServices.get(key);
            if (enabled != null && enabled == true) {
                Log.i(TAG, "Restarting service! (" + key + ")");
                Intent intent = new Intent(map.get(key));
                startService(intent);
            }
        }
        
        if (System.currentTimeMillis() - lastHeard > 5*60000 && SquirrelControl.enabledServices.get(SquirrelControl.RADIO_SERVICE)) {
            try {
                Log.i(TAG, "Rebooting");
                Runtime.getRuntime().exec(new String[]{"/system/bin/su","-c","reboot now"});
            } catch (IOException e) {
                Log.i(TAG, "Failed to reboot");
            }
        }

    }

    // Instead of using clone(), which leads to Eclipse warnings...
    public static HashMap<String, String> copyHashMap(
            HashMap<String, String> map) {
        HashMap<String, String> copy = new HashMap<String, String>();
        Iterator<Entry<String, String>> it = map.entrySet().iterator();
        Entry<String, String> pairs;
        while (it.hasNext()) {
            pairs = it.next();
            copy.put(pairs.getKey(), pairs.getValue());
        }
        return copy;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(aliveListener);
        unregisterReceiver(commandListener);
    }

}
