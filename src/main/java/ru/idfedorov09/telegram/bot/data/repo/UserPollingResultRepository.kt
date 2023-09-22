package ru.idfedorov09.telegram.bot.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import ru.idfedorov09.telegram.bot.data.model.UserPollingResult

interface UserPollingResultRepository : JpaRepository<UserPollingResult, Long> {
    fun findByUserId(userId: Long): List<UserPollingResult>
}
