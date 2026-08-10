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

    fun addPost(content: String, imageUris: List<String> = emptyList()) {
        val relation = relationship.value ?: return
        if (content.isBlank() && imageUris.isEmpty()) return
        viewModelScope.launch {
            val persistedImages = imageUris.take(9)
            val post = repository.addPost(relation.id, "user", content.trim(), persistedImages)
            coupleAi.commentOnUserPost(
                assistantId = relation.assistantId,
                postContent = content.trim(),
                imageUris = persistedImages,
            )?.let { reply ->
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
                    imageUris = decodeImageUris(post.imageUri),
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
                    val count = decodeImageUris(post.imageUri).size
                    val photoHint = when {
                        count > 1 -> "（带${count}张照片）"
                        count == 1 -> "（带1张照片）"
                        else -> ""
                    }
                    "$who$photoHint：${post.content}"
                }
                .ifBlank { "这里还没有动态，你可以发第一条。" }

            val draft = coupleAi.createPostDraft(relation.assistantId, recentContext)
                ?: return@launch
            if (draft.content.isBlank() && !draft.needImage) return@launch

            val generatedImages = if (draft.needImage && draft.imagePrompt.isNotBlank()) {
                coupleAi.generatePostImages(
                    prompt = draft.imagePrompt,
                    count = draft.imageCount,
                )
            } else {
                emptyList()
            }

            if (draft.content.isNotBlank() || generatedImages.isNotEmpty()) {
                repository.addPost(
                    relationshipId = relation.id,
                    author = "assistant",
                    content = draft.content.trim(),
                    imageUris = generatedImages,
                )
            }
        }
    }

    fun toggleLike(post: CouplePostEntity) = viewModelScope.launch { repository.toggleLike(post) }

    fun addDiary(
        title: String,
        content: String,
        folder: String = "全部心事",
        paper: String = "ivory",
    ) = relationship.value?.let { value ->
        viewModelScope.launch {
            repository.addDiary(
                relationshipId = value.id,
                author = "user",
                title = title.trim(),
                content = content.trim(),
                date = System.currentTimeMillis(),
                folder = folder.ifBlank { "全部心事" },
                paper = paper.ifBlank { "ivory" },
            )
        }
    }

    fun requestDiaryReply(entry: CoupleDiaryEntity) {
        val relation = relationship.value ?: return
        viewModelScope.launch {
            coupleAi.replyToDiary(
                assistantId = relation.assistantId,
                title = entry.title,
                content = entry.content,
            )?.let { reply ->
                repository.saveDiaryReply(entry, reply)
            }
        }
    }

    fun addAnniversary(title: String, date: Long = System.currentTimeMillis(), yearly: Boolean = true) = relationship.value?.let { value ->
        viewModelScope.launch { repository.addAnniversary(value.id, title, date, yearly) }
    }

    private fun decodeImageUris(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
        }.getOrElse {
            listOf(raw)
        }.filter { it.isNotBlank() }.take(9)
    }
}
