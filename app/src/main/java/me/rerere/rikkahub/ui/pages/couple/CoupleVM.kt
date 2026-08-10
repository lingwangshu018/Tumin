package me.rerere.rikkahub.ui.pages.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val diaryFolders = relationship.flatMapLatest { it?.let { repository.diaryFolders(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val anniversaries = relationship.flatMapLatest { it?.let { repository.anniversaries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(assistantId: String, startedAt: Long = System.currentTimeMillis()) = viewModelScope.launch {
        repository.bind(assistantId, startedAt)
    }

    fun setJournalCover(cover: String) {
        val relation = relationship.value ?: return
        viewModelScope.launch { repository.updateJournalCover(relation, cover) }
    }

    fun setJournalInscription(title: String?, date: String?) {
        val relation = relationship.value ?: return
        viewModelScope.launch { repository.updateJournalInscription(relation, title, date) }
    }

    fun addPost(content: String, imageUris: List<String> = emptyList()) {
        val relation = relationship.value ?: return
        if (content.isBlank() && imageUris.isEmpty()) return
        viewModelScope.launch {
            val persistedImages = imageUris.take(9)
            val post = repository.addPost(relation.id, "user", content.trim(), persistedImages)
            coupleAi.commentOnUserPost(relation.assistantId, content.trim(), persistedImages)?.let { reply ->
                repository.addComment(relation.id, post.id, "assistant", reply)
            }
        }
    }

    fun addUserComment(post: CouplePostEntity, content: String) {
        val relation = relationship.value ?: return
        viewModelScope.launch {
            repository.addComment(relation.id, post.id, "user", content)
            if (post.author == "assistant") {
                coupleAi.replyToUserComment(relation.assistantId, post.content, content, decodeImageUris(post.imageUri))?.let { reply ->
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
            val recentContext = existingPosts.take(8).reversed().joinToString("\n") { post ->
                val who = if (post.author == "assistant") "你" else "恋人"
                val count = decodeImageUris(post.imageUri).size
                val photoHint = when { count > 1 -> "（带${count}张照片）"; count == 1 -> "（带1张照片）"; else -> "" }
                "$who$photoHint：${post.content}"
            }.ifBlank { "这里还没有动态，你可以发第一条。" }

            val draft = coupleAi.createPostDraft(relation.assistantId, recentContext) ?: return@launch
            if (draft.content.isBlank() && !draft.needImage) return@launch
            val generatedImages = if (draft.needImage && draft.imagePrompt.isNotBlank()) {
                coupleAi.generatePostImages(draft.imagePrompt, draft.imageCount)
            } else emptyList()
            if (draft.content.isNotBlank() || generatedImages.isNotEmpty()) {
                repository.addPost(relation.id, "assistant", draft.content.trim(), generatedImages)
            }
        }
    }

    fun toggleLike(post: CouplePostEntity) = viewModelScope.launch { repository.toggleLike(post) }

    fun addDiary(title: String, content: String, folder: String = "全部心事", paper: String = "ivory") = relationship.value?.let { value ->
        viewModelScope.launch {
            repository.addDiary(value.id, "user", title.trim(), content.trim(), System.currentTimeMillis(), folder.ifBlank { "全部心事" }, paper.ifBlank { "ivory" })
        }
    }

    fun updateDiary(entry: CoupleDiaryEntity, title: String, content: String, folder: String, paper: String) = viewModelScope.launch {
        repository.updateDiary(entry, title.trim(), content.trim(), folder.ifBlank { "全部心事" }, paper.ifBlank { "ivory" })
    }

    fun toggleDiaryBookmark(entry: CoupleDiaryEntity) = viewModelScope.launch {
        repository.toggleDiaryBookmark(entry)
    }

    fun addDiaryFolder(name: String) {
        val relation = relationship.value ?: return
        val clean = name.trim()
        if (clean.isBlank() || clean == "全部心事") return
        if (diaryFolders.value.any { it.name.equals(clean, true) }) return
        viewModelScope.launch { repository.addDiaryFolder(relation.id, clean, diaryFolders.value.size) }
    }

    fun renameDiaryFolder(folder: CoupleDiaryFolderEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank() || clean == "全部心事" || clean == folder.name) return
        if (diaryFolders.value.any { it.id != folder.id && it.name.equals(clean, true) }) return
        viewModelScope.launch { repository.renameDiaryFolder(folder, clean) }
    }

    fun deleteDiaryFolder(folder: CoupleDiaryFolderEntity) = viewModelScope.launch { repository.deleteDiaryFolder(folder) }

    fun requestDiaryReply(entry: CoupleDiaryEntity) {
        val relation = relationship.value ?: return
        viewModelScope.launch {
            val otherEntries = diaries.value.filter { it.id != entry.id }
            val memoryEntries = buildList {
                otherEntries
                    .filter { it.bookmarked }
                    .sortedByDescending { it.entryDate }
                    .take(3)
                    .forEach { add(it) }
                otherEntries
                    .filterNot { candidate -> any { it.id == candidate.id } }
                    .sortedByDescending { it.entryDate }
                    .take((5 - size).coerceAtLeast(0))
                    .forEach { add(it) }
            }
            val memoryContext = memoryEntries.joinToString("\n\n") { old ->
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(old.entryDate))
                val mark = if (old.bookmarked) "【书签】" else "【旧日记】"
                val excerpt = old.content.replace("\n", " ").take(260)
                "$mark $date《${old.title}》\n$excerpt"
            }

            coupleAi.replyToDiary(
                assistantId = relation.assistantId,
                title = entry.title,
                content = entry.content,
                memoryContext = memoryContext,
            )?.let { reply ->
                repository.saveDiaryReply(entry, reply, entry.replyPaper ?: "cream_letter")
            }
        }
    }

    fun setDiaryReplyPaper(entry: CoupleDiaryEntity, replyPaper: String) = viewModelScope.launch {
        repository.updateReplyPaper(entry, replyPaper)
    }

    fun addAnniversary(title: String, date: Long = System.currentTimeMillis(), yearly: Boolean = true) = relationship.value?.let { value ->
        viewModelScope.launch { repository.addAnniversary(value.id, title, date, yearly) }
    }

    private fun decodeImageUris(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
        }.getOrElse { listOf(raw) }.filter { it.isNotBlank() }.take(9)
    }
}
