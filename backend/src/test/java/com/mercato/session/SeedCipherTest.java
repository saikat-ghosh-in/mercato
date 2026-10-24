package com.mercato.session;
import com.mercato.Auth.SeedCipher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeedCipherTest {
 @Test void authenticatedEncryptionBindsSeedToAccountAndRejectsTampering() {
   var cipher=new SeedCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
   String encrypted=cipher.encrypt("USR-1","SEED");
   assertFalse(encrypted.contains("SEED"));
   assertEquals("SEED",cipher.decrypt("USR-1",encrypted));
   assertThrows(IllegalStateException.class,()->cipher.decrypt("USR-2",encrypted));
   assertNotEquals(encrypted,cipher.encrypt("USR-1","SEED"));
 }
 @Test void absentEncryptionKeyFailsClosed() {assertThrows(IllegalStateException.class,()->new SeedCipher("").encrypt("USR-1","SEED"));}
}
