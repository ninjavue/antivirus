package uz.csec.antivirus;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Log;

public class InstallBlockerAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.contains("packageinstaller")) {
            Log.d("InstallBlocker", "Install dialog detected: " + pkg);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                blockInstallButton(root);
            }
        }
    }

    private void blockInstallButton(AccessibilityNodeInfo node) {
        if (node == null) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                CharSequence text = child.getText();
                if (text != null) {
                    String t = text.toString().toLowerCase();
                    if (t.contains("cancel") || t.contains("bekor") || t.contains("отмена")) {
                        child.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d("InstallBlocker", "Install dialog canceled!");
                    }
                }
                blockInstallButton(child);
            }
        }
    }

    @Override
    public void onInterrupt() {}
} 