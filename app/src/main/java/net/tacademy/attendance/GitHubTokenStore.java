package net.tacademy.attendance;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Device-local encryption. The APK contains no GitHub credential. */
final class GitHubTokenStore {
    private final SharedPreferences prefs;private final String alias;
    GitHubTokenStore(Context c){this(c,"github_log_credential");}
    GitHubTokenStore(Context c,String name){prefs=c.getSharedPreferences(name,Context.MODE_PRIVATE);alias="attendance."+name;}
    boolean configured(){return prefs.contains("ciphertext");}
    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return gen.generateKey();
    }
    void save(String token)throws Exception{
        if(!token.matches("github_pat_[A-Za-z0-9_]{20,240}"))throw new IllegalArgumentException("GitHub의 Fine-grained 토큰을 붙여 넣어 주세요.");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String encoded=Base64.encodeToString(c.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!prefs.edit().putString("ciphertext",encoded).putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).commit())throw new java.io.IOException();
    }
    String read()throws Exception{
        if(!configured())throw new IllegalStateException("먼저 GitHub 업로드 연결을 설정해 주세요.");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(prefs.getString("ciphertext",""),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    void clear()throws Exception{if(!prefs.edit().clear().commit())throw new java.io.IOException();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias))store.deleteEntry(alias);}
}
