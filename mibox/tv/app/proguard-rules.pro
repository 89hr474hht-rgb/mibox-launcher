# LaunchOS TV: Media3 and AndroidX ship their own consumer rules; nothing here reads classes by reflection.
# Strip the chatty android.util.Log levels from the release build. Warnings and errors stay: they go through Logs,
# which writes where a failure happened and its type, never a message (it could hold a stream address or account).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
