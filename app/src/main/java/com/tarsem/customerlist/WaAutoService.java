package com.tarsem.customerlist;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * Taps the Send button in WhatsApp for a message that the app has just opened,
 * then presses Back until the app is on screen again.
 * It does nothing unless the app has called begin() (i.e. the user started a send).
 */
public class WaAutoService extends AccessibilityService {

    static volatile WaAutoService instance;
    static volatile boolean expecting = false;   // waiting to tap Send in the next WhatsApp screen
    static volatile int result = 0;              // 0 = unknown, 1 = Send tapped, 2 = Send button not found

    private final Handler h = new Handler(Looper.getMainLooper());
    private long startedAt = 0;

    static boolean isEnabled() { return instance != null; }

    /** Called by the app right before it opens a WhatsApp chat. */
    static void begin() {
        result = 0;
        expecting = true;
        WaAutoService s = instance;
        if (s != null) {
            s.startedAt = SystemClock.uptimeMillis();
            s.h.removeCallbacks(s.poll);
            s.h.postDelayed(s.poll, 1500);
        }
    }

    @Override protected void onServiceConnected() { instance = this; }

    @Override public boolean onUnbind(android.content.Intent i) {
        instance = null;
        expecting = false;
        return super.onUnbind(i);
    }

    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }

    private static boolean isWhatsApp(String pkg) {
        return pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b");
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!expecting) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && isWhatsApp(String.valueOf(root.getPackageName()))) {
                AccessibilityNodeInfo send = findSend(root);
                if (send != null && click(send)) {
                    expecting = false;
                    result = 1;
                    returnToApp(6, 1500);
                    return;
                }
            }
            if (SystemClock.uptimeMillis() - startedAt > 20000) {
                expecting = false;
                result = 2;
                returnToApp(6, 800);
                return;
            }
            h.postDelayed(this, 600);
        }
    };

    private AccessibilityNodeInfo findSend(AccessibilityNodeInfo root) {
        for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
            List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(pkg + ":id/send");
            if (l != null && !l.isEmpty()) return l.get(0);
        }
        List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByText("Send");
        if (l != null) {
            for (AccessibilityNodeInfo n : l) {
                CharSequence d = n.getContentDescription();
                if (d != null && d.toString().equalsIgnoreCase("Send")) return n;
            }
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo n) {
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 4 && cur != null; i++) {
            if (cur.isClickable()) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            cur = cur.getParent();
        }
        return false;
    }

    // Press Back only while WhatsApp is on screen, until our app is visible again.
    private void returnToApp(final int tries, long delay) {
        h.postDelayed(() -> {
            AccessibilityNodeInfo r = getRootInActiveWindow();
            String pkg = r == null ? "" : String.valueOf(r.getPackageName());
            if (pkg.equals(getPackageName()) || tries <= 0) return;
            if (isWhatsApp(pkg)) performGlobalAction(GLOBAL_ACTION_BACK);
            returnToApp(tries - 1, 900);
        }, delay);
    }
}
