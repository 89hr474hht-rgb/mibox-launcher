package com.gathin.launchos.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the account on the box only, encrypted with an AES key that never leaves the Android Keystore. The app's
 * files are private to it and the manifest turns backups off, so the account is not copied anywhere.
 */
final class AccountStore {
    private static final String KEY_ALIAS = "launchos_tv_account";
    private static final String PREFS = "account";

    private static Account cached;
    private static int version;

    private AccountStore() {
    }

    /** Changes each time the account is saved or removed, so screens know to reload. */
    static int version() {
        return version;
    }

    static synchronized Account load(Context context) {
        if (cached != null) return cached;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String data = p.getString("data", null), iv = p.getString("iv", null);
        if (data == null || iv == null) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            JSONObject o = new JSONObject(new String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8));
            cached = new Account(o.getString("server"), o.getString("user"), o.getString("password"));
            return cached;
        } catch (GeneralSecurityException | JSONException | IllegalArgumentException | java.io.IOException e) {
            return null;
        }
    }

    static synchronized boolean save(Context context, Account a) {
        try {
            JSONObject o = new JSONObject().put("server", a.server).put("user", a.user).put("password", a.password);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] data = c.doFinal(o.toString().getBytes(StandardCharsets.UTF_8));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("data", Base64.encodeToString(data, Base64.NO_WRAP))
                    .putString("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                    .apply();
            cached = a;
            version++;
            return true;
        } catch (GeneralSecurityException | JSONException | java.io.IOException e) {
            return false;
        }
    }

    static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        cached = null;
        version++;
    }

    private static SecretKey key() throws GeneralSecurityException, java.io.IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry e = ks.getEntry(KEY_ALIAS, null);
        if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }
}
