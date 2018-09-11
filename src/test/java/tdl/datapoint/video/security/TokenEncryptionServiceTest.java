package tdl.datapoint.video.security;

import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TokenEncryptionServiceTest {

    private TokenEncryptionService tokenEncryptionService;

    @Before
    public void setUp() throws InvalidKeyException, NoSuchAlgorithmException {
        tokenEncryptionService = new TokenEncryptionService("some other password");
    }

    @Test
    public void should_encrypt_a_token_with_a_key() throws UnsupportedEncodingException {
        String expectedEncryptedToken = "NkNGQTNCNzE3Njg1ODJEODlDQjE5RjhCNDQwOUMwRUM0";

        String actualEncryptedToken = tokenEncryptionService.createHashFrom("TCH","dcb54f35845842568b25a739311320cb");

        assertThat("Expected the encrypted tokens to match", actualEncryptedToken, is(expectedEncryptedToken));
    }

    @Test
    public void should_be_idempotent_for_a_given_input() throws UnsupportedEncodingException {
        String firstActualEncryptedToken = tokenEncryptionService.createHashFrom("TCH","a965e26fe3b9421ea0b0f65959a8a1ae");
        String secondActualEncryptedToken = tokenEncryptionService.createHashFrom("TCH","a965e26fe3b9421ea0b0f65959a8a1ae");

        assertThat("Expected to produce the same encrypted tokens", secondActualEncryptedToken, is(firstActualEncryptedToken));
    }
}
