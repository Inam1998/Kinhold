package com.inamibrahim.kinhold.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inamibrahim.kinhold.model.Family
import com.inamibrahim.kinhold.model.PersonDraft
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Holds the stored tree for the screens and runs changes off the main thread. */
class KinholdViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = FamilyRepository(app)

    val family: StateFlow<Family> = repo.family

    /** False until the stored tree has been read, so the first screen is not wrongly shown as empty. */
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repo.load()
            loaded = true
        }
    }

    fun add(draft: PersonDraft, relation: Relation?, onAdded: (Int) -> Unit) {
        viewModelScope.launch { onAdded(repo.addPerson(draft, relation)) }
    }

    fun update(id: Int, draft: PersonDraft, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updatePerson(id, draft)
            onDone()
        }
    }

    fun delete(id: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deletePerson(id)
            onDone()
        }
    }

    fun addSampleFamily(count: Int) {
        viewModelScope.launch { repo.addSampleFamily(count) }
    }
}
