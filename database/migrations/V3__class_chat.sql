CREATE TABLE IF NOT EXISTS chat_rooms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    classroom_id BIGINT UNSIGNED NOT NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_rooms_classroom (classroom_id),
    CONSTRAINT fk_chat_rooms_classroom FOREIGN KEY (classroom_id)
        REFERENCES classrooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_rooms_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_room_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    member_role ENUM('TEACHER', 'STUDENT') NOT NULL,
    status ENUM('ACTIVE', 'LEFT') NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    left_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_room_member (room_id, user_id),
    KEY idx_chat_member_user_status (user_id, status),
    CONSTRAINT fk_chat_members_room FOREIGN KEY (room_id)
        REFERENCES chat_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_members_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_id BIGINT UNSIGNED NOT NULL,
    sender_id BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    edited_at TIMESTAMP(3) NULL,
    deleted_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_chat_message_request (sender_id, request_id),
    KEY idx_chat_messages_room_created (room_id, created_at, id),
    CONSTRAINT fk_chat_messages_room FOREIGN KEY (room_id)
        REFERENCES chat_rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
