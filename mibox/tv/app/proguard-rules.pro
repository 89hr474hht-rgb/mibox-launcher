# LaunchOS TV: Media3 and AndroidX ship their own consumer rules; nothing here reads classes by reflection.
# Strip android.util.Log calls from the release build, so no stream address (which holds the account) reaches logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
