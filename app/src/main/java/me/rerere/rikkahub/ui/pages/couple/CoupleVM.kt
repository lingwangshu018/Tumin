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
    val settings = settingsStore.settingsFlow
    val relationship = repository.relationship.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val posts = relationship.flatMapLatest { it?.let { repository.posts(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val diaries = relationship.flatMapLatest { it?.let { repository.diaries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val anniversaries = relationship.flatMapLatest { it?.let { repository.anniversaries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(assistantId: String, startedAt: Long = System.currentTimeMillis()) = viewModelScope.launch { repository.bind(assistantId, startedAt) }
    fun addPost(content: String) = relationship.value?.let { value -> viewModelScope.launch { repository.addPost(value.id, "user", content) } }
    fun toggleLike(post: CouplePostEntity) = viewModelScope.launch { repository.toggleLike(post) }
    fun addDiary(title: String, content: String) = relationship.value?.let { value -> viewModelScope.launch { repository.addDiary(value.id, "user", title, content, System.currentTimeMillis()) } }
    fun addAnniversary(title: String, date: Long = System.currentTimeMillis(), yearly: Boolean = true) = relationship.value?.let { value -> viewModelScope.launch { repository.addAnniversary(value.id, title, date, yearly) } }
}
