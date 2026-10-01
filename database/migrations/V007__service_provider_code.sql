BEGIN;

ALTER TABLE service_provider ADD COLUMN code TEXT;
UPDATE service_provider SET code = 'PROVIDER_' || id WHERE code IS NULL;
ALTER TABLE service_provider ALTER COLUMN code SET NOT NULL;
ALTER TABLE service_provider ADD CONSTRAINT uq_service_provider_code UNIQUE (code);
ALTER TABLE service_provider ADD CONSTRAINT ck_service_provider_code_nonblank CHECK (BTRIM(code) <> '');

COMMIT;
