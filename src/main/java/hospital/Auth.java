package hospital;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password hashing, signed session tokens and simple rate limiting. */
final class Auth {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder UNB64 = Base64.getUrlDecoder();
    static final String DUMMY_HASH = hash("not-a-real-password");

    private final byte[] key;
    private final Map<String, long[]> failures = new HashMap<>();
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    Auth(String secret) {
        if (secret == null || secret.length() < 16) {
            byte[] r = new byte[32];
            RANDOM.nextBytes(r);
            secret = B64.encodeToString(r);
            System.out.println("Note: SESSION_SECRET is not set (or too short). Sessions will reset on every restart.");
        }
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    // ---- passwords ----
    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return B64.encodeToString(salt) + ":" + B64.encodeToString(pbkdf2(password, salt));
    }

    static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split(":");
            return p.length == 2 && MessageDigest.isEqual(pbkdf2(password, UNB64.decode(p[0])), UNB64.decode(p[1]));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- session tokens ----
    String token(User user) {
        String body = B64.encodeToString((user.getId() + "|" + (System.currentTimeMillis() / 1000 + 8 * 3600))
                .getBytes(StandardCharsets.UTF_8));
        return body + "." + B64.encodeToString(hmac(body));
    }

    /** Returns the user id inside a valid, unexpired token, or -1. */
    int userId(String token) {
        try {
            String[] p = token.split("\\.");
            if (p.length != 2 || !MessageDigest.isEqual(hmac(p[0]), UNB64.decode(p[1]))) return -1;
            String[] f = new String(UNB64.decode(p[0]), StandardCharsets.UTF_8).split("\\|");
            return Long.parseLong(f[1]) < System.currentTimeMillis() / 1000 ? -1 : Integer.parseInt(f[0]);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- rate limiting ----
    synchronized boolean blocked(String key) {
        long[] f = failures.get(key);
        return f != null && f[0] >= 5 && System.currentTimeMillis() - f[1] < 10 * 60_000;
    }

    synchronized void failed(String key) {
        long now = System.currentTimeMillis();
        long[] f = failures.compute(key, (k, v) -> v == null || now - v[1] >= 10 * 60_000 ? new long[]{0, now} : v);
        f[0]++;
        f[1] = now;
    }

    synchronized void succeeded(String key) { failures.remove(key); }

    /** Allows at most max calls per minute for the given key. */
    synchronized boolean rate(String key, int max) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
        if (q.size() >= max) return false;
        q.addLast(now);
        return true;
    }
}
