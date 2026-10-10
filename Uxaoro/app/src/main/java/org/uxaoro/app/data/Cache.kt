package org.uxaoro.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object Cache {
    private lateinit var dir: File
    private val gson = Gson()

    fun init(ctx: Context) {
        dir = File(ctx.applicationContext.filesDir, "offline")
        dir.mkdirs()
    }

    fun clear() {
        if (!::dir.isInitialized) return
        dir.listFiles()?.forEach { it.delete() }
    }

    fun clearChatData() {
        if (!::dir.isInitialized) return
        dir.listFiles()?.filter { it.name == "chats.json" || it.name.startsWith("msg_") }?.forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, key.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".json")

    fun put(key: String, value: Any) {
        if (!::dir.isInitialized) return
        runCatching { file(key).writeText(gson.toJson(value)) }
    }

    fun <T> get(key: String, cls: Class<T>): T? {
        if (!::dir.isInitialized) return null
        val f = file(key)
        if (!f.exists()) return null
        return runCatching { gson.fromJson(f.readText(), cls) }.getOrNull()
    }

    fun <T> getList(key: String, cls: Class<T>): List<T> {
        if (!::dir.isInitialized) return emptyList()
        val f = file(key)
        if (!f.exists()) return emptyList()
        val type = TypeToken.getParameterized(List::class.java, cls).type
        return runCatching { gson.fromJson<List<T>>(f.readText(), type) }.getOrNull().orEmpty()
    }

    fun saveFeed(items: List<PostOut>) = put("feed", items)
    fun loadFeed(): List<PostOut> = getList("feed", PostOut::class.java)

    fun saveFriends(items: List<UserPublic>) = put("friends", items)
    fun loadFriends(): List<UserPublic> = getList("friends", UserPublic::class.java)

    fun saveChats(items: List<ChatOut>) = put("chats", items)
    fun loadChats(): List<ChatOut> = getList("chats", ChatOut::class.java)

    fun saveMessages(chatId: String, items: List<CachedMsg>) = put("msg_$chatId", items)
    fun loadMessages(chatId: String): List<CachedMsg> = getList("msg_$chatId", CachedMsg::class.java)

    fun saveCommunities(items: List<CommunityOut>) = put("communities", items)
    fun loadCommunities(): List<CommunityOut> = getList("communities", CommunityOut::class.java)
    fun updateCommunity(community: CommunityOut) = saveCommunities(
        loadCommunities().map { if (it.id == community.id) community else it },
    )
    fun removeCommunity(id: String) = saveCommunities(loadCommunities().filterNot { it.id == id })

    fun saveStories(feed: StoryFeed) = put("stories", feed)
    fun loadStories(): StoryFeed = get("stories", StoryFeed::class.java) ?: StoryFeed()
}
