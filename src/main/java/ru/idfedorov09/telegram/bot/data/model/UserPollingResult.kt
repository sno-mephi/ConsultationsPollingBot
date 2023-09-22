package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users_polling_results")
data class UserPollingResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = -1,

    @Column(name = "user_id")
    val userId: Long = -1,

    @Column(name = "lesson_date")
    val date: LocalDateTime? = null,

    // присутствие
    @Column(name = "is_on_lesson")
    val presence: Boolean? = null,

    // процент понимания материала
    @Column(name = "understanding")
    val understanding: Int? = null,

    // было ли узнано чет новое
    @Column(name = "is_new_knowledge")
    val isNewKnowledge: Boolean? = null,

    // комментарий к занятию
    @Column(name = "comment", columnDefinition = "TEXT")
    val comment: String? = null,
)
