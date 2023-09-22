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
            1 -> presenceStage(update, chatId, bot, pollDate, user)
            2 -> understandingStage(update, chatId, bot, pollDate, user)
            3 -> isNewKnowledgeStage(update, chatId, bot, pollDate, user)
            4 -> commentStage(update, chatId, bot, pollDate, user)
            else -> null
        }
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

    private fun createShareKeyboard() = createKeyboard(
        listOf(
            listOf(
                InlineKeyboardButton("0").also { it.callbackData = "0" },
                InlineKeyboardButton("25").also { it.callbackData = "25" },
                InlineKeyboardButton("50").also { it.callbackData = "50" },
                InlineKeyboardButton("75").also { it.callbackData = "75" },
                InlineKeyboardButton("100").also { it.callbackData = "100" },
            ),
        ),
    )

    private fun presenceStage(
        update: Update,
        chatId: String,
        bot: TelegramPollingBot,
        pollDate: LocalDateTime,
        user: User,
    ) {
        if (!update.hasCallbackQuery()) return
        val answer = update.callbackQuery.data
        val pollingResult = userPollingResultRepository.findByUserIdAndDate(chatId, pollDate) ?: return

        if (answer == "yes") {
            userRepository.save(user.copy(currentQuestion = 2))
            userPollingResultRepository.save(
                pollingResult.copy(
                    presence = true,
                ),
            )
        } else if (answer == "no") {
            userRepository.save(user.copy(currentQuestion = 0))
            userPollingResultRepository.save(
                pollingResult.copy(
                    presence = false,
                ),
            )
        } else {
            return
        }

        val msg = SendMessage()
        msg.chatId = chatId
        msg.text = "На сколько процентов вы все поняли?:"
        val keyboard = createShareKeyboard()
        msg.replyMarkup = keyboard
        bot.execute(msg)
        bot.execute(SendMessage(chatId, update.callbackQuery.data))
    }

    private fun understandingStage(
        update: Update,
        chatId: String,
        bot: TelegramPollingBot,
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
                currentQuestion = 3,
            ),
        )
        bot.execute(
            SendMessage(chatId, "Были ли на занятии моменты, которые вы поняли только сейчас?")
                .also { it.replyMarkup = createChooseKeyboard() },
        )
    }

    private fun isNewKnowledgeStage(
        update: Update,
        chatId: String,
        bot: TelegramPollingBot,
        pollDate: LocalDateTime,
        user: User,
    ) {
        if (!update.hasCallbackQuery()) return
        val answer = update.callbackQuery.data
        val pollingResult = userPollingResultRepository.findByUserIdAndDate(chatId, pollDate) ?: return

        if (answer == "yes") {
            userRepository.save(user.copy(currentQuestion = 4))
            userPollingResultRepository.save(
                pollingResult.copy(
                    isNewKnowledge = true,
                ),
            )
        } else if (answer == "no") {
            userRepository.save(user.copy(currentQuestion = 0))
            userPollingResultRepository.save(
                pollingResult.copy(
                    isNewKnowledge = false,
                ),
            )
        } else {
            return
        }

        val msg = SendMessage()
        msg.chatId = chatId
        msg.text = "Напишите свой комментарий по занятию"
        bot.execute(msg)
    }

    private fun commentStage(
        update: Update,
        chatId: String,
        bot: TelegramPollingBot,
        pollDate: LocalDateTime,
        user: User,
    ) {
        if (!update.hasCallbackQuery()) return
        val answer = update.message.text
        val pollingResult = userPollingResultRepository.findByUserIdAndDate(chatId, pollDate) ?: return
        if (answer.isEmpty()) return
        userRepository.save(user.copy(currentQuestion = 0))
        userPollingResultRepository.save(
            pollingResult.copy(
                comment = answer,
            ),
        )
    }
}
