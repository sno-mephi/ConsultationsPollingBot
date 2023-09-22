package ru.idfedorov09.telegram.bot.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.idfedorov09.telegram.bot.data.model.UserPollingResult
import java.time.LocalDateTime

interface UserPollingResultRepository : JpaRepository<UserPollingResult, String> {
    fun findByUserId(userId: String): List<UserPollingResult>

    fun findByUserIdAndDate(userId: String, date: LocalDateTime): UserPollingResult?
}
