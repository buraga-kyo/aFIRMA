package com.afirma.assina;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ArmazenamentoMinio {
	private final MinioClient cliente;
	private final String bucket;

	public ArmazenamentoMinio(Environment ambiente) {
		String endpoint = ambiente.getProperty("ASSINEAI_MINIO_ENDPOINT", "");
		String usuario = ambiente.getProperty("ASSINEAI_MINIO_ACCESS_KEY", "");
		String senha = ambiente.getProperty("ASSINEAI_MINIO_SECRET_KEY", "");
		bucket = ambiente.getProperty("ASSINEAI_MINIO_BUCKET", "afirma-documentos");
		cliente = endpoint.isBlank() || usuario.isBlank() || senha.isBlank() ? null
				: MinioClient.builder().endpoint(endpoint).credentials(usuario, senha).build();
	}

	public boolean ativo() { return cliente != null; }

	public String guardar(UUID id, String versao, byte[] pdf) {
		if (cliente == null) throw new IllegalStateException("MinIO não configurado");
		String chave = "assinaturas/" + id + "/" + versao + "-" + UUID.randomUUID() + ".pdf";
		try {
			if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
				cliente.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
			cliente.putObject(PutObjectArgs.builder().bucket(bucket).object(chave)
					.stream(new ByteArrayInputStream(pdf), (long) pdf.length, -1L)
					.contentType("application/pdf").build());
			return chave;
		} catch (Exception erro) { throw new IllegalStateException("Falha ao guardar documento no MinIO", erro); }
	}

	public byte[] ler(String chave) {
		if (cliente == null) throw new IllegalStateException("MinIO não configurado");
		try (var dados = cliente.getObject(GetObjectArgs.builder().bucket(bucket).object(chave).build())) {
			return dados.readAllBytes();
		} catch (Exception erro) { throw new IllegalStateException("Falha ao ler documento do MinIO", erro); }
	}
}
