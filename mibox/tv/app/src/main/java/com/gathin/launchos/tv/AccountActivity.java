package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Connexion: a glass card split in two over the posters already on the box. The owner types the server, user name
 * and password with the remote and the box's keyboard; the eye shows or hides the password. Se connecter tests the
 * account with the server first (the button turns into a spinner meanwhile); only an account the server accepts is
 * kept, and the button then says Continuer.
 */
public final class AccountActivity extends Activity {
    private EditText server, user, password;
    private TextView result, saveLabel;
    private View saveSpinner;
    private ImageView reveal;
    private boolean busy, connected, shown;

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
        Ui.lift(reveal, 1.05f);
        Ui.lift(findViewById(R.id.save), 1.03f);
        Ui.lift(findViewById(R.id.remove), 1.05f);
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
        findViewById(R.id.remove).setOnClickListener(v -> {
            AccountStore.clear(this);
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
        if (base == null || u.isEmpty() || p.isEmpty()) {
            show(getString(R.string.missing_fields), true);
            return;
        }
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
                show(error.french(), true);
                return;
            }
            if (!AccountStore.save(this, account)) {
                show(getString(R.string.saved_failed), true);
                return;
            }
            String expires = info.expires > 0
                    ? new SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(new Date(info.expires * 1000))
                    : "sans date de fin";
            connected = true;
            saveLabel.setText(R.string.login_continue);
            String formats = info.ts && info.m3u8 ? "TS et HLS" : info.m3u8 ? "HLS" : "TS";
            show("Connecté. Compte enregistré sur la box.\n"
                    + "Abonnement : " + expires + "\n"
                    + "Connexions : " + info.activeConnections + " en cours sur " + Math.max(info.maxConnections, 1)
                    + " autorisée" + (info.maxConnections > 1 ? "s" : "") + "\n"
                    + "Formats du direct : " + formats, false);
        });
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
        result.setTextColor(getColor(error ? R.color.ds_danger : R.color.ds_text));
        ImageView icon = findViewById(R.id.result_icon);
        icon.setImageResource(error ? R.drawable.ic_alert : waiting ? R.drawable.ic_refresh : R.drawable.ic_check_circle);
        icon.setImageTintList(getColorStateList(error ? R.color.danger : waiting ? R.color.accent_light : R.color.ok));
    }
}
