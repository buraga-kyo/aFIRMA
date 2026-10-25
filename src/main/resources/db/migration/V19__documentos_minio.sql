ALTER TABLE envelope_assinatura ADD COLUMN pdf_original_chave_minio VARCHAR(255);
ALTER TABLE envelope_assinatura ADD COLUMN pdf_atual_chave_minio VARCHAR(255);
ALTER TABLE envelope_assinatura ADD COLUMN pdf_final_chave_minio VARCHAR(255);
ALTER TABLE envelope_assinatura ALTER COLUMN pdf_original DROP NOT NULL;
ALTER TABLE envelope_assinatura ADD CONSTRAINT pdf_original_existe CHECK (pdf_original IS NOT NULL OR pdf_original_chave_minio IS NOT NULL);
