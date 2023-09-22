package ru.idfedorov09.telegram.bot.fetcher

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.model.User
import ru.idfedorov09.telegram.bot.data.repo.UserPollingResultRepository
import ru.idfedorov09.telegram.bot.data.repo.UserRepository
import ru.idfedorov09.telegram.bot.entity.TelegramPollingBot
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.flow.InjectData
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import java.lang.RuntimeException
import java.time.LocalDateTime

@Component
class PollingContinueFetcher(
    private val userRepository: UserRepository,
    private val userPollingResultRepository: UserPollingResultRepository,
    private val redisService: RedisService,
) : GeneralFetcher() {

    // TODO: доработать фетчер!
    @InjectData
    fun doFetch(
        update: Update,
        bot: TelegramPollingBot,
        updatesUtil: UpdatesUtil,
        exp: ExpContainer,
    ) {
        val chatId = updatesUtil.getChatId(update) ?: return
        val user = userRepository.findByTui(chatId) ?: throw RuntimeException("user not found")
        val pollDate = redisService.getLastPollDate()

        when (user.currentQuestion) {
            1 -> presenceStage()
            2 -> understandingStage(update, chatId, pollDate, user)
            3 -> isNewKnowledgeStage()
            4 -> commentStage()
            else -> null
        }

        val testMsg = SendMessage()
        testMsg.chatId = chatId
        testMsg.text = "Выберите одну из кнопок:"
        val keyboard = createChooseKeyboard()
        testMsg.replyMarkup = keyboard
        bot.execute(testMsg)
        bot.execute(SendMessage(chatId, update.callbackQuery.data))
    }

    private fun createKeyboard(keyboard: List<List<InlineKeyboardButton>>) =
        InlineKeyboardMarkup().also { it.keyboard = keyboard }

    private fun createChooseKeyboard() = createKeyboard(
        listOf(
            listOf(
                InlineKeyboardButton("Да").also { it.callbackData = "yes" },
                InlineKeyboardButton("Нет").also { it.callbackData = "no" },
            ),
        ),
    )

    private fun presenceStage() {}

    private fun understandingStage(
        update: Update,
        chatId: String,
        pollDate: LocalDateTime,
        user: User,
    ) {
        if (!update.hasCallbackQuery()) return
        val percent = update.callbackQuery.data.toIntOrNull() ?: return
        // TODO: return? мб зарегать?) хотя странно. подумать
        val pollingResult = userPollingResultRepository.findByUserIdAndDate(chatId, pollDate) ?: return
        userPollingResultRepository.save(
            pollingResult.copy(
                understanding = percent,
            ),
        )
        userRepository.save(
            user.copy(
                currentQuestion = user.currentQuestion + 1,
            ),
        )
        // TODO: отправить следующее сообщение
    }

    private fun isNewKnowledgeStage() {}

    private fun commentStage() {}
}
