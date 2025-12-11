package me.ambassator.phonepointer.util;

/**
 * Simple display information holder (width/height/density).
 * Matches the structure used by Taskbar's DesktopModeInputController.
 */
public class DisplayInfo {
    public int width;
    public int height;
    public int currentDensity;
    public int defaultDensity;
    public boolean displayDefaultsToFreeform;

    public DisplayInfo(int width, int height, int currentDensity, int defaultDensity, boolean displayDefaultsToFreeform) {
        this.width = width;
        this.height = height;
        this.currentDensity = currentDensity;
        this.defaultDensity = defaultDensity;
        this.displayDefaultsToFreeform = displayDefaultsToFreeform;
    }
}
