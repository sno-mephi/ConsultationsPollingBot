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

    companion object {
        private const val firstPollMessage = "\uD83D\uDCE3 Сегодня прошла консультация по математическому анализу.\n" +
            "Вы были на занятии?"
    }

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

        if (message == "/start") {
            userRepository.findByTui(chatId) ?: run {
                bot.execute(
                    SendMessage(
                        chatId,
                        "Привет! Это бот для обратной связи по консультациям " +
                            "[Студенческого Научного Общества](https://sno.mephi.ru/) \uD83E\uDD16" +
                            "Для того, чтобы мы могли сделать консультации лучше, пожалуйста, " +
                            "зарегистрируйся, написав номер своей группы в том же формате, что и на home.mephi.ru, " +
                            "например, Б23-105.",
                    ).also {
                        it.enableMarkdown(true)
                    },
                )
            }
            return
        }

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

        if (!user.isAdmin) return

        var currentDate = redisService.getLastPollDate()

        if (message == "/comments") {
            userRepository.findAll()
                .filter { it.tui != null }
                .associateWith { userPollingResultRepository.findByUserIdAndDate(it.tui!!, currentDate) }
                .filter { it.value?.comment != null }
                .forEach { curUser ->
                    bot.execute(
                        SendMessage(
                            chatId,
                            "\uD83D\uDCDD Комментарий представителя группы `${curUser.key.group}` " +
                                "по последнему занятию ($currentDate):\n${curUser.value?.comment}",
                        ).also { it.enableMarkdown(true) },
                    )
                    Thread.sleep(200L)
                }
        }
        if (message != "/poll") return

        currentDate = LocalDateTime.now(ZoneId.of("Europe/Moscow"))
        redisService.setLastPollDate(currentDate)
        currentDate = redisService.getLastPollDate()

        userRepository.findAll().forEach { user ->
            user.tui?.let {
                Thread.sleep(150L)
                userRepository.save(user.copy(currentQuestion = 1))
                userPollingResultRepository.save(
                    UserPollingResult(
                        userId = user.tui,
                        date = currentDate,
                    ),
                )
                val msg = SendMessage()
                msg.chatId = it
                msg.text = firstPollMessage
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
                InlineKeyboardButton("Да ✅").also { it.callbackData = "yes" },
                InlineKeyboardButton("Нет ❌").also { it.callbackData = "no" },
            ),
        )
        return keyboard
    }

    private fun generateAdminSecret() = DigestUtils.sha256Hex(LocalDateTime.now().toString())
}
