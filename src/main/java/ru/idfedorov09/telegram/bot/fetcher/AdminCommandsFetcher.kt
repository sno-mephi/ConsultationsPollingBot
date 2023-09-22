package ru.idfedorov09.telegram.bot.fetcher

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.data.model.User
import ru.idfedorov09.telegram.bot.data.repo.UserRepository
import ru.idfedorov09.telegram.bot.entity.TelegramPollingBot
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.flow.InjectData
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class AdminCommandsFetcher(
    private val userRepository: UserRepository,
    private val redisService: RedisService
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        bot: TelegramPollingBot,
        updatesUtil: UpdatesUtil,
        exp: ExpContainer,
    ) {
        // админам можно взаимодействовать с ботом только через команды
        val message = updatesUtil.getText(update)?.lowercase() ?: return
        val chatId = updatesUtil.getChatId(update) ?: return

        // первое сообщение поьзователя обязательно должно быть текстовым - если нет, сохранение в бд не будет!
        val user = userRepository.findByTui(chatId) ?: User().also {
            userRepository.save(
                it.copy(
                    tui = chatId,
                ),
            )
        }

        exp.isValidCommand = true
        exp.isCurrentCommandByAdmin = user.isAdmin

        // команда для того чтобы стать админом. Работает только с экспом!
        if (exp.allowSpecialCommands && message == "/be_admin") {
            userRepository.save(user.copy(isAdmin = true))
        }

        // от админов разрешена только одна команда - старт опроса. Если это не она - скипаем фетчер
        if (message != "/poll") return
        redisService.setLastPollDate(LocalDateTime.now(ZoneId.of("Europe/Moscow")))

        // TODO: здесь пройтись по пользователям и разослать им сообщение о начале опроса о занятии
    }
}
