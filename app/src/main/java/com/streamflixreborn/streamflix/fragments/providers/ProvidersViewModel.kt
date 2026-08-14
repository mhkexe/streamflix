package com.streamflixreborn.streamflix.fragments.providers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.models.Provider as ModelProvider
import com.streamflixreborn.streamflix.providers.TmdbProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ProvidersViewModel : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: Flow<State> = _state

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val providers: List<ModelProvider>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getProviders()
    }

    fun getProviders() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            // Keep only TMDb Providers as requested, mapped to specific languages
            val languages = listOf("en", "es", "it", "fr", "de", "pl")
            
            val modelProviders = languages.map { lang ->
                val tmdbProvider = TmdbProvider(lang)
                val displayName = when (lang) {
                    "en" -> "English"
                    "es" -> "Spanish"
                    "it" -> "Italian"
                    "fr" -> "French"
                    "de" -> "German"
                    "pl" -> "Polish"
                    else -> Locale.forLanguageTag(lang).displayLanguage.replaceFirstChar { it.titlecase() }
                }
                
                val flagRes = when (lang) {
                    "en" -> "res://drawable/ic_flag_us"
                    "es" -> "res://drawable/ic_flag_es"
                    "it" -> "res://drawable/ic_flag_it"
                    "fr" -> "res://drawable/ic_flag_fr"
                    "de" -> "res://drawable/ic_flag_de"
                    "pl" -> "res://drawable/ic_flag_pl"
                    else -> "" 
                }

                ModelProvider(
                    name = displayName,
                    logo = flagRes,
                    language = lang,
                    provider = tmdbProvider,
                    isFavorite = false 
                )
            }

            _state.emit(State.SuccessLoading(modelProviders))
        } catch (e: Exception) {
            Log.e("ProvidersViewModel", "getProviders: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }
}
