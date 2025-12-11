package me.ambassator.phonepointer.util;

/**
 * Preference keys mirroring the Taskbar implementation for desktop input.
 */
public final class Constants {
    private Constants() {}

    public static final String PREF_DESKTOP_INPUT = "desktop_input";
    public static final String PREF_DESKTOP_GYRO = "desktop_gyro";
    public static final String PREF_DESKTOP_GYRO_SLOW = "desktop_gyro_slow";
    public static final String PREF_DIM_SCREEN = "dim_screen";
    public static final String PREF_SWIPE_SENS = "swipe_sensitivity_percent";
    public static final String PREF_GYRO_SENS = "gyro_sensitivity_percent"; // legacy single slider
    public static final String PREF_GYRO_SENS_X = "gyro_sensitivity_percent_x";
    public static final String PREF_GYRO_SENS_Y = "gyro_sensitivity_percent_y";
}
