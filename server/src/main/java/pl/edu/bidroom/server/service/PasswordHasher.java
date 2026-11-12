package pl.edu.bidroom.server.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import pl.edu.bidroom.common.BusinessException;

public final class PasswordHasher {
    public static final int ITERATIONS=600_000;
    private final Semaphore slots=new Semaphore(2);
    private final SecureRandom random=new SecureRandom();
    public record Hash(byte[] value,byte[] salt,int iterations) { }
    public Hash create(String password) {
        BusinessException.require(password!=null&&password.length()>=12&&password.length()<=128,"VALIDATION_ERROR","Hasło musi mieć 12–128 znaków.");
        byte[] salt=new byte[16];random.nextBytes(salt);return new Hash(derive(password,salt,ITERATIONS),salt,ITERATIONS);
    }
    public boolean matches(String password,Hash expected) {
        if(password==null||password.length()>128) return false;
        return MessageDigest.isEqual(derive(password,expected.salt(),expected.iterations()),expected.value());
    }
    private byte[] derive(String password,byte[] salt,int iterations) {
        if(!slots.tryAcquire()) throw new BusinessException("RATE_LIMITED","Trwa zbyt wiele prób logowania. Spróbuj za chwilę.");
        char[] chars=password.toCharArray(); PBEKeySpec spec=new PBEKeySpec(chars,salt,iterations,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword();Arrays.fill(chars,'\0');slots.release(); }
    }
}
