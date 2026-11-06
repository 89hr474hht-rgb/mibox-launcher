package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Where the owner types the server, user name and password, with the remote and the box's keyboard. Saving tests
 * the account with the server first; only an account the server accepts is kept. The password field stays masked.
 */
public final class AccountActivity extends Activity {
    private EditText server, user, password;
    private TextView result;
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_account);
        server = findViewById(R.id.server);
        user = findViewById(R.id.user);
        password = findViewById(R.id.password);
        result = findViewById(R.id.result);
        Account a = AccountStore.load(this);
        if (a != null) {
            server.setText(a.server);
            user.setText(a.user);
            password.setText(a.password);
        }
        findViewById(R.id.save).setOnClickListener(v -> save());
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
        server.setText(base);
        Account account = new Account(base, u, p);
        busy = true;
        show(getString(R.string.testing), false);
        Bg.run(() -> new Xtream(account).login(), (info, error) -> {
            busy = false;
            if (isDestroyed()) return;
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
            String formats = info.ts && info.m3u8 ? "TS et HLS" : info.m3u8 ? "HLS" : "TS";
            show("Connecté. Compte enregistré sur la box.\n"
                    + "Abonnement : " + expires + "\n"
                    + "Connexions : " + info.activeConnections + " en cours sur " + Math.max(info.maxConnections, 1)
                    + " autorisée" + (info.maxConnections > 1 ? "s" : "") + "\n"
                    + "Formats du direct : " + formats, false);
        });
    }

    private void show(String text, boolean error) {
        result.setText(text);
        result.setTextColor(getColor(error ? R.color.danger : R.color.text));
    }
}
