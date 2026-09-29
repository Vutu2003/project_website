BEGIN;

CREATE TABLE department (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_department PRIMARY KEY,
    code TEXT NOT NULL CONSTRAINT uq_department_code UNIQUE,
    name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_department_code_nonblank CHECK (BTRIM(code) <> ''),
    CONSTRAINT ck_department_name_nonblank CHECK (BTRIM(name) <> '')
);

CREATE TABLE service_provider (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_service_provider PRIMARY KEY,
    name TEXT NOT NULL,
    contact_details TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_service_provider_name_nonblank CHECK (BTRIM(name) <> '')
);

CREATE TABLE user_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_user_account PRIMARY KEY,
    department_id BIGINT,
    role_code TEXT NOT NULL,
    username TEXT NOT NULL CONSTRAINT uq_user_account_username UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_account_department FOREIGN KEY (department_id)
        REFERENCES department(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_user_account_role CHECK (role_code IN ('PHONG_VTYT', 'BAN_GIAM_DOC', 'KHOA_PHONG', 'ADMIN')),
    CONSTRAINT ck_user_account_department_scope CHECK (role_code <> 'KHOA_PHONG' OR department_id IS NOT NULL),
    CONSTRAINT ck_user_account_username_nonblank CHECK (BTRIM(username) <> ''),
    CONSTRAINT ck_user_account_hash_nonblank CHECK (BTRIM(password_hash) <> ''),
    CONSTRAINT ck_user_account_display_name_nonblank CHECK (BTRIM(display_name) <> '')
);

CREATE TABLE equipment (
    id BIGINT GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_equipment PRIMARY KEY,
    department_id BIGINT NOT NULL,
    equipment_code TEXT NOT NULL CONSTRAINT uq_equipment_code UNIQUE,
    name TEXT NOT NULL,
    serial_number TEXT,
    model TEXT,
    technical_spec TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_equipment_department FOREIGN KEY (department_id)
        REFERENCES department(id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_equipment_code_nonblank CHECK (BTRIM(equipment_code) <> ''),
    CONSTRAINT ck_equipment_name_nonblank CHECK (BTRIM(name) <> '')
);

COMMIT;
