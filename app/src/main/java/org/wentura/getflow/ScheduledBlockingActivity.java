package org.wentura.getflow;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.content.Context;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import android.graphics.Color;

import java.util.Calendar;

public class ScheduledBlockingActivity extends AppCompatActivity {

    private NumberPicker hourPicker, minutePicker, ampmPicker;
    private CalendarView calendarView;
    private Button startButton, endButton, importButton, saveButton;
    private ImageButton addGroupButton;
    private TextView groupNameText;
    private long selectedDate;
    private String groupName = "";
    private boolean isStartTime = true;
    private long startTime, endTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_blocking);

        hourPicker = findViewById(R.id.hour_picker);
        minutePicker = findViewById(R.id.minute_picker);
        ampmPicker = findViewById(R.id.ampm_picker);
        calendarView = findViewById(R.id.calendar_view);
        startButton = findViewById(R.id.start_button);
        endButton = findViewById(R.id.end_button);
        importButton = findViewById(R.id.import_button);
        saveButton = findViewById(R.id.save_button);
        addGroupButton = findViewById(R.id.add_group_button);
        groupNameText = findViewById(R.id.group_name_text);

        setupPickers();

        selectedDate = System.currentTimeMillis();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            selectedDate = calendar.getTimeInMillis();
        });

        startButton.setOnClickListener(v -> {
            isStartTime = true;
            startButton.setBackgroundColor(Color.GREEN);
            endButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        });

        endButton.setOnClickListener(v -> {
            isStartTime = false;
            endButton.setBackgroundColor(Color.GREEN);
            startButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        });

        saveButton.setOnClickListener(v -> saveScheduledBlocking());

        addGroupButton.setOnClickListener(v -> showAddGroupDialog());

        importButton.setOnClickListener(v -> {
            // Implement import functionality
            Toast.makeText(this, "Import functionality to be implemented", Toast.LENGTH_SHORT).show();
        });

        hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateTime());
        minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateTime());
        ampmPicker.setOnValueChangedListener((picker, oldVal, newVal) -> updateTime());
    }

    private void setupPickers() {
        hourPicker.setMinValue(1);
        hourPicker.setMaxValue(12);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setFormatter(value -> String.format("%02d", value));

        ampmPicker.setMinValue(0);
        ampmPicker.setMaxValue(1);
        ampmPicker.setDisplayedValues(new String[]{"AM", "PM"});
    }

    private void showAddGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Group");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            groupName = input.getText().toString();
            groupNameText.setText(groupName);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateTime() {
        long time = getTimeInMillis();
        if (isStartTime) {
            startTime = time;
        } else {
            endTime = time;
        }
    }

    private long getTimeInMillis() {
        int hour = hourPicker.getValue();
        int minute = minutePicker.getValue();
        boolean isPM = ampmPicker.getValue() == 1;

        if (isPM && hour != 12) hour += 12;
        if (!isPM && hour == 12) hour = 0;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDate);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        return calendar.getTimeInMillis();
    }

    private void saveScheduledBlocking() {
        if (groupName.isEmpty()) {
            Toast.makeText(this, "Please create a group first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startTime == 0 || endTime == 0) {
            Toast.makeText(this, "Please select both start and end times", Toast.LENGTH_SHORT).show();
            return;
        }

        if (endTime <= startTime) {
            Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show();
            return;
        }

        long duration = endTime - startTime;

        // Save the scheduled blocking
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(Constants.SCHEDULED_START_TIME, startTime);
        editor.putLong(Constants.SCHEDULED_DURATION, duration);
        editor.putString(Constants.SCHEDULED_GROUP_NAME, groupName);
        editor.apply();

        // Schedule the alarm
        scheduleAlarm(startTime);

        Toast.makeText(this, "Scheduled blocking saved for group: " + groupName, Toast.LENGTH_SHORT).show();

        // Redirect to main screen
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void scheduleAlarm(long startTime) {
        Intent intent = new Intent(this, TimerActionReceiver.class);
        intent.setAction(Constants.ACTION_START_SCHEDULED_BLOCKING);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startTime, pendingIntent);
            }
        }
    }
}