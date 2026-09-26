package com.bingo.app.master.entity;

import com.bingo.app.common.jackson.LocalDateTimeZoneSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id"),
        @Index(name = "idx_notifications_unread", columnList = "user_id, read_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /** i18n template key used by clients to render the notification in the user's language. */
    @Column(name = "message_key", length = 100)
    private String messageKey;

    /** JSON map of placeholders for the message_key template. */
    @Column(name = "message_params", columnDefinition = "TEXT")
    private String messageParams;

    @JsonSerialize(using = LocalDateTimeZoneSerializer.class)
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @JsonSerialize(using = LocalDateTimeZoneSerializer.class)
    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
