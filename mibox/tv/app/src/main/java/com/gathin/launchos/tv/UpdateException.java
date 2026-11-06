package com.gathin.launchos.tv;

import android.content.Context;

/** A failure to show to the user: a string resource and its arguments. */
final class UpdateException extends Exception {
    final int message;
    final Object[] args;

    UpdateException(int message, Object... args) {
        super("update error " + message);
        this.message = message;
        this.args = args;
    }

    String text(Context context) {
        return context.getString(message, args);
    }
}
