package me.ambassator.phonepointer.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import me.ambassator.phonepointer.util.DebugLogger;

/**
 * Simple in-app viewer for the phonepointer.log file (no adb needed).
 */
public class LogViewerActivity extends AppCompatActivity {

    private TextView logView;
    private TextView pathView;
    private File logFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(32, 32, 32, 32);

        pathView = new TextView(this);
        pathView.setTextColor(Color.WHITE);
        pathView.setTextSize(14f);
        pathView.setText("Log file: " + DebugLogger.getLogPath());
        root.addView(pathView);

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> loadLog());

        Button clear = new Button(this);
        clear.setText("Clear log");
        clear.setOnClickListener(v -> clearLog());

        Button export = new Button(this);
        export.setText("Export");
        export.setOnClickListener(v -> exportLog());

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.START);
        buttonRow.addView(refresh);
        buttonRow.addView(clear);
        buttonRow.addView(export);
        root.addView(buttonRow);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#DDDDDD"));
        logView.setTextSize(13f);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        scrollParams.topMargin = 16;
        root.addView(scroll, scrollParams);

        setContentView(root);
        logFile = new File(getFilesDir(), "phonepointer.log");
        loadLog();
    }

    private void loadLog() {
        try {
            if (logFile == null || !logFile.exists()) {
                logView.setText("No log file yet.");
                return;
            }
            byte[] data = Files.readAllBytes(logFile.toPath());
            logView.setText(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logView.setText("Failed to read log: " + e.getMessage());
        }
    }

    private void clearLog() {
        try {
            File f = new File(getFilesDir(), "phonepointer.log");
            if (f.exists()) {
                Files.delete(f.toPath());
            }
            logView.setText("Log cleared.");
        } catch (Exception e) {
            logView.setText("Failed to clear log: " + e.getMessage());
        }
    }

    private void exportLog() {
        try {
            if (logFile == null || !logFile.exists()) {
                logView.setText("No log file to export.");
                return;
            }
            File dest = new File(getExternalFilesDir(null), "phonepointer-export.log");
            try (InputStream in = new FileInputStream(logFile);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            logView.setText("Exported to: " + dest.getAbsolutePath());
        } catch (Exception e) {
            logView.setText("Failed to export: " + e.getMessage());
        }
    }
}
