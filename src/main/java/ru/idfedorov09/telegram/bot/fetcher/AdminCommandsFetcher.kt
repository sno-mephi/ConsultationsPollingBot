package ru.idfedorov09.telegram.bot.fetcher

import org.telegram.telegrambots.meta.api.objects.Update
import ru.idfedorov09.telegram.bot.entity.TelegramPollingBot
import ru.idfedorov09.telegram.bot.flow.ExpContainer
import ru.idfedorov09.telegram.bot.flow.InjectData
import ru.idfedorov09.telegram.bot.util.UpdatesUtil

class AdminCommandsFetcher() : GeneralFetcher() {

    @InjectData
    fun doFetch(
        update: Update,
        bot: TelegramPollingBot,
        updatesUtil: UpdatesUtil,
        exp: ExpContainer,
    ) {
        if (!exp.allowAdminCommands) return
    }
}
