package org.wentura.getflow;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import java.util.Calendar;

public class ScheduledBlockingActivity extends AppCompatActivity {

    private TimePicker timePicker;
    private CalendarView calendarView;
    private Button saveButton;
    private ImageButton addActivityButton;
    private TextView activityNameView;
    private long selectedDate;
    private String activityName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_blocking);

        timePicker = findViewById(R.id.time_picker);
        calendarView = findViewById(R.id.calendar_view);
        saveButton = findViewById(R.id.save_button);
        addActivityButton = findViewById(R.id.add_activity_button);
        activityNameView = findViewById(R.id.activity_name_view);

        selectedDate = System.currentTimeMillis();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            selectedDate = calendar.getTimeInMillis();
        });

        saveButton.setOnClickListener(view -> saveScheduledBlocking());

        addActivityButton.setOnClickListener(view -> showAddActivityDialog());
    }

    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Activity");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            activityName = input.getText().toString();
            activityNameView.setText("Activity: " + activityName);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveScheduledBlocking() {
        if (activityName.isEmpty()) {
            Toast.makeText(this, "Please add an activity first", Toast.LENGTH_SHORT).show();
            return;
        }

        int hour, minute;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            hour = timePicker.getHour();
            minute = timePicker.getMinute();
        } else {
            hour = timePicker.getCurrentHour();
            minute = timePicker.getCurrentMinute();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDate);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long scheduledTime = calendar.getTimeInMillis();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("scheduled_blocking_time", scheduledTime);
        editor.putString("scheduled_activity_name", activityName);
        editor.apply();

        Toast.makeText(this, "Scheduled blocking saved", Toast.LENGTH_SHORT).show();
    }
}