package com.desi.youtube.upload;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class UploadCapabilityService {

  private final SecureRandom random = new SecureRandom();

  public String create() {
    byte[] value = new byte[32];
    random.nextBytes(value);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }

  public String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      );
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public boolean matches(String plain, String hashed) {
    return (
      plain != null &&
      hashed != null &&
      MessageDigest.isEqual(
        hash(plain).getBytes(StandardCharsets.US_ASCII),
        hashed.getBytes(StandardCharsets.US_ASCII)
      )
    );
  }
}
