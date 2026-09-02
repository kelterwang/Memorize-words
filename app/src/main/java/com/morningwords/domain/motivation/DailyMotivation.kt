package com.morningwords.domain.motivation

val DAILY_QUOTES = listOf(
    "为人民服务",
    "实事求是",
    "自力更生，艰苦奋斗",
    "好好学习，天天向上",
    "星星之火，可以燎原",
    "一切反动派都是纸老虎",
    "人民万岁",
    "团结起来，争取更大的胜利",
    "没有调查，没有发言权",
    "实践是检验真理的标准",
    "虚心使人进步，骄傲使人落后",
    "世上无难事，只要肯登攀",
    "人是要有一点精神的",
    "自信人生二百年",
    "会当水击三千里",
    "不管风吹浪打，胜似闲庭信步",
    "军民团结如一人",
    "宜将剩勇追穷寇",
    "不可沽名学霸王",
    "雄关漫道真如铁",
    "而今迈步从头越",
    "无限风光在险峰",
    "风物长宜放眼量",
    "牢骚太盛防肠断",
    "天若有情天亦老",
    "人间正道是沧桑",
    "数风流人物，还看今朝",
    "一万年太久，只争朝夕",
    "独有英雄驱虎豹",
    "敢教日月换新天",
    "踏遍青山人未老",
)

data class DailyQuoteSelection(
    val quoteIndex: Int,
    val remainingIndices: Set<Int>,
    val changed: Boolean,
)

fun greetingForHour(hour: Int): String = when (hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..23 -> "晚上好"
    else -> "夜深了"
}

fun selectDailyQuote(
    today: String,
    previousDate: String?,
    previousIndex: Int?,
    remainingIndices: Set<Int>,
    randomValue: Int,
    quoteCount: Int = DAILY_QUOTES.size,
): DailyQuoteSelection {
    require(quoteCount > 0)
    if (today == previousDate && previousIndex != null && previousIndex in 0 until quoteCount) {
        return DailyQuoteSelection(previousIndex, remainingIndices, changed = false)
    }
    val validRemaining = remainingIndices.filterTo(sortedSetOf()) { it in 0 until quoteCount }
    val pool = if (validRemaining.isEmpty()) (0 until quoteCount).toSortedSet() else validRemaining
    val eligible = if (validRemaining.isEmpty() && quoteCount > 1 && previousIndex != null && previousIndex in pool) pool - previousIndex else pool
    val selected = eligible.elementAt(Math.floorMod(randomValue, eligible.size))
    return DailyQuoteSelection(selected, pool - selected, changed = true)
}
