package org.wentura.getflow;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.wentura.getflow.activities.Activities;
import org.wentura.getflow.database.Activity;
import org.wentura.getflow.database.Database;
import org.wentura.getflow.settings.SettingsActivity;
import org.wentura.getflow.statistics.StatisticsActivity;
import org.wentura.getflow.R;

import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final int AUTO_SHOW_FULL_SCREEN_AFTER = 3000;
    private static final String CHANNEL_ID = "TimerChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int WAKE_LOCK_PERMISSION_REQUEST_CODE = 1001;

    private ImageView workIcon;
    private ImageView breakIcon;
    private TextView timerTextView;
    private Button activityTextView;
    private Database database;
    private boolean isScaleAnimationDone = false;
    private boolean isTimerTextViewActionUpCalled = false;
    private Handler fullScreenHandler = new Handler();
    private Runnable enterFullScreen = () -> Utility.hideSystemUI(getWindow());
    private ImageButton menuButton;

    private Timer timer;
    private TimerTask timerTask;
    private Handler handler;

    private boolean isWorking = true;
    private boolean isPaused = false;
    private long timeRemaining = 25 * 60 * 1000; // 25 minutes in milliseconds

    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra(Constants.UPDATE_UI_ACTION);

            if (action == null) {
                return;
            }

            switch (action) {
                case Constants.BUTTON_SKIP:
                case Constants.BUTTON_START: {
                    SharedPreferences sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                    boolean isBreakState = sharedPreferences.getBoolean(Constants.IS_BREAK_STATE, false);

                    if (isBreakState) {
                        workIcon.setVisibility(View.INVISIBLE);
                        breakIcon.setVisibility(View.VISIBLE);
                    } else {
                        workIcon.setVisibility(View.VISIBLE);
                        breakIcon.setVisibility(View.INVISIBLE);
                    }

                    break;
                }
                case Constants.BUTTON_STOP:
                    stopTimerUI();
                    break;
                case Constants.BUTTON_PAUSE:
                    startBlinkingAnimation();
                    break;
            }
        }
    };

    private final BroadcastReceiver updateTimerTextView = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            if (sharedPreferences.getBoolean(Constants.IS_STOP_BUTTON_VISIBLE, false)) {
                updateTimerTextView(intent.getIntExtra(Constants.TIME_LEFT_INTENT, 0));
            }
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPreferences.getBoolean(Constants.FULL_SCREEN_MODE, false)) {
            if (hasFocus) {
                Utility.hideSystemUI(getWindow());
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        if (event.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPreferences.getBoolean(Constants.FULL_SCREEN_MODE, false)) {
            View decorView = getWindow().getDecorView();
            boolean isFullScreenOff = (decorView.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;

            if (isFullScreenOff) {
                Utility.showSystemUI(getWindow());

                fullScreenHandler.removeCallbacks(enterFullScreen);
                fullScreenHandler.postDelayed(enterFullScreen, AUTO_SHOW_FULL_SCREEN_AFTER);
            } else {
                Utility.hideSystemUI(getWindow());
            }
        }

        return false;
    }

// ... (continued in the next message due to length constraints)
@Override
protected void onResume() {
    super.onResume();

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (sharedPreferences.getBoolean(Constants.FULL_SCREEN_MODE, false)) {
        Utility.hideSystemUI(getWindow());
    }

    setupUI();

    Utility.toggleKeepScreenOn(this);

    LocalBroadcastManager.getInstance(this).registerReceiver(
            updateTimerTextView, new IntentFilter(Constants.ON_TICK));

    LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver, new IntentFilter(Constants.UPDATE_UI));
}

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (sharedPreferences.getInt(Constants.TIME_LEFT, 0) == 0) {
            finish();
        } else {
            moveTaskToBack(true);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerTextView = findViewById(R.id.countdown_text_view);
        activityTextView = findViewById(R.id.current_activity);
        workIcon = findViewById(R.id.work_icon);
        breakIcon = findViewById(R.id.break_icon);
        menuButton = findViewById(R.id.menu);

        setupNotificationChannels();

        database = Database.getInstance(this);

        ActionBar actionbar = getSupportActionBar();
        if (actionbar != null) {
            actionbar.hide();
        }

        activityTextView.setOnClickListener(view -> {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

            if (preferences.getBoolean(Constants.FULL_SCREEN_MODE, false)) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                    Utility.showSystemUI(getWindow());
                }

                fullScreenHandler.removeCallbacks(enterFullScreen);
                Utility.hideSystemUI(getWindow());
            }

            startActivity(new Intent(this, Activities.class));
        });

        timerTextView.setOnTouchListener(new OnTouchListener(this) {
            @Override
            public void onUp() {
                isTimerTextViewActionUpCalled = true;

                if (isScaleAnimationDone) {
                    revertTimerAnimation();
                    isScaleAnimationDone = false;
                }
            }

            @Override
            public void onDown() {
                isTimerTextViewActionUpCalled = false;
                AnimatorSet animatorSet = startTimerAnimation();

                animatorSet.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {}

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isScaleAnimationDone = true;

                        if (isTimerTextViewActionUpCalled) {
                            revertTimerAnimation();
                            isTimerTextViewActionUpCalled = false;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {}

                    @Override
                    public void onAnimationRepeat(Animator animation) {}
                });
            }

            @Override
            public void onTap() {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                if (sharedPreferences.getBoolean(Constants.IS_TIMER_RUNNING, false)) {
                    pauseTimer();
                } else {
                    startTimer();
                }
            }

            @Override
            public void onMyLongPress() {
                stopTimer();
                isScaleAnimationDone = true;
            }

            @Override
            public void onSwipeLeft() {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                if (sharedPreferences.getInt(Constants.TIME_LEFT, 0) != 0 ||
                        sharedPreferences.getBoolean(Constants.IS_BREAK_STATE, false)) {
                    skipTimer();
                }
            }
        });

        menuButton.setOnClickListener(view -> {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.FULL_SCREEN_MODE, false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            PopupMenu popup = new PopupMenu(MainActivity.this, menuButton);
            popup.getMenuInflater().inflate(R.menu.menu, popup.getMenu());  // Changed to R.menu.menu

            popup.setOnMenuItemClickListener(item -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

                int itemId = item.getItemId();
                if (itemId == R.id.settings) {
                    startSettingsActivity();
                    return true;
                } else if (itemId == R.id.statistics) {
                    startStatisticsActivity();
                    return true;
                } else if (itemId == R.id.scheduled_blocking) {
                    startActivity(new Intent(MainActivity.this, ScheduledBlockingActivity.class));
                    return true;
                } else if (itemId == R.id.about) {
                    startAboutActivity();
                    return true;
                }
                return false;
            });

            popup.show();
        });

        showHelpingSnackbars();

        showIgnoreBatteryOptimizationDialog();

        handler = new Handler();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GetFlow::WakeLock");

        createNotificationChannel();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED) {
            acquireWakeLock();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WAKE_LOCK}, WAKE_LOCK_PERMISSION_REQUEST_CODE);
        }
    }

// ... (continued in the next message due to length constraints)
private void showIgnoreBatteryOptimizationDialog() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return;
    }

    String packageName = getPackageName();
    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    boolean isNeverShowIgnoreBatteryOptimizationDialog =
            preferences.getBoolean(Constants.NEVER_SHOW_IGNORE_BATTERY_OPTIMIZATION_DIALOG, false);

    if (powerManager == null ||
            powerManager.isIgnoringBatteryOptimizations(packageName) ||
            isNeverShowIgnoreBatteryOptimizationDialog) {
        return;
    }

    @SuppressLint("BatteryLife")
    MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ignore_battery_optimization_title)
            .setMessage(R.string.ignore_battery_optimization_message)
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.never_show_neutral_button, (dialog, which) -> {
                SharedPreferences.Editor preferenceEditor = preferences.edit();
                preferenceEditor.putBoolean(Constants.NEVER_SHOW_IGNORE_BATTERY_OPTIMIZATION_DIALOG, true);
                preferenceEditor.apply();
            });

    dialogBuilder.show();
}

    private void showHelpingSnackbars() {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this);

        final int currentStep = sharedPreferences.getInt(Constants.TUTORIAL_STEP, 0);

        List<String> messages = Arrays.asList(
                getString(R.string.press_on_timer_snackbar_message),
                getString(R.string.swipe_timer_snackbar_message),
                getString(R.string.long_press_on_timer_snackbar_message));

        if (currentStep >= messages.size()) {
            return;
        }

        final SharedPreferences.Editor editPreferences =
                sharedPreferences.edit();

        final Snackbar snackbar = Snackbar.make(findViewById(R.id.main_activity), messages.get(currentStep),
                Snackbar.LENGTH_INDEFINITE);

        snackbar.setAction(getString(R.string.OK), view -> {
            snackbar.dismiss();

            editPreferences.putInt(Constants.TUTORIAL_STEP, currentStep + 1).apply();
            showHelpingSnackbars();
        });

        snackbar.setTextColor(Color.WHITE);
        snackbar.getView().setBackgroundColor(getResources().getColor(R.color.dark_grey));
        snackbar.setActionTextColor(getResources().getColor(R.color.colorPrimary));
        snackbar.show();
    }

    private AnimatorSet startTimerAnimation() {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(timerTextView,
                "scaleX", 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(timerTextView,
                "scaleY", 0.95f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleDownX).with(scaleDownY);
        animatorSet.start();
        return animatorSet;
    }

    private void revertTimerAnimation() {
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(
                timerTextView, "scaleX", 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(
                timerTextView, "scaleY", 1f);
        scaleUpX.setDuration(100);
        scaleUpY.setDuration(100);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(scaleUpX).with(scaleUpY);
        animatorSet.start();
    }

    @Override
    protected void onPause() {
        super.onPause();

        fullScreenHandler.removeCallbacks(enterFullScreen);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateTimerTextView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void startSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void startStatisticsActivity() {
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    private void startAboutActivity() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    private void setupUI() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isBreakState = sharedPreferences.getBoolean(Constants.IS_BREAK_STATE, false);
        int workSessionCounter = sharedPreferences.getInt(Constants.WORK_SESSION_COUNTER, 0);
        int timeLeft = sharedPreferences.getInt(Constants.TIME_LEFT, 0);
        int activityId = sharedPreferences.getInt(Constants.CURRENT_ACTIVITY_ID, 1);

        Database.databaseExecutor.execute(() -> {
            int numberOfActivities = database.activityDao().getNumberOfActivities();

            if (numberOfActivities < 1) {
                database.activityDao().insertActivity(new Activity(getString(R.string.default_activity_name)));
            }

            String activityName = database.activityDao().getName(activityId);
            runOnUiThread(() -> activityTextView.setText(activityName));

            boolean areLongBreaksEnabled = database.activityDao().areLongBreaksEnabled(activityId);

            int duration;

            if (timeLeft == 0) {
                if (isBreakState) {
                    if (workSessionCounter != 0 && workSessionCounter % 4 == 0 && areLongBreaksEnabled) {
                        duration = database.activityDao().getLongBreakDuration(activityId);
                    } else {
                        duration = database.activityDao().getBreakDuration(activityId);
                    }
                } else {
                    duration = database.activityDao().getWorkDuration(activityId);
                }
                runOnUiThread(() -> updateTimerTextView(duration * 60_000));
            } else {
                runOnUiThread(() -> updateTimerTextView(timeLeft));
            }
        });

        if (sharedPreferences.getBoolean(Constants.IS_WORK_ICON_VISIBLE, true)) {
            workIcon.setVisibility(View.VISIBLE);
        } else {
            workIcon.setVisibility(View.INVISIBLE);
        }

        if (sharedPreferences.getBoolean(Constants.IS_BREAK_ICON_VISIBLE, false)) {
            breakIcon.setVisibility(View.VISIBLE);
        } else {
            breakIcon.setVisibility(View.INVISIBLE);
        }

        if (sharedPreferences.getBoolean(Constants.IS_TIMER_BLINKING, false)) {
            startBlinkingAnimation();
        }
    }

// ... (continued in the next message due to length constraints)
private void stopTimer() {
    Intent stopIntent = new Intent(this, TimerActionReceiver.class);
    stopIntent.putExtra(Constants.BUTTON_ACTION, Constants.BUTTON_STOP);
    stopIntent.putExtra(Constants.CURRENT_ACTIVITY_ID_INTENT, getCurrentActivityId());
    sendBroadcast(stopIntent);
}

    private void stopTimerUI() {
        workIcon.setVisibility(View.VISIBLE);
        breakIcon.setVisibility(View.INVISIBLE);

        revertTimerAnimation();
        timerTextView.clearAnimation();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        Database.databaseExecutor.execute(() -> {
            int duration =
                    database.activityDao().getWorkDuration(sharedPreferences.getInt(Constants.CURRENT_ACTIVITY_ID, 1));

            runOnUiThread(() -> updateTimerTextView(duration * 60_000));
        });
    }

    private void skipTimer() {
        revertTimerAnimation();
        timerTextView.clearAnimation();

        Intent intent = new Intent(this, TimerActionReceiver.class);
        intent.putExtra(Constants.BUTTON_ACTION, Constants.BUTTON_SKIP);
        intent.putExtra(Constants.CURRENT_ACTIVITY_ID_INTENT, getCurrentActivityId());
        sendBroadcast(intent);
    }

    private int getCurrentActivityId() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getInt(Constants.CURRENT_ACTIVITY_ID, 1);
    }

    private void startTimer() {
        timerTextView.clearAnimation();

        Intent intent = new Intent(this, TimerActionReceiver.class);
        intent.putExtra(Constants.BUTTON_ACTION, Constants.BUTTON_START);
        intent.putExtra(Constants.CURRENT_ACTIVITY_ID_INTENT, getCurrentActivityId());
        sendBroadcast(intent);
    }

    private void pauseTimer() {
        Intent intent = new Intent(this, TimerActionReceiver.class);
        intent.putExtra(Constants.BUTTON_ACTION, Constants.BUTTON_PAUSE);
        intent.putExtra(Constants.CURRENT_ACTIVITY_ID_INTENT, getCurrentActivityId());
        sendBroadcast(intent);
    }

    private void startBlinkingAnimation() {
        Animation blinkingAnimation = new AlphaAnimation(1.0f, 0.5f);
        blinkingAnimation.setDuration(1000);
        blinkingAnimation.setRepeatMode(Animation.REVERSE);
        blinkingAnimation.setRepeatCount(Animation.INFINITE);
        timerTextView.startAnimation(blinkingAnimation);
    }

    private void updateTimerTextView(long time) {
        timerTextView.setText(Utility.formatTime(time));
    }

    private void setupNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (notificationManager == null) {
            return;
        }

        NotificationChannel timerCompletedChannel = new NotificationChannel(Constants.CHANNEL_TIMER_COMPLETED,
                Constants.CHANNEL_TIMER_COMPLETED, NotificationManager.IMPORTANCE_HIGH);

        NotificationChannel timerChannel = new NotificationChannel(Constants.CHANNEL_TIMER,
                Constants.CHANNEL_TIMER, NotificationManager.IMPORTANCE_LOW);

        timerCompletedChannel.setShowBadge(false);
        timerCompletedChannel.enableLights(true);
        timerCompletedChannel.setBypassDnd(true);

        timerChannel.setShowBadge(false);

        notificationManager.createNotificationChannel(timerCompletedChannel);
        notificationManager.createNotificationChannel(timerChannel);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WAKE_LOCK_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                acquireWakeLock();
            } else {
                // Permission denied, handle accordingly
                Toast.makeText(this, "Wake lock permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK)
                    == PackageManager.PERMISSION_GRANTED) {
                try {
                    wakeLock.acquire();
                } catch (SecurityException e) {
                    e.printStackTrace();
                    // Handle the exception (e.g., request the permission)
                }
            } else {
                // Request the WAKE_LOCK permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WAKE_LOCK},
                        WAKE_LOCK_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void sendNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("GetFlow")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Timer Channel";
            String description = "Channel for Timer notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}