-- A instalação local já aplicou V45. Mantém os registros e os nomes esperados
-- pelo trator do GitHub ao incorporar as novas evidências da assinatura.
ALTER TABLE firma_cartorio_identidade RENAME TO firma_assineai_identidade;
ALTER TABLE firma_cartorio_controle RENAME TO firma_assineai_controle;
ALTER TABLE firma_cartorio_codigo RENAME TO firma_assineai_codigo;
ALTER TABLE firma_cartorio_marca RENAME TO firma_assineai_marca;
ALTER INDEX firma_cartorio_criador RENAME TO firma_assineai_criador;
