package com.example.youtubeshortsredirector;

import android.app.Activity;
import android.content.ClipData; // Keep for creating empty ClipData
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.example.youtubeshortsredirector.service.RedirectShortsService;

public class ClipboardAccessActivity extends Activity {
    private static final String TAG_ACTIVITY = "ClipboardAccessActivity";
    private ClipboardManager clipboardManager;
    private boolean clipboardReadAttempted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG_ACTIVITY, "onCreate");
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // Optional: setContentView(R.layout.your_transparent_layout_for_clipboard_activity);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG_ACTIVITY, "onResume - Activity is now in focus.");

        if (!clipboardReadAttempted && hasWindowFocus()) {
            clipboardReadAttempted = true;
            Log.d(TAG_ACTIVITY, "Attempting to read clipboard as activity is in focus.");
            String url = null; // Store URL to use after clearing clipboard
            boolean readSuccess = false;

            if (clipboardManager.hasPrimaryClip()) { // Check if there's something to get
                ClipData clip = clipboardManager.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) {
                        url = text.toString();
                        Log.i(TAG_ACTIVITY, "Successfully read URL from clipboard: " + url);
                        readSuccess = true;

                        // --- NEW: Clear Clipboard ---
                        try {
                            ClipData emptyClip = ClipData.newPlainText("", ""); // Create empty clip
                            clipboardManager.setPrimaryClip(emptyClip);
                            Log.i(TAG_ACTIVITY, "Clipboard cleared.");
                        } catch (Exception e) {
                            Log.e(TAG_ACTIVITY, "Error clearing clipboard: " + e.getMessage());
                            // Continue anyway, reading was successful
                        }
                        // --- END NEW ---
                    } else {
                        Log.w(TAG_ACTIVITY, "Clipboard text is null.");
                    }
                } else {
                    Log.w(TAG_ACTIVITY, "Clipboard clip is null or empty after hasPrimaryClip check.");
                }
            } else {
                Log.w(TAG_ACTIVITY, "Clipboard has no primary clip to read.");
            }


            if (readSuccess && url != null) {
                Intent broadcastIntent = new Intent(RedirectShortsService.ACTION_URL_COPIED_VIA_ACTIVITY);
                broadcastIntent.putExtra(RedirectShortsService.EXTRA_URL, url);
                sendBroadcast(broadcastIntent);
                Log.d(TAG_ACTIVITY, "Broadcast sent with URL.");
            } else {
                // If read failed, still send a broadcast but without URL, or with an error flag.
                // Service should handle this gracefully (e.g., reset state).
                // For now, we just log and the service's timeout will eventually trigger a reset.
                Log.w(TAG_ACTIVITY, "Failed to read URL from clipboard. No broadcast with URL sent.");
                // Optionally send a failure broadcast:
                // Intent failureIntent = new Intent(RedirectShortsService.ACTION_URL_COPIED_VIA_ACTIVITY);
                // sendBroadcast(failureIntent); // Service would see null URL
            }

            finishAndRemoveTaskLog();
        } else if (hasWindowFocus() && clipboardReadAttempted){ // Already attempted this onResume
            Log.d(TAG_ACTIVITY, "Clipboard already processed this onResume cycle. Finishing.");
            finishAndRemoveTaskLog();
        }
    }

    private void finishAndRemoveTaskLog() {
        Log.d(TAG_ACTIVITY, "Finishing ClipboardAccessActivity.");
        if (!isFinishing()) {
            finishAndRemoveTask();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG_ACTIVITY, "onWindowFocusChanged: " + hasFocus);
        if (hasFocus && !clipboardReadAttempted) {
            Log.d(TAG_ACTIVITY, "Window focused, calling onResume to attempt clipboard read.");
            onResume(); // Re-trigger onResume logic if focus gained and not yet attempted
        } else if (!hasFocus && !isFinishing()) {
            Log.w(TAG_ACTIVITY, "Lost focus. Finishing ClipboardAccessActivity.");
            finishAndRemoveTaskLog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG_ACTIVITY, "onPause");
        if (!isFinishing()) {
            Log.d(TAG_ACTIVITY, "onPause and not finishing, ensuring finish via finishAndRemoveTaskLog.");
            finishAndRemoveTaskLog();
        }
    }
}