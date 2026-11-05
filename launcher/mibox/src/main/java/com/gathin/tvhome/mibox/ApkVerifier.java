package com.gathin.tvhome.mibox;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks a downloaded APK before it is handed to PackageInstaller: it must be this launcher, the exact version
 * the release announced, newer than the installed one, and signed with the installed app's own certificate.
 * The certificate is the real proof that the file comes from the owner's release key: the SHA-256 checks only
 * prove the download was not damaged. Android repeats the signature check itself when installing.
 */
final class ApkVerifier {
    private ApkVerifier() {
    }

    static void verify(Context context, File apk, Release release) throws UpdateException {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = archiveInfo(pm, apk);
        if (archive == null) throw new UpdateException(R.string.mibox_err_not_apk);
        if (!context.getPackageName().equals(archive.packageName)) throw new UpdateException(R.string.mibox_err_package);
        if (!release.version.toString().equals(archive.versionName)
                || versionCode(archive) != release.version.versionCode()) {
            throw new UpdateException(R.string.mibox_err_version);
        }
        PackageInfo self;
        try {
            self = pm.getPackageInfo(context.getPackageName(), certFlags());
        } catch (PackageManager.NameNotFoundException e) {
            throw new UpdateException(R.string.mibox_err_package);
        }
        if (versionCode(archive) <= versionCode(self)) throw new UpdateException(R.string.mibox_err_not_newer);
        Set<String> installed = certs(self);
        Set<String> downloaded = certs(archive);
        if (installed.isEmpty() || !installed.equals(downloaded)) throw new UpdateException(R.string.mibox_err_signature);
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo archiveInfo(PackageManager pm, File apk) {
        PackageInfo info = pm.getPackageArchiveInfo(apk.getPath(), certFlags());
        // Some Android 9 builds return an archive's PackageInfo without signingInfo; the old flag fills it.
        if (info != null && certs(info).isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo old = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
            if (old != null) info = old;
        }
        return info;
    }

    @SuppressWarnings("deprecation")
    private static int certFlags() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
    }

    /** SHA-256 of each current signing certificate. */
    @SuppressWarnings("deprecation")
    private static Set<String> certs(PackageInfo info) {
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            SigningInfo si = info.signingInfo;
            if (si.hasMultipleSigners()) {
                sigs = si.getApkContentsSigners();
            } else {
                Signature[] history = si.getSigningCertificateHistory();
                // The current certificate is the last one of the rotation history.
                if (history != null && history.length > 0) sigs = new Signature[]{history[history.length - 1]};
            }
        }
        if (sigs == null) sigs = info.signatures;
        Set<String> out = new HashSet<>();
        if (sigs != null) {
            for (Signature s : sigs) out.add(Checksums.hex(ReleaseClient.sha256().digest(s.toByteArray())));
        }
        return out;
    }
}
