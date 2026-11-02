package com.afirma.assina;
import java.util.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ArquivosAutentica {
 private final ServicoAutentica dados;private final BancoAssineai banco;private final TransactionTemplate tx;private final EspelhoAutentica espelho;private final Path pasta;
 public ArquivosAutentica(ServicoAutentica dados,BancoAssineai banco,TransactionTemplate tx,EspelhoAutentica espelho,@Value("${APP_CHAT_ARQUIVOS_DIR:./chat-arquivos}")String pasta){this.dados=dados;this.banco=banco;this.tx=tx;this.espelho=espelho;this.pasta=Path.of(pasta).toAbsolutePath().normalize();}
 public Map<String,Object> guardar(MultipartFile arquivo,ServicoAutentica.Contexto c){
  dados.autorizar(c,"atendimentos","read");RegrasAutentica.exigir(arquivo!=null&&!arquivo.isEmpty()&&arquivo.getSize()<=15*1024*1024,"Arquivo inválido ou maior que 15 MB");String nome=Objects.toString(arquivo.getOriginalFilename(),"arquivo").replaceAll("[\\r\\n]","");String mime=Objects.toString(arquivo.getContentType(),"application/octet-stream");RegrasAutentica.exigir(nome.length()<=255&&mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"),"Nome ou tipo de arquivo inválido");
  UUID id=UUID.randomUUID();Path destino=pasta.resolve(id.toString());
  try{Files.createDirectories(pasta);Files.setPosixFilePermissions(pasta,PosixFilePermissions.fromString("rwx------"));Files.write(destino,arquivo.getBytes(),StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(destino,PosixFilePermissions.fromString("rw-------"));
   return tx.execute(s->{Object empresa=espelho.id("Cliente",c.cliente());if(empresa==null){espelho.salvar("Cliente",dados.registro("Cliente",c.cliente(),c),c);empresa=espelho.id("Cliente",c.cliente());}
    // O callback existente cria o Arquivo canônico com vínculo de propriedade.
    banco.um("SELECT set_config('firma.autentica_direcao','',true)",Map.of());
    banco.executar("INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento) VALUES(:id,:e,:n,:t,:s,:ch)",Map.of("id",id,"e",Long.parseLong(empresa.toString()),"n",nome,"t",mime,"s",arquivo.getSize(),"ch",id.toString()));
    var v=banco.um("SELECT destino_id FROM firma_autentica_vinculo WHERE origem='chat_arquivo' AND origem_id=:id",Map.of("id",id.toString()));return dados.registro("Arquivo",((Number)v.get("destino_id")).longValue(),c);
   });
  }catch(Exception e){try{Files.deleteIfExists(destino);}catch(Exception ignorado){}if(e instanceof RuntimeException r)throw r;throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível guardar o arquivo",e);}
 }


 @org.springframework.transaction.annotation.Transactional public Map<String,Object> url(String url,String nome,String mime,ServicoAutentica.Contexto c){dados.autorizar(c,"atendimentos","read");ServicoWebhooks.verificarDestino(url);RegrasAutentica.exigir(nome!=null&&!nome.isBlank()&&nome.length()<=255&&mime!=null&&mime.matches("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"),"Arquivo inválido");Object empresa=espelho.id("Cliente",c.cliente());if(empresa==null){espelho.salvar("Cliente",dados.registro("Cliente",c.cliente(),c),c);empresa=espelho.id("Cliente",c.cliente());}UUID id=UUID.randomUUID();banco.um("SELECT set_config('firma.autentica_direcao','',true)",Map.of());banco.executar("INSERT INTO chat_arquivo(id,empresa_id,nome,tipo_mime,tamanho,chave_armazenamento,url) VALUES(:id,:e,:nome,:mime,0,:ch,:url)",Map.of("id",id,"e",Long.parseLong(empresa.toString()),"nome",nome,"mime",mime,"ch",id.toString(),"url",url));var v=banco.um("SELECT destino_id FROM firma_autentica_vinculo WHERE origem='chat_arquivo' AND origem_id=:id",Map.of("id",id.toString()));return dados.registro("Arquivo",((Number)v.get("destino_id")).longValue(),c);}
 public Map<String,Object> base64(String base64,String nome,String tipo,ServicoAutentica.Contexto c){RegrasAutentica.exigir(base64!=null&&base64.length()<=21*1024*1024,"Arquivo maior que 15 MB");if(base64.startsWith("data:"))base64=base64.substring(base64.indexOf(',')+1);byte[] bytes=Base64.getDecoder().decode(base64);return guardar(new ConteudoArquivo(nome,tipo,bytes),c);}
 private record ConteudoArquivo(String nome,String tipo,byte[] bytes) implements MultipartFile {
  public String getName(){return "arquivo";}public String getOriginalFilename(){return nome;}public String getContentType(){return tipo;}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}public void transferTo(java.io.File destino)throws java.io.IOException{Files.write(destino.toPath(),bytes);}
 }
 public ServicoArquivosChat.Arquivo baixar(long id,ServicoAutentica.Contexto c){var r=dados.registro("Arquivo",id,c);String chave=Objects.toString(r.get("ArquivoChave"),"");if(!chave.matches("[a-fA-F0-9-]{36}")||!Files.isRegularFile(pasta.resolve(chave)))return remoto(r);
 try{return new ServicoArquivosChat.Arquivo(Objects.toString(r.get("ArquivoNome"),"arquivo"),Objects.toString(r.get("ArquivoMimeType"),"application/octet-stream"),Files.readAllBytes(pasta.resolve(chave)));}catch(Exception e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Arquivo indisponível");}}

 private ServicoArquivosChat.Arquivo remoto(Map<String,Object> r){String url=Objects.toString(r.get("ArquivoURL"),"");ServicoWebhooks.verificarDestino(url);try{var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();var resposta=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(30)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofInputStream());try(var in=resposta.body()){if(resposta.statusCode()!=200)throw new java.io.IOException();byte[] bytes=in.readNBytes(15*1024*1024+1);RegrasAutentica.exigir(bytes.length<=15*1024*1024,"Arquivo maior que 15 MB");return new ServicoArquivosChat.Arquivo(Objects.toString(r.get("ArquivoNome"),"arquivo"),Objects.toString(r.get("ArquivoMimeType"),"application/octet-stream"),bytes);}}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Arquivo remoto indisponível");}}
}
