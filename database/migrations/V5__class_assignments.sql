CREATE TABLE IF NOT EXISTS class_assignments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    classroom_id BIGINT UNSIGNED NOT NULL,
    teacher_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NULL,
    original_file_name VARCHAR(255) NULL,
    content_type VARCHAR(160) NULL,
    file_size BIGINT UNSIGNED NULL,
    file_data MEDIUMBLOB NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_class_assignments_class_created (classroom_id, created_at),
    CONSTRAINT fk_class_assignments_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_assignments_teacher FOREIGN KEY (teacher_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
