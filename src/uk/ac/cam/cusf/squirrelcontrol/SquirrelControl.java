/*
 * 
 * - Status SMS messages? / Bluetooth?
 * - File writing check, IOExceptions (just for SquirrelCamera now, done for rest)
 * 
 */

package uk.ac.cam.cusf.squirrelcontrol;

import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class SquirrelControl extends Activity {

    public final static String TAG = "SquirrelControl";

    public final static String RADIO_SERVICE = "uk.ac.cam.cusf.squirrelradio.RadioService";
    public final static String RADIO_ACTION = "uk.ac.cam.cusf.squirrelradio.RADIO_SERVICE";

    public final static String LOG_SERVICE = "uk.ac.cam.cusf.squirrellog.LogService";
    public final static String LOG_ACTION = "uk.ac.cam.cusf.squirrellog.LOG_SERVICE";

    public final static String CAMERA_ACTIVITY = "uk.ac.cam.cusf.squirrelcamera.SquirrelCamera";
    public final static String CAMERA_ACTION = "uk.ac.cam.cusf.squirrelcamera.CAMERA_ACTIVITY";

    private final static String AUTO_START = "uk.ac.cam.cusf.squirrelcamera.AUTO_START";

    private final static int DELAY = 3000;

    private final static int N = 3;

    public static HashMap<String, Boolean> enabledServices = new HashMap<String, Boolean>();
    public static boolean cameraStarted = false;

    private TextView status;
    private Button action[] = new Button[N];
    private CheckBox confirm[] = new CheckBox[N];

    private Context context;
    private Handler handler;

    private Runnable timeoutRunnable[] = new Runnable[N];
    private Runnable actionRunnable[] = new Runnable[N];

    private Runnable cameraRunnable;
    private int cameraRestart = -1;

    private Runnable serviceChecker;

    private SharedPreferences sharedPreferences;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Only let the screen be turned off if the Power button is pressed
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        context = this;

        handler = new Handler();

        sharedPreferences = getPreferences(Context.MODE_PRIVATE);

        status = (TextView) findViewById(R.id.textView4);

        action[0] = (Button) findViewById(R.id.Button01);
        confirm[0] = (CheckBox) findViewById(R.id.CheckBox01);
        actionRunnable[0] = actionRunnable(0, LOG_SERVICE, LOG_ACTION);

        action[1] = (Button) findViewById(R.id.Button02);
        confirm[1] = (CheckBox) findViewById(R.id.CheckBox02);
        actionRunnable[1] = actionRunnable(1, RADIO_SERVICE, RADIO_ACTION);

        action[2] = (Button) findViewById(R.id.Button03);
        confirm[2] = (CheckBox) findViewById(R.id.CheckBox03);
        actionRunnable[2] = new Runnable() {
            @Override
            public void run() {
                if (cameraStarted) {
                    cameraStarted = false;
                    sharedPreferences.edit().putBoolean(CAMERA_ACTIVITY, false)
                            .commit();
                    cameraRestart = -1;
                    handler.removeCallbacks(cameraRunnable);
                    status.setText("");
                    action[2].setText("Start");
                } else {
                    cameraStarted = true;
                    sharedPreferences.edit().putBoolean(CAMERA_ACTIVITY, true)
                            .commit();
                    action[2].setText("Stop");
                    startCamera();
                }
            }
        };

        PackageManager pm = getPackageManager();
        try {
            pm.getApplicationInfo("uk.ac.cam.cusf.squirrellog", 0);
        } catch (NameNotFoundException e) {
            confirm[0].setEnabled(false);
        }
        try {
            pm.getApplicationInfo("uk.ac.cam.cusf.squirrelradio", 0);
        } catch (NameNotFoundException e) {
            confirm[1].setEnabled(false);
        }
        try {
            pm.getApplicationInfo("uk.ac.cam.cusf.squirrelcamera", 0);
        } catch (NameNotFoundException e) {
            confirm[2].setEnabled(false);
        }
        try {
            pm.getApplicationInfo("uk.ac.cam.cusf.squirrelsms", 0);
        } catch (NameNotFoundException e) {
            new AlertDialog.Builder(this).setTitle("SquirrelSMS").setMessage(
                    "The package uk.ac.cam.cusf.squirrelsms was not found!")
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                }
                            }).show();
        }

        cameraRunnable = new Runnable() {
            @Override
            public void run() {
                if (cameraRestart < 0) {
                    status.setText("");
                } else if (cameraRestart == 0) {
                    cameraRestart = -1;
                    status.setText("Restarting Squirrel Camera...");
                    startCamera();
                } else {
                    status.setText("Restarting Squirrel Camera in "
                            + cameraRestart + " seconds");
                    cameraRestart--;
                    handler.postDelayed(this, 1000);
                }
            }
        };

        enabledServices.put(LOG_SERVICE, Monitoring.isServiceRunning(
                LOG_SERVICE, context)
                || sharedPreferences.getBoolean(LOG_SERVICE, false));
        enabledServices.put(RADIO_SERVICE, Monitoring.isServiceRunning(
                RADIO_SERVICE, context)
                || sharedPreferences.getBoolean(RADIO_SERVICE, false));
        action[0]
                .setText(enabledServices.get(LOG_SERVICE) == Boolean.TRUE ? "Stop"
                        : "Start");
        action[1]
                .setText(enabledServices.get(RADIO_SERVICE) == Boolean.TRUE ? "Stop"
                        : "Start");

        cameraStarted = sharedPreferences.getBoolean(CAMERA_ACTIVITY, false);
        action[2].setText(cameraStarted ? "Stop" : "Start");

        for (int i = 0; i < N; i++) {
            action[i].setEnabled(false);
            timeoutRunnable[i] = timeoutRunnable(i);
            confirm[i].setOnCheckedChangeListener(checkListener(i));
            action[i].setOnClickListener(clickListener(i));
        }

        serviceChecker = new Runnable() {
            @Override
            public void run() {
                if (!Monitoring.isServiceRunning(
                        MonitorService.class.getName(), context)) {
                    Log.i(TAG, "MonitorService not running - launching now...");
                    Intent intent = new Intent(context, MonitorService.class);
                    startService(intent);
                }
                handler.postDelayed(this, 30000);
            }
        };

    }

    private Runnable actionRunnable(final int i, final String service,
            final String intentAction) {
        return new Runnable() {
            @Override
            public void run() {
                if (enabledServices.get(service) == Boolean.TRUE) {
                    enabledServices.put(service, false);
                    sharedPreferences.edit().putBoolean(service, false)
                            .commit();
                    stopService(intentAction);
                    action[i].setText("Start");
                } else {
                    startService(intentAction);
                    enabledServices.put(service, true);
                    sharedPreferences.edit().putBoolean(service, true).commit();
                    action[i].setText("Stop");
                }
            }
        };
    }

    private OnCheckedChangeListener checkListener(final int i) {
        return new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                if (isChecked) {
                    action[i].setEnabled(true);
                    handler.postDelayed(timeoutRunnable[i], DELAY);
                } else {
                    handler.removeCallbacks(timeoutRunnable[i]);
                    action[i].setEnabled(false);
                }
            }
        };
    }

    private OnClickListener clickListener(final int i) {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!confirm[i].isChecked())
                    return;
                handler.removeCallbacks(timeoutRunnable[0]);
                action[i].setEnabled(false);
                confirm[i].setChecked(false);
                actionRunnable[i].run();
            }
        };
    }

    private Runnable timeoutRunnable(final int i) {
        return new Runnable() {
            @Override
            public void run() {
                confirm[i].setChecked(false);
            }
        };
    }

    private void startCamera() {
        Intent intent = new Intent(CAMERA_ACTION);
        intent.putExtra(AUTO_START, true);
        startActivity(intent);
    }

    private void restartCamera() {
        Log.i(TAG, "restartCamera() " + cameraRestart);
        handler.removeCallbacks(cameraRunnable);
        cameraRestart = 10;
        handler.post(cameraRunnable);
    }

    private void startService(String action) {
        Intent intent = new Intent(action);
        startService(intent);
    }

    private void stopService(String action) {
        Intent intent = new Intent(action);
        stopService(intent);
    }

    @Override
    public void onStop() {

        super.onStop();

        handler.removeCallbacks(serviceChecker);

        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
    }

    @Override
    public void onPause() {

        super.onPause();
        handler.removeCallbacks(cameraRunnable);
        cameraRestart = -1;

    }

    @Override
    public void onStart() {

        super.onStart();

        wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

    }

    @Override
    public void onResume() {

        super.onResume();
        handler.post(serviceChecker);
        status.setText("");
        if (cameraStarted && cameraRestart == -1) {
            restartCamera();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Do nothing
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

}