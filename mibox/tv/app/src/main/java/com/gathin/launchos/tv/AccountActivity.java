package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Connexion. The owner types the server, user name and password with the remote; the eye shows or hides the password.
 * The password may stay empty for a code-only account: the identifier is then sent as the password too, the form
 * such providers use (an Xtream stream address carries the password, so an empty one cannot work).
 * Se connecter tests the account with the server first (on {@link Bg#run}, the button a spinner meanwhile); only an
 * account the server accepts is kept (AccountStore), and the button then says Continuer. Supprimer le compte asks a
 * second OK ({@link Confirm}), then deletes the account and everything kept for it (AccountStore.forget).
 */
public final class AccountActivity extends Activity {
    private EditText server, user, password;
    private TextView result, saveLabel;
    private View saveSpinner;
    private ImageView reveal;
    private boolean busy, connected, shown;
    private Confirm remove;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_account);
        server = findViewById(R.id.server);
        user = findViewById(R.id.user);
        password = findViewById(R.id.password);
        result = findViewById(R.id.result);
        saveLabel = findViewById(R.id.save_label);
        saveSpinner = findViewById(R.id.save_spinner);
        reveal = findViewById(R.id.reveal);
        reveal.setOnClickListener(v -> reveal(!shown));
        Focus.lift(reveal);
        Focus.lift(findViewById(R.id.save));
        Focus.lift(findViewById(R.id.remove));
        Account a = AccountStore.load(this);
        if (a != null) {
            server.setText(a.server);
            user.setText(a.user);
            password.setText(a.password);
        }
        TextWatcher edited = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence t, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence t, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (!connected || busy) return;
                connected = false;
                saveLabel.setText(R.string.login_connect);
            }
        };
        server.addTextChangedListener(edited);
        user.addTextChangedListener(edited);
        password.addTextChangedListener(edited);
        findViewById(R.id.save).setOnClickListener(v -> {
            if (connected) finish();
            else save();
        });
        TextView removeButton = findViewById(R.id.remove);
        remove = new Confirm(removeButton, removeButton, () -> {
            AccountStore.forget(this);
            connected = false;
            saveLabel.setText(R.string.login_connect);
            server.setText("");
            user.setText("");
            password.setText("");
            show(getString(R.string.removed), false);
        });
        (a == null ? server : findViewById(R.id.save)).requestFocus();
    }

    private void save() {
        if (busy) return;
        String typed = server.getText().toString();
        // A pasted M3U link carries the user name and password: fill them in.
        String[] fromLink = Account.credentialsFromLink(typed);
        if (fromLink != null) {
            user.setText(fromLink[0]);
            password.setText(fromLink[1]);
        }
        String base = Account.normalizeServer(typed);
        String u = user.getText().toString().trim(), p = password.getText().toString();
        if (base == null || u.isEmpty()) {
            show(getString(R.string.missing_fields), true);
            return;
        }
        boolean codeOnly = p.isEmpty();
        if (codeOnly) p = u;
        busy = true;
        server.setText(base);
        Account account = new Account(base, u, p);
        loading(true);
        show(getString(R.string.testing), false);
        Bg.run(() -> new Xtream(account).login(), (info, error) -> {
            busy = false;
            if (isDestroyed()) return;
            loading(false);
            if (error != null) {
                show(codeOnly && error.kind == XtreamException.Kind.AUTH ? getString(R.string.login_code_only_refused)
                        : error.text(this), true);
                return;
            }
            if (!AccountStore.save(this, account)) {
                show(getString(R.string.saved_failed), true);
                return;
            }
            String expires = info.expires > 0
                    ? new SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(new Date(info.expires * 1000))
                    : getString(R.string.login_no_end);
            connected = true;
            // A panel that only offers HLS for live channels: ask for it first.
            if (info.m3u8 && !info.ts) Options.setLiveHls(this, AccountStore.key(account), true);
            saveLabel.setText(R.string.login_continue);
            String formats = getString(info.ts && info.m3u8 ? R.string.login_formats_both
                    : info.m3u8 ? R.string.login_formats_hls : R.string.login_formats_ts);
            int allowed = Math.max(info.maxConnections, 1);
            show(getString(R.string.login_ok) + "\n"
                    + getString(R.string.login_expires, expires) + "\n"
                    + getResources().getQuantityString(R.plurals.login_connections, allowed, info.activeConnections, allowed)
                    + "\n" + getString(R.string.login_formats, formats), false);
        });
    }

    /** Back while the deletion waits for its second OK only puts the button back. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && remove != null && remove.cancel()) return true;
        return super.onKeyDown(keyCode, event);
    }

    /** Se connecter becomes a spinner while the server answers. */
    private void loading(boolean on) {
        saveLabel.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        saveSpinner.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    /** The eye: shows the password as typed, or masks it again; the cursor stays at the end. */
    private void reveal(boolean show) {
        shown = show;
        password.setInputType(InputType.TYPE_CLASS_TEXT | (show ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        password.setTypeface(server.getTypeface());
        password.setSelection(password.getText().length());
        reveal.setImageResource(show ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        reveal.setContentDescription(getString(show ? R.string.login_hide_password : R.string.login_show_password));
    }

    private void show(String text, boolean error) {
        boolean waiting = text.equals(getString(R.string.testing));
        findViewById(R.id.result_box).setVisibility(View.VISIBLE);
        result.setText(text);
        result.setTextColor(getColor(error ? R.color.lo_error : R.color.lo_text));
        ImageView icon = findViewById(R.id.result_icon);
        icon.setImageResource(error ? R.drawable.ic_alert : waiting ? R.drawable.ic_refresh : R.drawable.ic_check_circle);
        icon.setImageTintList(getColorStateList(error ? R.color.lo_error : waiting ? R.color.lo_accent : R.color.lo_ok));
    }
}
