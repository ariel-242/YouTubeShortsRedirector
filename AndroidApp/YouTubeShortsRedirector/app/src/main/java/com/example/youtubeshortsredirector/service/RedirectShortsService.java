package com.example.youtubeshortsredirector.service; // Your package name


import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build; // Added for API version check
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.example.youtubeshortsredirector.MainActivity; // Import MainActivity for constants

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedirectShortsService extends AccessibilityService {

    private static final String TAG = "ShortsRedirectService";
    private static final String YOUTUBE_PACKAGE_NAME = "com.google.android.youtube";

    // Constants for communication with ClipboardAccessActivity
    public static final String ACTION_URL_COPIED_VIA_ACTIVITY = "com.example.youtubeshortsredirector.ACTION_URL_COPIED_VIA_ACTIVITY";
    public static final String EXTRA_URL = "com.example.youtubeshortsredirector.EXTRA_URL";
    // Make sure this FQN matches your ClipboardAccessActivity's actual location
    public static final String CLIPBOARD_ACCESS_ACTIVITY_CLASS_NAME = "com.example.youtubeshortsredirector.ClipboardAccessActivity";


    // CRITICAL: Update these with IDs from uiautomatorviewer for the Shorts player
    private static final String[] POTENTIAL_SHORTS_PLAYER_IDS = {
            "com.google.android.youtube:id/reel_player_underlay", // Example
            "com.google.android.youtube:id/shorts_player_view_pager" // Example
            // Add more valid IDs here
    };
    private static final String FULLSCREEN_NUTTON_ID = "com.google.android.youtube:id/fullscreen_button";

    private static final String HEBREW_SHARE_CONTENT_DESCRIPTION = "שיתוף הסרטון הזה";
    private static final String ENGLISH_SHARE_CONTENT_DESCRIPTION = "Share this video"; // Verify exact string
    private static final String[] SHARE_CANDIDATES = {HEBREW_SHARE_CONTENT_DESCRIPTION, ENGLISH_SHARE_CONTENT_DESCRIPTION};

    private static final String ENGLISH_COPY_LINK_TEXT = "Copy link";
    private static final String HEBREW_COPY_LINK_TEXT = "העתקת הקישור"; // Verify exact string
    private static final String[] COPY_LINK_CANDIDATES = {ENGLISH_COPY_LINK_TEXT, HEBREW_COPY_LINK_TEXT};

    private boolean isWaitingForShareSheet = false;
    private boolean isWaitingForOwnActivityFocus = false;
    private boolean isProcessingShort = false;
    private boolean isRedirectionFunctionalityEnabled = true; // Default to enabled

    private int findShareAttemptCount = 0;
    private static final int MAX_FIND_SHARE_ATTEMPTS = 5; // Retry limit for finding share button

    private BroadcastReceiver urlCopiedReceiver;
    private BroadcastReceiver settingsUpdateReceiver;
    private Handler timeoutHandler;
    private Runnable clipboardActivityTimeoutRunnable;
    private static final long CLIPBOARD_ACTIVITY_TIMEOUT_MS = 7000; // 7 seconds

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Service connected.");
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) { info = new AccessibilityServiceInfo(); }
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.packageNames = new String[]{YOUTUBE_PACKAGE_NAME};
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100; // Default is 0, 100ms is reasonable
        // android:canRetrieveWindowContent="true" is set in XML config
        setServiceInfo(info);

        timeoutHandler = new Handler(Looper.getMainLooper());

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE);
        isRedirectionFunctionalityEnabled = prefs.getBoolean(MainActivity.KEY_REDIRECTION_ENABLED, true);
        Log.i(TAG, "Initial Redirection Functionality Enabled: " + isRedirectionFunctionalityEnabled);

        // Receiver for URL from ClipboardAccessActivity
        urlCopiedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_URL_COPIED_VIA_ACTIVITY.equals(intent.getAction())) {
                    if (clipboardActivityTimeoutRunnable != null) {
                        timeoutHandler.removeCallbacks(clipboardActivityTimeoutRunnable);
                        clipboardActivityTimeoutRunnable = null;
                        Log.d(TAG, "ClipboardActivity timeout cancelled.");
                    }
                    String url = intent.getStringExtra(EXTRA_URL);
                    if (url != null) {
                        Log.i(TAG, "Received URL via broadcast: " + url);
                        if(isWaitingForOwnActivityFocus) {
                            isWaitingForOwnActivityFocus = false;
                            extractVideoIdAndRedirect(url);
                        } else {
                            Log.w(TAG, "Received URL broadcast but was NOT waiting for own activity focus. Resetting.");
                            resetStateFlags();
                        }
                    } else {
                        Log.w(TAG, "Received broadcast for URL_COPIED but URL was null. Resetting state.");
                        resetStateFlags();
                    }
                }
            }
        };
        IntentFilter urlFilter = new IntentFilter(ACTION_URL_COPIED_VIA_ACTIVITY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(urlCopiedReceiver, urlFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(urlCopiedReceiver, urlFilter);
        }
        Log.d(TAG, "urlCopiedReceiver registered.");

        // Receiver for settings updates and reset command from MainActivity
        settingsUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                switch (intent.getAction()) {
                    case MainActivity.ACTION_UPDATE_SETTINGS:
                        isRedirectionFunctionalityEnabled = intent.getBooleanExtra(MainActivity.KEY_REDIRECTION_ENABLED, true);
                        Log.i(TAG, "Redirection functionality updated by MainActivity: " + isRedirectionFunctionalityEnabled);
                        if (!isRedirectionFunctionalityEnabled && isProcessingShort) {
                            Log.i(TAG, "Redirection disabled during processing. Resetting state.");
                            resetStateFlags();
                        }
                        break;
                    case MainActivity.ACTION_RESET_FLAGS_COMMAND:
                        Log.i(TAG, "Received reset flags command from MainActivity.");
                        resetStateFlags();
                        break;
                }
            }
        };
        IntentFilter settingsFilter = new IntentFilter();
        settingsFilter.addAction(MainActivity.ACTION_UPDATE_SETTINGS);
        settingsFilter.addAction(MainActivity.ACTION_RESET_FLAGS_COMMAND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsUpdateReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(settingsUpdateReceiver, settingsFilter);
        }
        Log.d(TAG, "settingsUpdateReceiver registered.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRedirectionFunctionalityEnabled) {
            // Log.v(TAG, "Redirection functionality is disabled. Skipping event."); // Can be too noisy
            return;
        }

//        Log.v(TAG, "onAccessibilityEvent - START. isProcessingShort: " + isProcessingShort +
//                ", isWaitingShare: " + isWaitingForShareSheet +
//                ", isWaitingOwnActivity: " + isWaitingForOwnActivityFocus +
//                ", findShareAttempts: " + findShareAttemptCount +
//                ", EventType: " + AccessibilityEvent.eventTypeToString(event.getEventType()) +
//                ", ClassName: " + event.getClassName());

        if (event.getPackageName() == null || !event.getPackageName().toString().equals(YOUTUBE_PACKAGE_NAME)) {
            return;
        }
        if (event.getClassName() != null && event.getClassName().toString().equals(CLIPBOARD_ACCESS_ACTIVITY_CLASS_NAME)) {
            Log.d(TAG, "Event from ClipboardAccessActivity, ignoring.");
            return;
        }
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            return;
        }

        try {
            if (isProcessingShort) {
                if (isWaitingForShareSheet) {
                    Log.d(TAG, "State: Processing & Waiting for Share Sheet. -> findAndClickCopyLink");
                    findAndClickCopyLink(rootNode);
                } else if (isWaitingForOwnActivityFocus) {
                    Log.d(TAG, "State: Processing & Waiting for Own Activity. (No action on this event)");
                } else { // Actively trying to find and click the share button
                    if (findShareAttemptCount < MAX_FIND_SHARE_ATTEMPTS) {
                        Log.d(TAG, "State: Processing & Finding Share Button (Attempt #" + (findShareAttemptCount + 1) + "/" + MAX_FIND_SHARE_ATTEMPTS + ").");
                        findAndClickShareButton(rootNode);
                    } else {
                        Log.w(TAG, "Max attempts (" + MAX_FIND_SHARE_ATTEMPTS + ") reached for Share button. Resetting.");
                        resetStateFlags();
                    }
                }
            } else { // !isProcessingShort
                boolean isShortsPlayerActive = detectShortsPlayer(rootNode);
                if (isShortsPlayerActive) {
                    Log.i(TAG, "Shorts player newly detected. Initializing processing state.");
                    isProcessingShort = true;
                    findShareAttemptCount = 0;
                    // Attempt to find share button on this current event immediately
                    if(findShareAttemptCount < MAX_FIND_SHARE_ATTEMPTS) { // Should be true (0 < 5)
                        Log.d(TAG, "State: Processing & Finding Share Button (Attempt #" + (findShareAttemptCount + 1) + "/" + MAX_FIND_SHARE_ATTEMPTS + ") - immediate after detection.");
                        findAndClickShareButton(rootNode);
                    }
                }
            }
        } finally {
            rootNode.recycle();
        }
    }

    private boolean detectShortsPlayer(AccessibilityNodeInfo rootNode) {
        // Log.v(TAG, "detectShortsPlayer: Checking IDs...");
        for (String id : POTENTIAL_SHORTS_PLAYER_IDS) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                Log.i(TAG, "Shorts player detected by ID: " + id);
                recycleNodeList(nodes);
                return true;
            }
            recycleNodeList(nodes);
        }
        // Log.v(TAG, "detectShortsPlayer: Checking for Share button as fallback...");
        for (String shareCandidateDesc : SHARE_CANDIDATES) {
            List<AccessibilityNodeInfo> shareButtons = findNodesByContentDescription(rootNode, shareCandidateDesc, false);
            if (!shareButtons.isEmpty()) {
                Log.i(TAG, "Shorts player likely active (found Share button with desc: '" + shareCandidateDesc + "').");
                recycleNodeList(shareButtons);
                return true;
            }
            recycleNodeList(shareButtons);
        }
        // Log.v(TAG, "detectShortsPlayer: No Shorts player indicators found.");
        return false;
    }

    private void findAndClickShareButton(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.e(TAG, "findAndClickShareButton: rootNode is null. Incrementing attempt count.");
            findShareAttemptCount++;
            return;
        }

        AccessibilityNodeInfo shareButtonClickTarget = null;
        String foundShareDesc = null;

        for (String shareCandidateDesc : SHARE_CANDIDATES) {
            // Log.v(TAG, "findAndClickShareButton: Trying desc: '" + shareCandidateDesc + "'");
            List<AccessibilityNodeInfo> nodesWithDesc = findNodesByContentDescription(rootNode, shareCandidateDesc, true);
            if (!nodesWithDesc.isEmpty()) {
                for (AccessibilityNodeInfo nodeWithDesc : nodesWithDesc) {
                    AccessibilityNodeInfo clickableNode = getClickableNode(nodeWithDesc);
                    if (clickableNode != null && clickableNode.isVisibleToUser()) { // getClickableNode now checks isEnabled too
                        Rect bounds = new Rect();
                        clickableNode.getBoundsInScreen(bounds);
                        Log.d(TAG, "Clickable Share target candidate found for desc '" + shareCandidateDesc + "'. Class: " + clickableNode.getClassName());
                        Log.d(TAG, "Properties: ID=" + clickableNode.getViewIdResourceName() +
                                ", Clickable=" + clickableNode.isClickable() +
                                ", Enabled=" + clickableNode.isEnabled() +
                                ", Visible=" + clickableNode.isVisibleToUser() +
                                ", Bounds=" + bounds.toShortString());
                        shareButtonClickTarget = clickableNode;
                        foundShareDesc = shareCandidateDesc;
                        break;
                    }
                    if (clickableNode != null) clickableNode.recycle();
                }
            }
            recycleNodeList(nodesWithDesc);
            if (shareButtonClickTarget != null) break;
        }

        if (shareButtonClickTarget != null) {
            Log.d(TAG, "Attempting to click Share button (target found with: '" + foundShareDesc + "')...");
            if (shareButtonClickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "Share button clicked successfully! Waiting for share sheet.");
                isWaitingForShareSheet = true;
            } else {
                Log.e(TAG, "Failed to perform click on Share button (Attempt #" + (findShareAttemptCount + 1) + "). Node class: " + shareButtonClickTarget.getClassName());
                findShareAttemptCount++;
            }
            shareButtonClickTarget.recycle();
        } else {
            Log.w(TAG, "Share button not found or not actionable in this pass (Attempt #" + (findShareAttemptCount + 1) + ").");
            findShareAttemptCount++;
        }
    }

    private void findAndClickCopyLink(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.w(TAG, "findAndClickCopyLink: rootNode is null.");
            return;
        }
        AccessibilityNodeInfo copyLinkButtonNode = null;
        String foundCopyLinkText = null;

        for (String copyLinkTextCandidate : COPY_LINK_CANDIDATES) {
            List<AccessibilityNodeInfo> textNodes = findNodesWithTextOrContentDescription(rootNode, copyLinkTextCandidate, true);
            if (!textNodes.isEmpty()) {
                for (AccessibilityNodeInfo textNode : textNodes) {
                    AccessibilityNodeInfo parentNode = textNode.getParent();
                    if (parentNode != null) {
                        if ("android.widget.Button".equals(parentNode.getClassName()) && parentNode.isClickable() && parentNode.isVisibleToUser() && parentNode.isEnabled()) {
                            copyLinkButtonNode = AccessibilityNodeInfo.obtain(parentNode);
                            foundCopyLinkText = copyLinkTextCandidate;
                            Log.d(TAG, "'" + foundCopyLinkText + "' button (parent) found. Class: " + parentNode.getClassName());
                            parentNode.recycle();
                            break;
                        }
                        parentNode.recycle();
                    }
                    if (copyLinkButtonNode == null) {
                        AccessibilityNodeInfo clickableAncestor = getClickableNode(textNode);
                        if (clickableAncestor != null && clickableAncestor.isVisibleToUser()) { // getClickableNode checks isEnabled
                            copyLinkButtonNode = clickableAncestor;
                            foundCopyLinkText = copyLinkTextCandidate;
                            Log.d(TAG, "'" + foundCopyLinkText + "' clickable ancestor found. Class: " + clickableAncestor.getClassName());
                            break;
                        }
                        if (clickableAncestor != null) clickableAncestor.recycle();
                    }
                }
            }
            recycleNodeList(textNodes);
            if (copyLinkButtonNode != null) break;
        }

        if (copyLinkButtonNode != null) {
            Log.d(TAG, "Attempting to click 'Copy link' button (found as '" + foundCopyLinkText + "', target class: " + copyLinkButtonNode.getClassName() + ")");
            if (copyLinkButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "'Copy link' button clicked successfully! Launching ClipboardAccessActivity.");
                isWaitingForShareSheet = false;
                isWaitingForOwnActivityFocus = true;

                if (clipboardActivityTimeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(clipboardActivityTimeoutRunnable);
                }
                clipboardActivityTimeoutRunnable = () -> {
                    if (isWaitingForOwnActivityFocus) {
                        Log.w(TAG, "Timeout waiting for ClipboardAccessActivity broadcast. Resetting state.");
                        resetStateFlags();
                    }
                };
                timeoutHandler.postDelayed(clipboardActivityTimeoutRunnable, CLIPBOARD_ACTIVITY_TIMEOUT_MS);
                Log.d(TAG, "ClipboardActivity timeout started (" + CLIPBOARD_ACTIVITY_TIMEOUT_MS + "ms).");

                Intent intent = new Intent();
                intent.setClassName(getPackageName(), CLIPBOARD_ACCESS_ACTIVITY_CLASS_NAME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                try {
                    startActivity(intent);
                    Log.d(TAG, "ClipboardAccessActivity started.");
                } catch (Exception e) {
                    Log.e(TAG, "Error starting ClipboardAccessActivity: " + e.getMessage() + ". Resetting.");
                    if (clipboardActivityTimeoutRunnable != null) {
                        timeoutHandler.removeCallbacks(clipboardActivityTimeoutRunnable);
                        clipboardActivityTimeoutRunnable = null;
                    }
                    resetStateFlags();
                }
            } else {
                Log.e(TAG, "Failed to perform click on 'Copy link' button. Resetting.");
                resetStateFlags();
            }
            copyLinkButtonNode.recycle();
        } else {
            Log.v(TAG, "'Copy link' structure not found with any candidate texts. Share sheet might still be loading.");
        }
    }

    private AccessibilityNodeInfo getClickableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node); // Start with a copy of the input node
        AccessibilityNodeInfo result = null;
        while (current != null) {
            if (current.isClickable() && current.isEnabled() && current.isVisibleToUser()) {
                result = current; // Found a suitable node, this is our result (it's already an obtained copy)
                current = null; // To exit loop, parent won't be processed now
            } else {
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle(); // Recycle the current iteration's copy
                current = (parent != null) ? AccessibilityNodeInfo.obtain(parent) : null;
                if (parent != null) parent.recycle(); // Recycle original parent from getParent()
            }
        }
        return result; // Return the found clickable node (or null)
    }


    private void extractVideoIdAndRedirect(String url) {
        Log.d(TAG, "extractVideoIdAndRedirect called with URL: " + url);
        String videoId = null;
        try {
            Pattern shortsPattern = Pattern.compile("youtube\\.com/shorts/([^?&/]+)");
            Matcher matcher = shortsPattern.matcher(url);
            if (matcher.find()) {
                videoId = matcher.group(1);
            }
            if (videoId != null) {
                Log.i(TAG, "Extracted Video ID: " + videoId);
                if (detectShortsPlayer(getRootInActiveWindow())) {
                    Log.d(TAG, "Attempting GLOBAL_ACTION_BACK to exit Shorts UI/Share Sheet.");
                    boolean back1 = performGlobalAction(GLOBAL_ACTION_BACK);
                    Log.d(TAG, "First GLOBAL_ACTION_BACK: " + back1);
                    try {
                        Thread.sleep(200); // Adjusted sleep duration
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, "Delay after back press interrupted.");
                    }
                }
                String standardUrl = "https://www.youtube.com/watch?v=" + videoId;
                Log.i(TAG, "Redirecting to: " + standardUrl);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(standardUrl));
                intent.setPackage(YOUTUBE_PACKAGE_NAME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                try {
                    startActivity(intent);
                    Log.i(TAG, "Redirection intent launched.");
                } catch (Exception e) {
                    Log.e(TAG, "Error launching redirection intent: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "Could not extract Video ID from URL: " + url);
            }
        } finally {
            resetStateFlags();
        }
    }

    private List<AccessibilityNodeInfo> findNodesByContentDescription(AccessibilityNodeInfo rootNode, String contentDescription, boolean obtainCopy) {
        List<AccessibilityNodeInfo> foundNodes = new ArrayList<>();
        if (rootNode == null || contentDescription == null) return foundNodes;
        Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
        AccessibilityNodeInfo initialNode = obtainCopy ? AccessibilityNodeInfo.obtain(rootNode) : rootNode;
        if (initialNode == null && obtainCopy) { return foundNodes; }
        if (initialNode != null) deque.add(initialNode);

        while (!deque.isEmpty()) {
            AccessibilityNodeInfo node = deque.removeFirst();
            CharSequence nodeDesc = node.getContentDescription();
            if (nodeDesc != null && contentDescription.equals(nodeDesc.toString())) {
                AccessibilityNodeInfo nodeToAdd = obtainCopy ? AccessibilityNodeInfo.obtain(node) : node;
                if (nodeToAdd != null) foundNodes.add(nodeToAdd);
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    AccessibilityNodeInfo childToAdd = obtainCopy ? AccessibilityNodeInfo.obtain(child) : child;
                    if (childToAdd != null) deque.addLast(childToAdd);
                    if (obtainCopy) child.recycle(); // Recycle original child from getChild if copy was made
                }
            }
            if (obtainCopy) node.recycle(); // Recycle node from deque if it was a copy
        }
        return foundNodes;
    }

    private List<AccessibilityNodeInfo> findNodesWithTextOrContentDescription(AccessibilityNodeInfo rootNode, String searchText, boolean obtainCopy) {
        List<AccessibilityNodeInfo> foundNodes = new ArrayList<>();
        if (rootNode == null || searchText == null) return foundNodes;
        Deque<AccessibilityNodeInfo> deque = new ArrayDeque<>();
        AccessibilityNodeInfo initialNode = obtainCopy ? AccessibilityNodeInfo.obtain(rootNode) : rootNode;
        if (initialNode == null && obtainCopy) { return foundNodes; }
        if (initialNode != null) deque.add(initialNode);

        while (!deque.isEmpty()) {
            AccessibilityNodeInfo node = deque.removeFirst();
            boolean matched = false;
            CharSequence nodeText = node.getText();
            if (nodeText != null && searchText.equals(nodeText.toString())) {
                AccessibilityNodeInfo nodeToAdd = obtainCopy ? AccessibilityNodeInfo.obtain(node) : node;
                if (nodeToAdd != null) foundNodes.add(nodeToAdd);
                matched = true;
            }
            CharSequence nodeDesc = node.getContentDescription();
            if (nodeDesc != null && searchText.equals(nodeDesc.toString())) {
                if (!matched || (nodeText != null && !nodeText.toString().equals(nodeDesc.toString()))) {
                    AccessibilityNodeInfo nodeToAdd = obtainCopy ? AccessibilityNodeInfo.obtain(node) : node;
                    if (nodeToAdd != null) foundNodes.add(nodeToAdd);
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    AccessibilityNodeInfo childToAdd = obtainCopy ? AccessibilityNodeInfo.obtain(child) : child;
                    if (childToAdd != null) deque.addLast(childToAdd);
                    if (obtainCopy) child.recycle();
                }
            }
            if (obtainCopy) node.recycle();
        }
        return foundNodes;
    }

    private void recycleNodeList(List<AccessibilityNodeInfo> nodes) {
        if (nodes != null) {
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) node.recycle();
            }
            nodes.clear();
        }
    }

    private void resetStateFlags() {
        Log.e(TAG, "--- RESETTING ALL STATE FLAGS ---");
        isWaitingForShareSheet = false;
        isWaitingForOwnActivityFocus = false;
        isProcessingShort = false;
        findShareAttemptCount = 0;
        if (clipboardActivityTimeoutRunnable != null) {
            timeoutHandler.removeCallbacks(clipboardActivityTimeoutRunnable);
            clipboardActivityTimeoutRunnable = null;
            Log.d(TAG, "ClipboardActivity timeout cancelled during state reset.");
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted.");
        if (urlCopiedReceiver != null) {
            try { unregisterReceiver(urlCopiedReceiver); }
            catch (IllegalArgumentException e) { Log.w(TAG, "urlCopiedReceiver not registered: " + e.getMessage()); }
            urlCopiedReceiver = null;
        }
        if (settingsUpdateReceiver != null) {
            try { unregisterReceiver(settingsUpdateReceiver); }
            catch (IllegalArgumentException e) { Log.w(TAG, "settingsUpdateReceiver not registered: " + e.getMessage()); }
            settingsUpdateReceiver = null;
        }
        resetStateFlags();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "Service unbound.");
        onInterrupt();
        return super.onUnbind(intent);
    }
}