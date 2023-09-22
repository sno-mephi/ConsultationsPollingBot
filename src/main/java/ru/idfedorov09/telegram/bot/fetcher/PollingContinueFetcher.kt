package ru.idfedorov09.telegram.bot.fetcher

import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import ru.idfedorov09.telegram.bot.data.repo.UserRepository
import ru.idfedorov09.telegram.bot.entity.TelegramPollingBot
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.flow.InjectData
import ru.idfedorov09.telegram.bot.service.RedisService
import ru.idfedorov09.telegram.bot.util.UpdatesUtil
import java.lang.RuntimeException

@Component
class PollingContinueFetcher(
    private val userRepository: UserRepository,
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
        if (!update.hasCallbackQuery()) return
        val chatId = updatesUtil.getChatId(update)
        val user = userRepository.findByTui(chatId) ?: throw RuntimeException("user not found")

        when (user.currentQuestion) {
            1 -> presenceStage()
            2 -> understandingStage()
            3 -> isNewKnowledgeStage()
            4 -> commentStage()
            else -> null
        }

        val testMsg = SendMessage()
        testMsg.chatId = chatId
        testMsg.text = "Выберите одну из кнопок:"
        val keyboard = createChoiceKeyboard()
        testMsg.replyMarkup = keyboard
        bot.execute(testMsg)
        bot.execute(SendMessage(chatId, update.callbackQuery.data))
    }

    private fun createChoiceKeyboard() =
        InlineKeyboardMarkup().also { keyboard ->
            keyboard.keyboard = listOf(
                listOf(
                    InlineKeyboardButton("Да").also { it.callbackData = "yes" },
                    InlineKeyboardButton("Нет").also { it.callbackData = "no" },
                ),
            )
        }

    private fun presenceStage() {}

    private fun understandingStage() {}

    private fun isNewKnowledgeStage() {}

    private fun commentStage() {}
}
