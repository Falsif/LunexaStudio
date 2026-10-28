package com.lunexa.studio.data

/** Изменение показателя за период по ручным снимкам. days — фактическая длина интервала. */
data class Delta(val value: Long, val days: Long)

object Stats {
    fun latest(list: List<ChannelSnapshot>): ChannelSnapshot? = list.maxByOrNull { it.dateEpochDay }

    /**
     * Берёт последний снимок и самый поздний снимок, сделанный не позже чем [days] дней до него.
     * Если такого нет — берёт самый ранний (и честно возвращает фактическое число дней).
     */
    fun delta(list: List<ChannelSnapshot>, days: Int, field: (ChannelSnapshot) -> Long): Delta? {
        val last = latest(list) ?: return null
        val earlier = list.filter { it.dateEpochDay < last.dateEpochDay }
        if (earlier.isEmpty()) return null
        val base = earlier.filter { it.dateEpochDay <= last.dateEpochDay - days }.maxByOrNull { it.dateEpochDay }
            ?: earlier.minBy { it.dateEpochDay }
        return Delta(field(last) - field(base), last.dateEpochDay - base.dateEpochDay)
    }
}
