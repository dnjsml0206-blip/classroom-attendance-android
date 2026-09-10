package net.tacademy.attendance;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.net.URLDecoder;
import java.util.regex.*;

/** Authentication stays encrypted on this device. Never logs cookie values. */
public final class AuthVault {
    private static final String ALIAS = "attendance-cookie-v1";
    private final Prefs prefs;
    public AuthVault(Context context) { prefs = new Prefs(context); }

    private SecretKey key() throws GeneralSecurityException, java.io.IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    public synchronized void save(String cookies) throws Exception {
        cookie(cookies, "CstrCode");
        cookie(cookies, "CstrCCode");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String data = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(cookies.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        prefs.raw.edit().putString("auth", data).apply();
    }
    public String read() throws Exception {
        String value = prefs.raw.getString("auth", "");
        if (value.isEmpty()) throw new GeneralSecurityException("Authentication required");
        String[] parts = value.split("\\.", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    public boolean ready() { try { String c = read(); cookie(c,"CstrCode"); cookie(c,"CstrCCode"); return true; } catch (Exception e) { return false; } }
    public void clear() { prefs.raw.edit().remove("auth").apply(); }
    public static String cookie(String header, String name) {
        if (header == null) throw new IllegalArgumentException("Authentication required");
        Matcher match = Pattern.compile("(?:^|;\\s*)" + Pattern.quote(name) + "=([^;]*)").matcher(header);
        if (!match.find() || match.group(1).isEmpty()) throw new IllegalArgumentException("Authentication required");
        String encoded = match.group(1);
        Matcher unicode = Pattern.compile("%u([0-9a-fA-F]{4})").matcher(encoded);
        StringBuffer buffer = new StringBuffer();
        while (unicode.find()) unicode.appendReplacement(buffer, Matcher.quoteReplacement(
            String.valueOf((char) Integer.parseInt(unicode.group(1), 16))));
        unicode.appendTail(buffer);
        String decoded; try { decoded = URLDecoder.decode(buffer.toString().replace("+", "%2B"), "UTF-8"); } catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
        if (decoded.length() > 256 || decoded.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid authentication value");
        return decoded;
    }
}
