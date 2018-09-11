package tdl.datapoint.video.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

public class TokenSigningService {
    private static final Logger log = LoggerFactory.getLogger(TokenSigningService.class);

    private Mac mac;

    public TokenSigningService(String encryptionKey) throws NoSuchAlgorithmException, InvalidKeyException {
        mac = Mac.getInstance("HmacMD5");
        mac.init(asSecretKey(encryptionKey));
    }

    private SecretKey asSecretKey(String encryptionKey) {
        byte[] encryptionKeyAsBytes = encryptionKey.getBytes();
        return new SecretKeySpec(encryptionKeyAsBytes, 0, encryptionKeyAsBytes.length, "AES");
    }

    private String sign(String token) {
        log.info("Request to sign token: '{}'", token);
        String signedToken = Arrays.toString(mac.doFinal(token.getBytes()));
        log.info("Signed token (raw): {}", signedToken);
        return signedToken;
    }

    public String createHashFrom(String challengeId, String participantId) throws UnsupportedEncodingException {
        String signedToken = sign(challengeId + participantId);
        String encodeToken = Base64.getEncoder().encodeToString(signedToken.getBytes("UTF-8"));
        log.info("Signed token (Base64 encoded): {}", encodeToken);
        return encodeToken;
    }
}
