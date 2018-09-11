package tdl.datapoint.video.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class TokenEncryptionService {
    private static final Logger log = LoggerFactory.getLogger(TokenEncryptionService.class);

    private Mac mac;

    public TokenEncryptionService(String encryptionKey) throws NoSuchAlgorithmException, InvalidKeyException {
        mac = Mac.getInstance("HmacMD5");
        mac.init(asSecretKey(encryptionKey));
    }

    private SecretKey asSecretKey(String encryptionKey) {
        byte[] encryptionKeyAsBytes = encryptionKey.getBytes();
        return new SecretKeySpec(encryptionKeyAsBytes, 0, encryptionKeyAsBytes.length, "AES");
    }

    public String createHashFrom(String challengeId, String participantId) throws UnsupportedEncodingException {
        String token = challengeId + participantId;
        log.info("Request to encrypt token: '{}'", token);
        byte [] mac_data = mac.doFinal(token.getBytes("UTF-8"));

        String encryptedToken = bytesToHex(mac_data);
        log.debug("Encrypted token (raw): {}", encryptedToken);
        String encodeEncryptedToken = Base64.getUrlEncoder().encodeToString(encryptedToken.getBytes("UTF-8"));
        encodeEncryptedToken = encodeEncryptedToken
                .replaceAll(":", "0")     // :
                .replaceAll(";", "0")     // ;
                .replaceAll("<", "0")     // <
                .replaceAll("=", "0")     // =
                .replaceAll(">", "0")     // >
                .replaceAll("\\?", "0")   // ?
                .replaceAll("/", "0")     // /
                .replaceAll("\\\\", "0"); // /
        log.debug("Encoded encrypted token (Base64): {}", encodeEncryptedToken);
        return encodeEncryptedToken;
    }

    private static String bytesToHex(byte[] bytes) {
        final  char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
