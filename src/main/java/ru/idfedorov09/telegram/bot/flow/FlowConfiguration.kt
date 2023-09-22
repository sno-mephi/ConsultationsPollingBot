package ru.idfedorov09.telegram.bot.flow

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.idfedorov09.telegram.bot.fetcher.AdminCommandsFetcher

/**
 * Основной класс, в котором строится последовательность вычислений (граф)
 */
@Configuration
open class FlowConfiguration() {
    /**
     * Возвращает построенный граф; выполняется только при запуске приложения
     */
    @Bean(name = ["flowBuilder"])
    open fun flowBuilder(): FlowBuilder {
        val flowBuilder = FlowBuilder()
        flowBuilder.buildFlow()
        return flowBuilder
    }

    @Autowired
    private lateinit var adminCommandsFetcher: AdminCommandsFetcher

    open fun FlowBuilder.buildFlow() {
        group {
            fetch(adminCommandsFetcher)
            // ветвь отвечающая за опрос. Админы не участвуют :)
            whenComplete(condition = { !exp.isCurrentCommandByAdmin && exp.isValidCommand}) {
            }
        }
    }
}
