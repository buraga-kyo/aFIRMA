package com.afirma.assina;

import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PaginaComprovante {
  public byte[] acrescentar(byte[] pdf,String nomeEmpresa,byte[] imagemEmpresa,String tipo,String identificacao) throws Exception {
    return acrescentar(pdf,nomeEmpresa,imagemEmpresa,tipo,identificacao,TemplatesEmpresa.padrao());
  }
  public byte[] acrescentar(byte[] pdf,String nomeEmpresa,byte[] imagemEmpresa,String tipo,String identificacao,Map<String,Object> modelo) throws Exception {
    if(nomeEmpresa==null || nomeEmpresa.isBlank() || nomeEmpresa.length()>120) throw new IllegalArgumentException("Informe o nome da empresa (até 120 caracteres)");
    var personalizado=new LinkedHashMap<>(modelo);
    if(imagemEmpresa!=null && imagemEmpresa.length>0) {
      if(imagemEmpresa.length>1024*1024) throw new IllegalArgumentException("A imagem da empresa deve ter até 1 MB");
      var img=org.openpdf.text.Image.getInstance(imagemEmpresa);
      String formato=img.getOriginalType()==org.openpdf.text.Image.ORIGINAL_PNG?"png":img.getOriginalType()==org.openpdf.text.Image.ORIGINAL_JPEG?"jpeg":"";
      if(formato.isEmpty()) throw new IllegalArgumentException("Use imagem PNG ou JPEG da empresa");
      personalizado.put("logo","data:image/"+formato+";base64,"+Base64.getEncoder().encodeToString(imagemEmpresa));
    }
    String agora=OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX"));
    var pessoa=new LinkedHashMap<String,Object>();pessoa.put("SignatarioNome",identificacao);pessoa.put("SignatarioAssinou",true);
    pessoa.put("SignatarioFirmadoVia",tipo);pessoa.put("SignatarioDataAssinatura",agora);
    return PaginaRegistroAssinaturas.acrescentar(pdf,personalizado,new PaginaRegistroAssinaturas.Dados(nomeEmpresa,"Documento assinado",UUID.randomUUID().toString(),
      HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)),"",agora,List.of(pessoa)));
  }
}
