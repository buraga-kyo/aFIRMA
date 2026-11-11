ALTER TABLE signatario_envelope ADD COLUMN ip_conexao VARCHAR(64), ADD COLUMN origem_ip VARCHAR(40);
ALTER TABLE "AssinaturaSignatarios" ADD COLUMN "SignatarioIpConexao" VARCHAR(64), ADD COLUMN "SignatarioOrigemIp" VARCHAR(40);
