package com.example.youtubeshortsredirector; // Your package name

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log; // Added for logging
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat; // For colors

import com.example.youtubeshortsredirector.service.RedirectShortsService;
import com.google.android.material.materialswitch.MaterialSwitch;


public class MainActivity extends AppCompatActivity {

    private TextView tvServiceStatus;
    private MaterialSwitch switchRedirectionEnabled;
    private Button btnResetServiceFlags;

    public static final String PREFS_NAME = "ShortsRedirectorPrefs";
    public static final String KEY_REDIRECTION_ENABLED = "redirectionEnabled";

    public static final String ACTION_UPDATE_SETTINGS = "com.example.youtubeshortsredirector.ACTION_UPDATE_SETTINGS";
    public static final String ACTION_RESET_FLAGS_COMMAND = "com.example.youtubeshortsredirector.ACTION_RESET_FLAGS_COMMAND";

    private static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvServiceStatus = findViewById(R.id.tvServiceStatus);
        Button btnOpenAccessibilitySettings = findViewById(R.id.btnOpenAccessibilitySettings);
        switchRedirectionEnabled = findViewById(R.id.switchRedirectionEnabled);
        btnResetServiceFlags = findViewById(R.id.btnResetServiceFlags);

        btnOpenAccessibilitySettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isRedirectionGloballyEnabled = prefs.getBoolean(KEY_REDIRECTION_ENABLED, true); // Default to true
        switchRedirectionEnabled.setChecked(isRedirectionGloballyEnabled);

        switchRedirectionEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_REDIRECTION_ENABLED, isChecked);
            editor.apply();
            Log.d(TAG, "Redirection enabled toggled: " + isChecked);

            Intent intent = new Intent(ACTION_UPDATE_SETTINGS);
            intent.putExtra(KEY_REDIRECTION_ENABLED, isChecked);
            intent.setPackage(getPackageName()); // Good practice for explicit broadcast
            sendBroadcast(intent);
            Toast.makeText(this, "Redirection Logic " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        btnResetServiceFlags.setOnClickListener(v -> {
            Log.d(TAG, "Reset Service Flags button clicked.");
            Intent intent = new Intent(ACTION_RESET_FLAGS_COMMAND);
            intent.setPackage(getPackageName()); // Good practice
            sendBroadcast(intent);
            Toast.makeText(this, "Sent reset command to service", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
    }

    private void updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, RedirectShortsService.class)) {
            tvServiceStatus.setText(R.string.status_service_enabled);
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            switchRedirectionEnabled.setEnabled(true);
            btnResetServiceFlags.setEnabled(true);
        } else {
            tvServiceStatus.setText(R.string.status_service_disabled);
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            switchRedirectionEnabled.setEnabled(false);
            btnResetServiceFlags.setEnabled(false);
        }
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityServiceClass) {
        ComponentName expectedComponentName = new ComponentName(context, accessibilityServiceClass);
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName))
                return true;
        }
        return false;
    }
}