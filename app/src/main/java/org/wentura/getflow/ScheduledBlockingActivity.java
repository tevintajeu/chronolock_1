package org.wentura.getflow;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.TimePicker;
import androidx.appcompat.app.AppCompatActivity;

public class ScheduledBlockingActivity extends AppCompatActivity {

    private TimePicker timePicker;
    private CalendarView calendarView;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_blocking);

        timePicker = findViewById(R.id.time_picker);
        calendarView = findViewById(R.id.calendar_view);
        saveButton = findViewById(R.id.save_button);

        saveButton.setOnClickListener(view -> {
            // Handle the save action
        });
    }
}
