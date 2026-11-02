package com.afirma.assina;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** O transporte pode ter aceitado a mensagem; repetir automaticamente pode duplicá-la. */
public class EntregaIndeterminada extends ResponseStatusException {
 public EntregaIndeterminada(){super(HttpStatus.BAD_GATEWAY,"Envio sem confirmação; confira o provedor antes de repetir");}
}
