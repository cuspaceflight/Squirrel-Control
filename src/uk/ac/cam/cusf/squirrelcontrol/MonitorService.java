package uk.ac.cam.cusf.squirrelcontrol;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class MonitorService extends Service {

	public final static String TAG = "SquirrelControl";
	
	private final static long DELAY = 30000;
	
	ActivityManager activityManager;
	HashMap<String,String> serviceMonitor;
	
	Handler handler;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
    @Override
    public void onCreate() {
    	activityManager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
    	
    	handler = new Handler();
    	
    	serviceMonitor = new HashMap<String,String>();
    	serviceMonitor.put(SquirrelControl.LOG_SERVICE, SquirrelControl.LOG_ACTION);
    	serviceMonitor.put(SquirrelControl.RADIO_SERVICE, SquirrelControl.RADIO_ACTION);
    	
    };
    
    public int onStartCommand(Intent intent, int flags, int startId) {
    	
    	Runnable checker = new Runnable() {
			@Override
			public void run() {
				check();
				handler.postDelayed(this, DELAY);
			}
    	};
    	handler.removeCallbacks(checker); // In case the Service is started while it is already running
    	handler.postDelayed(checker, DELAY);
    	
    	return START_STICKY;
    }
    
    
    private void check() {
    	

    	HashMap<String,String> map = copyHashMap(serviceMonitor);

    	List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);
		for (RunningServiceInfo runningService : services) {
			if (map.containsKey(runningService.service.getClassName())) {
				map.remove(runningService.service.getClassName());
			}
		}
		for (String key: map.keySet()) {
			Boolean enabled = SquirrelControl.enabledServices.get(key);
			if (enabled != null && enabled == true) {
				Log.i(TAG, "Restarting service! ("+key+")");
				Intent intent = new Intent(map.get(key));
		        startService(intent);
			}
		}
    	
    }
    
    
    // Instead of using clone(), which leads to Eclipse warnings...
    public static HashMap<String,String> copyHashMap(HashMap<String,String> map) {
    	HashMap<String,String> copy = new HashMap<String,String>();
    	Iterator<Entry<String, String>> it = map.entrySet().iterator();
    	Entry<String,String> pairs;
    	while (it.hasNext()) {
            pairs = it.next();
            copy.put(pairs.getKey(), pairs.getValue());            
        }
    	return copy;
    }
    
	@Override
	public void onDestroy() {
    	super.onDestroy();
    	
	}

}
