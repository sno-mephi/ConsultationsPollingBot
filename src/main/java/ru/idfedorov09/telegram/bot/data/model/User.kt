package ru.idfedorov09.telegram.bot.data.model

import jakarta.persistence.*

@Entity
@Table(name = "users_table")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = -1,

    // id в телеге
    @Column(name = "true_user_id", columnDefinition = "TEXT")
    val tui: String? = null,

    // админ или нет
    @Column(name = "is_admin")
    val isAdmin: Boolean = false,

    // номер группы
    @Column(name = "study_group", columnDefinition = "TEXT")
    val group: String? = null,

    // текущий номер вопроса (0 - нет вопроса)
    @Column(name = "current_question")
    val currentQuestion: Int = 0,
)
