CREATE DATABASE IF NOT EXISTS criminal_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE criminal_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role VARCHAR(32) NOT NULL,
    id_number VARCHAR(80) NULL,
    id_proof_path VARCHAR(1024) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_notifications_read_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    KEY idx_users_id_number (id_number),
    KEY idx_users_role_status (role, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_number VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NULL,
    crime_type VARCHAR(100) NULL,
    location_jurisdiction VARCHAR(255) NULL,
    date_reported DATE NULL,
    priority VARCHAR(32) NULL,
    victims TEXT NULL,
    incident_description MEDIUMTEXT NULL,
    investigation_notes MEDIUMTEXT NULL,
    rejected_officers TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_officer VARCHAR(80) NULL,
    created_by VARCHAR(120) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cases_case_number (case_number),
    KEY idx_cases_status (status),
    KEY idx_cases_assigned_officer (assigned_officer),
    KEY idx_cases_created_by (created_by),
    KEY idx_cases_date_reported (date_reported)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS case_victims (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_number VARCHAR(64) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    age SMALLINT UNSIGNED NULL,
    dob DATE NULL,
    gender VARCHAR(40) NULL,
    phone VARCHAR(40) NULL,
    address VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_victims_case_number (case_number),
    CONSTRAINT fk_case_victims_case_number
        FOREIGN KEY (case_number) REFERENCES cases (case_number)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS case_evidence (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    case_id BIGINT UNSIGNED NULL,
    case_number VARCHAR(64) NOT NULL,
    evidence_number VARCHAR(80) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NULL,
    file_name VARCHAR(255) NULL,
    file_path TEXT NOT NULL,
    collected_by VARCHAR(80) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING REVIEW',
    notes MEDIUMTEXT NULL,
    revision_number INT UNSIGNED NOT NULL DEFAULT 1,
    is_resubmission TINYINT(1) NOT NULL DEFAULT 0,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_case_evidence_case_id (case_id),
    KEY idx_case_evidence_case_number (case_number),
    KEY idx_case_evidence_status (status),
    KEY idx_case_evidence_uploaded_at (uploaded_at),
    CONSTRAINT fk_case_evidence_case_id
        FOREIGN KEY (case_id) REFERENCES cases (id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS suspect_faces (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    suspect_name VARCHAR(160) NOT NULL,
    case_number VARCHAR(64) NULL,
    image_path VARCHAR(1024) NULL,
    embedding MEDIUMTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_suspect_faces_case_number (case_number),
    KEY idx_suspect_faces_name (suspect_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user` VARCHAR(120) NOT NULL DEFAULT 'SYSTEM',
    user_id VARCHAR(120) GENERATED ALWAYS AS (`user`) STORED,
    action_text MEDIUMTEXT NULL,
    action MEDIUMTEXT NULL,
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_logs_timestamp (`timestamp`),
    KEY idx_activity_logs_user_id (user_id)
) ENGINE=InnoDB;