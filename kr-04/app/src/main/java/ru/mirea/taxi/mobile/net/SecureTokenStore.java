package ru.mirea.taxi.mobile.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureTokenStore {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mirea-taxi-refresh-v1";
    private static final String ENTRY = "encrypted_refresh";
    private static final int IV_LENGTH = 12;
    private final SharedPreferences preferences;

    SecureTokenStore(Context context) {
        preferences = context.getSharedPreferences("taxi_session", Context.MODE_PRIVATE);
    }

    String read() {
        String encoded = preferences.getString(ENTRY, null);
        if (encoded == null) {
            return null;
        }
        try {
            byte[] value = Base64.decode(encoded, Base64.NO_WRAP);
            if (value.length < IV_LENGTH + 16) {
                clear();
                return null;
            }
            byte[] iv = java.util.Arrays.copyOfRange(value, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(value, IV_LENGTH, value.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException error) {
            clear();
            return null;
        }
    }

    void write(String token) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            if (iv.length != IV_LENGTH) {
                throw new IOException("Не удалось получить параметр шифрования сессии.");
            }
            byte[] value = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            if (!preferences.edit().putString(ENTRY, Base64.encodeToString(value, Base64.NO_WRAP)).commit()) {
                throw new IOException("Не удалось сохранить зашифрованную сессию.");
            }
        } catch (GeneralSecurityException error) {
            throw new IOException("Ключ сессии недоступен.", error);
        }
    }

    void clear() {
        preferences.edit().remove(ENTRY).apply();
    }

    private SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(PROVIDER);
        store.load(null);
        var cached = store.getKey(KEY_ALIAS, null);
        if (cached instanceof SecretKey) {
            return (SecretKey) cached;
        }
        var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
