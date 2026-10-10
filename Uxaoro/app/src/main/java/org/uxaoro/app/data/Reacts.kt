package org.uxaoro.app.data

/** The standard Unicode reaction pack (not custom/premium emoji). */
object Reacts {
    val all: List<String> = listOf(
        "👍", "👎", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢",
        "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊️", "🤡", "🥱", "🥴", "😍", "🐳",
        "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓",
        "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "🙈",
        "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄", "☃️", "💅", "🤪", "🗿",
        "🆒", "💘", "🙉", "🦄", "😘", "💊", "🙊", "😎", "👾", "🤷‍♂️", "🤷", "🤷‍♀️", "😡",
    )

    /** The quick-reaction row shown above the full grid (like Telegram). */
    val quick: List<String> = all.take(8)

    fun key(e: String?) = e.orEmpty().replace("\uFE0F", "")

    fun same(a: String?, b: String?) = key(a) == key(b) && key(a).isNotEmpty()
}
