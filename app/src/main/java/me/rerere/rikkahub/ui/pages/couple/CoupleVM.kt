package me.rerere.rikkahub.ui.pages.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.*
import me.rerere.rikkahub.data.repository.CoupleRepository

class CoupleVM(
    private val repository: CoupleRepository,
    settingsStore: SettingsStore,
) : ViewModel() {
    private val coupleAi = CoupleAiService()

    val settings = settingsStore.settingsFlow
    val relationship = repository.relationship.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val posts = relationship.flatMapLatest { it?.let { repository.posts(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val comments = relationship.flatMapLatest { it?.let { repository.comments(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val diaries = relationship.flatMapLatest { it?.let { repository.diaries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val anniversaries = relationship.flatMapLatest { it?.let { repository.anniversaries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(assistantId: String, startedAt: Long = System.currentTimeMillis()) = viewModelScope.launch {
        repository.bind(assistantId, startedAt)
    }

    fun addPost(content: String) {
        val relation = relationship.value ?: return
        viewModelScope.launch {
            val post = repository.addPost(relation.id, "user", content)
            coupleAi.commentOnUserPost(relation.assistantId, content)?.let { reply ->
                repository.addComment(relation.id, post.id, "assistant", reply)
            }
        }
    }

    fun addUserComment(post: CouplePostEntity, content: String) {
        val relation = relationship.value ?: return
        viewModelScope.launch {
            repository.addComment(relation.id, post.id, "user", content)
            if (post.author == "assistant") {
                coupleAi.replyToUserComment(
                    assistantId = relation.assistantId,
                    postContent = post.content,
                    userComment = content,
                )?.let { reply ->
                    repository.addComment(relation.id, post.id, "assistant", reply)
                }
            }
        }
    }

    fun maybeCreateAiPost(force: Boolean = false) {
        val relation = relationship.value ?: return
        val existingPosts = posts.value
        val latestAiPost = existingPosts.firstOrNull { it.author == "assistant" }
        val cooldownMs = 12 * 60 * 60 * 1000L
        if (!force && latestAiPost != null && System.currentTimeMillis() - latestAiPost.createdAt < cooldownMs) return

        viewModelScope.launch {
            val recentContext = existingPosts
                .take(8)
                .reversed()
                .joinToString("\n") { post ->
                    val who = if (post.author == "assistant") "你" else "恋人"
                    "$who：${post.content}"
                }
                .ifBlank { "这里还没有动态，你可以发第一条。" }

            coupleAi.createPost(relation.assistantId, recentContext)?.let { content ->
                repository.addPost(relation.id, "assistant", content)
            }
        }
    }

    fun toggleLike(post: CouplePostEntity) = viewModelScope.launch { repository.toggleLike(post) }

    fun addDiary(title: String, content: String) = relationship.value?.let { value ->
        viewModelScope.launch { repository.addDiary(value.id, "user", title, content, System.currentTimeMillis()) }
    }

    fun addAnniversary(title: String, date: Long = System.currentTimeMillis(), yearly: Boolean = true) = relationship.value?.let { value ->
        viewModelScope.launch { repository.addAnniversary(value.id, title, date, yearly) }
    }
}
