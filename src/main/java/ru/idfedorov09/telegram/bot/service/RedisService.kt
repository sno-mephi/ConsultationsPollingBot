package ru.idfedorov09.telegram.bot.service

import com.google.gson.Gson
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import redis.clients.jedis.Jedis
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class RedisService @Autowired constructor(private val jedis: Jedis, private val gson: Gson) {

    companion object {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    fun <T> getValue(key: String?, type: Class<T>?): T {
        val jsonValue = jedis[key]
        return gson.fromJson(jsonValue, type)
    }

    fun getValue(key: String?): String? {
        return jedis[key]
    }

    fun setValue(key: String, value: String?) {
        jedis[key] = value
    }

    fun setLastPollDate(date: LocalDateTime) {
        val dateTimeStr = date.format(formatter)
        jedis.set("last_poll_date", dateTimeStr)
    }

    fun getLastPollDate() = LocalDateTime.parse(jedis.get("last_poll_date"), formatter)
}
