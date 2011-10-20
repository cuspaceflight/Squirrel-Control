package uk.ac.cam.cusf.squirrelcontrol;

import java.util.List;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;

public class Monitoring {

	public static boolean isServiceRunning(String serviceClass, Context context) {
		ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

		for (RunningServiceInfo runningService : services) {
			if (serviceClass.equals(runningService.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

}
