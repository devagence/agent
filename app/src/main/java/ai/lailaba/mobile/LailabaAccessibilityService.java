package ai.lailaba.mobile;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

/** Optional, user-enabled accessibility bridge. It does not start itself or hide its status. */
public class LailabaAccessibilityService extends AccessibilityService {
    private static volatile LailabaAccessibilityService instance;
    private static volatile String pendingTapText;

    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        String target = pendingTapText;
        if (target == null || target.trim().isEmpty()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        if (tapMatching(root, target.trim().toLowerCase(Locale.ROOT), 0)) pendingTapText = null;
        root.recycle();
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    static boolean isEnabled() { return instance != null; }
    static boolean requestTapText(String text) {
        if (instance == null || text == null || text.trim().isEmpty()) return false;
        pendingTapText = text;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root != null) {
            boolean result = tapMatching(root, text.trim().toLowerCase(Locale.ROOT), 0);
            root.recycle();
            if (result) pendingTapText = null;
            return result;
        }
        return false;
    }
    private static boolean tapMatching(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 30) return false;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean match = (text != null && text.toString().toLowerCase(Locale.ROOT).contains(target)) ||
            (desc != null && desc.toString().toLowerCase(Locale.ROOT).contains(target));
        if (match && node.isEnabled() && node.isClickable()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean result = tapMatching(child, target, depth + 1);
                child.recycle();
                if (result) return true;
            }
        }
        return false;
    }
}
