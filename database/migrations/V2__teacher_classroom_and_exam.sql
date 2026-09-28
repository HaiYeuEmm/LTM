CREATE TABLE IF NOT EXISTS classrooms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_teacher_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    class_code VARCHAR(24) NULL,
    status ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_classrooms_class_code (class_code),
    KEY idx_classrooms_owner_status (owner_teacher_id, status),
    CONSTRAINT fk_classrooms_owner FOREIGN KEY (owner_teacher_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS class_memberships (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    classroom_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    status ENUM('ACTIVE', 'REMOVED') NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    removed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_class_membership (classroom_id, student_id),
    KEY idx_class_membership_student_status (student_id, status),
    CONSTRAINT fk_class_membership_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_membership_student FOREIGN KEY (student_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_banks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_teacher_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_question_banks_owner (owner_teacher_id),
    CONSTRAINT fk_question_banks_owner FOREIGN KEY (owner_teacher_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS questions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_bank_id BIGINT UNSIGNED NOT NULL,
    question_type ENUM('SINGLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER')
        NOT NULL DEFAULT 'SINGLE_CHOICE',
    content TEXT NOT NULL,
    explanation TEXT NULL,
    default_points DECIMAL(7,2) NOT NULL DEFAULT 1.00,
    status ENUM('ACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_questions_bank_status (question_bank_id, status),
    CONSTRAINT fk_questions_bank FOREIGN KEY (question_bank_id)
        REFERENCES question_banks (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_choices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id BIGINT UNSIGNED NOT NULL,
    content TEXT NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_question_choice_position (question_id, position),
    KEY idx_question_choices_question (question_id),
    CONSTRAINT fk_question_choices_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exams (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    owner_teacher_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(1000) NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_exams_owner_status (owner_teacher_id, status),
    CONSTRAINT fk_exams_owner FOREIGN KEY (owner_teacher_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_questions (
    exam_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    position SMALLINT UNSIGNED NOT NULL,
    points DECIMAL(7,2) NOT NULL,
    PRIMARY KEY (exam_id, question_id),
    UNIQUE KEY uq_exam_question_position (exam_id, position),
    CONSTRAINT fk_exam_questions_exam FOREIGN KEY (exam_id)
        REFERENCES exams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_questions_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exam_id BIGINT UNSIGNED NOT NULL,
    classroom_id BIGINT UNSIGNED NOT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    duration_minutes SMALLINT UNSIGNED NOT NULL,
    max_attempts TINYINT UNSIGNED NOT NULL DEFAULT 1,
    status ENUM('DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'GRADED')
        NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_exam_sessions_class_status (classroom_id, status, opens_at),
    KEY idx_exam_sessions_exam (exam_id),
    CONSTRAINT fk_exam_sessions_exam FOREIGN KEY (exam_id)
        REFERENCES exams (id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_sessions_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exam_session_id BIGINT UNSIGNED NOT NULL,
    student_id BIGINT UNSIGNED NOT NULL,
    attempt_number TINYINT UNSIGNED NOT NULL DEFAULT 1,
    state ENUM('NOT_STARTED', 'IN_PROGRESS', 'LOCKED', 'SUBMITTED', 'GRADED')
        NOT NULL DEFAULT 'NOT_STARTED',
    started_at DATETIME(3) NULL,
    submitted_at DATETIME(3) NULL,
    answer_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_session_student_number (exam_session_id, student_id, attempt_number),
    KEY idx_attempts_student_state (student_id, state),
    CONSTRAINT fk_attempts_exam_session FOREIGN KEY (exam_session_id)
        REFERENCES exam_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempts_student FOREIGN KEY (student_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attempt_answers (
    attempt_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    selected_choice_id BIGINT UNSIGNED NULL,
    answer_text TEXT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT fk_attempt_answers_attempt FOREIGN KEY (attempt_id)
        REFERENCES attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempt_answers_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempt_answers_choice FOREIGN KEY (selected_choice_id)
        REFERENCES question_choices (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exam_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT UNSIGNED NOT NULL,
    score DECIMAL(8,2) NOT NULL,
    max_score DECIMAL(8,2) NOT NULL,
    warning_count INT UNSIGNED NOT NULL DEFAULT 0,
    graded_by BIGINT UNSIGNED NULL,
    graded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_exam_results_attempt (attempt_id),
    KEY idx_exam_results_graded_by (graded_by),
    CONSTRAINT fk_exam_results_attempt FOREIGN KEY (attempt_id)
        REFERENCES attempts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_exam_results_grader FOREIGN KEY (graded_by)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS teacher_audit_logs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id BIGINT UNSIGNED NULL,
    details JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_teacher_audit_actor_time (actor_user_id, created_at),
    KEY idx_teacher_audit_entity (entity_type, entity_id),
    CONSTRAINT fk_teacher_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
