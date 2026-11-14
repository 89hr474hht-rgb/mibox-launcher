package com.gathin.launchos.tv;

import android.content.Context;

/**
 * A failed call to the panel, as a kind (and an HTTP code or the account's state when there is one). It never carries
 * the address, which holds the account; {@link #text} gives the sentence to show, from the resources.
 */
final class XtreamException extends Exception {
    enum Kind { BAD_ADDRESS, UNKNOWN_HOST, TIMEOUT, NETWORK, REFUSED, SERVER, BAD_ANSWER, TOO_BIG, AUTH, ACCOUNT_STATUS }

    final Kind kind;
    final int code;
    final String status;

    XtreamException(Kind kind, int code) {
        this(kind, code, null);
    }

    XtreamException(Kind kind, int code, String status) {
        super(kind + (code != 0 ? " " + code : ""));
        this.kind = kind;
        this.code = code;
        this.status = status;
    }

    /** What went wrong, in a plain sentence for the screen. */
    String text(Context c) {
        switch (kind) {
            case BAD_ADDRESS:
                return c.getString(R.string.err_bad_address);
            case UNKNOWN_HOST:
                return c.getString(R.string.err_unknown_host);
            case TIMEOUT:
                return c.getString(R.string.err_timeout);
            case NETWORK:
                return c.getString(R.string.err_network);
            case REFUSED:
                return c.getString(R.string.err_refused, code);
            case SERVER:
                return c.getString(R.string.err_server, code);
            case BAD_ANSWER:
                return c.getString(R.string.err_bad_answer);
            case TOO_BIG:
                return c.getString(R.string.err_too_big);
            case AUTH:
                return c.getString(R.string.err_auth);
            case ACCOUNT_STATUS:
                return c.getString(R.string.err_account_status, status == null ? "" : status);
            default:
                return c.getString(R.string.err_unknown);
        }
    }
}
