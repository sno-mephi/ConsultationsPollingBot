package ru.idfedorov09.telegram.bot.fetcher

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.model.User
import ru.idfedorov09.telegram.bot.data.model.UserPollingResult
import ru.idfedorov09.telegram.bot.data.repo.UserPollingResultRepository
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
    private val redisService: RedisService,
    private val userPollingResultRepository: UserPollingResultRepository,
) : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        bot: TelegramPollingBot,
        updatesUtil: UpdatesUtil,
        exp: ExpContainer,
    ) {
        // админам можно взаимодействовать с ботом только через команды
        // ПЕРВОЕ СООБЩЕНИЕ СОДЕРЖИТ НОМЕР ГРУППЫ
        val message = updatesUtil.getText(update)?.lowercase() ?: return
        val chatId = updatesUtil.getChatId(update) ?: return

        // первое сообщение поьзователя обязательно должно быть текстовым - если нет, сохранение в бд не будет!
        val user = userRepository.findByTui(chatId) ?: User().also {
            if (!isCorrectStudyGroup(message)) {
                bot.execute(
                    SendMessage(
                        chatId,
                        "Вы еще не зарегистрированы. Пожалуйста, укажите корректный номер группы для регистрации.",
                    ),
                )
                return
            }
            userRepository.save(
                it.copy(
                    tui = chatId,
                    group = message,
                ),
            )
            bot.execute(
                SendMessage(
                    chatId,
                    "Вы успешно зарегистрированы!",
                ),
            )
        }

        exp.isValidCommand = true
        exp.isCurrentCommandByAdmin = user.isAdmin

        val secretAdminKey = redisService.getSafe("admin_secret")

        // команда для того чтобы стать админом. Работает только с экспом!
        // TODO:  а если чел уже был админом?)
        if (exp.allowSpecialCommands && (message == secretAdminKey || (secretAdminKey == null && message == "/be_admin"))) {
            userRepository.save(user.copy(isAdmin = true))
            val newSecret = generateAdminSecret()
            redisService.setValue("admin_secret", newSecret)
            bot.execute(
                SendMessage(
                    chatId,
                    "Вы стали администратором.",
                ),
            )
            bot.execute(
                SendMessage(
                    "920061911",
                    "Сгенерирован новый секрет: " +
                        "`$newSecret`",
                ).also { it.enableMarkdown(true) },
            )
        }

        // от админов разрешена только одна команда - старт опроса. Если это не она - скипаем фетчер
        if (message != "/poll") return
        if (!user.isAdmin) return

        var currentDate = LocalDateTime.now(ZoneId.of("Europe/Moscow"))
        redisService.setLastPollDate(currentDate)
        currentDate = redisService.getLastPollDate()

        userRepository.findAll().forEach { user ->
            user.tui?.let {
                userRepository.save(user.copy(currentQuestion = 1))
                userPollingResultRepository.save(
                    UserPollingResult(
                        userId = user.tui,
                        date = currentDate,
                    ),
                )
                val msg = SendMessage()
                msg.chatId = it
                msg.text = "Сегодня прошла консультация по математическому анализу. " +
                    "Вы были на занятии?"
                val keyboard = createChoiceKeyboard()
                msg.replyMarkup = keyboard
                bot.execute(msg)
            }
        }
    }

    private fun isCorrectStudyGroup(input: String): Boolean {
        val regex = Regex("[бс]23-\\d{3}")
        return regex.matches(input)
    }

    private fun createChoiceKeyboard(): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                InlineKeyboardButton("Да").also { it.callbackData = "yes" },
                InlineKeyboardButton("Нет").also { it.callbackData = "no" },
            ),
        )
        return keyboard
    }

    private fun generateAdminSecret() = DigestUtils.sha256Hex(LocalDateTime.now().toString())
}
