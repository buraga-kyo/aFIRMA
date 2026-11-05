package com.afirma.assina;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SenhaFirmaTest {
 @Test void confereVetorCalculadoPeloCryptoDoNodeComAcentosEEmoji(){
   byte[] sal=new byte[16];for(int i=0;i<16;i++)sal[i]=(byte)i;
   String base64=Base64.getEncoder().encodeToString(sal);
   // crypto.pbkdf2Sync('Senha de produção ç🔐', Buffer.from([0..15]), 310000, 32, 'sha256')
   String hash="Ski45dvgRleAH0Xjn8CRGDF83HKt1Frzg01+exec4JQ=";
   assertTrue(SenhaFirma.verificar("Senha de produção ç🔐",hash,base64));
   assertFalse(SenhaFirma.verificar("Senha diferente",hash,base64));
 }
 @Test void criaCredenciaisCompativeisComOsCamposDaOrigem(){
   var a=SenhaFirma.criar("senha-de-teste-2026");var b=SenhaFirma.criar("senha-de-teste-2026");
   assertNotEquals(a.sal(),b.sal());assertEquals(16,Base64.getDecoder().decode(a.sal()).length);assertEquals(32,Base64.getDecoder().decode(a.hash()).length);
   assertTrue(SenhaFirma.verificar("senha-de-teste-2026",a.hash(),a.sal()));
   assertFalse(SenhaFirma.verificar("senha-de-teste-2026",a.hash(),"sal-invalido"));
 }
 @Test void mantemCompatibilidadeComCredenciaisBcryptAnteriores(){
   String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4).encode("senha-anterior");
   assertTrue(SenhaFirma.verificar("senha-anterior",hash,""));assertFalse(SenhaFirma.verificar("senha-errada",hash,""));
 }
}
