package com.thelightphone.sanntid

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightCoordinates
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SanntidScreenMode {
    data object Loading : SanntidScreenMode()

    data object SearchInput : SanntidScreenMode()

    data class SearchResults(
        val query: String,
        val results: List<StopPlace>,
    ) : SanntidScreenMode()

    data class Departures(
        val stop: StopPlace,
        val groups: List<ModeGroup>,
    ) : SanntidScreenMode()

    /** The "BYTT STED" chooser: favourites first, then the two ways of finding a new stop. */
    data object StopPicker : SanntidScreenMode()

    data class NearbyResults(val stops: List<NearbyStop>) : SanntidScreenMode()
}

data class SanntidUiState(
    val mode: SanntidScreenMode = SanntidScreenMode.Loading,
    val canCancelSearch: Boolean = false,
    val editorSession: Int = 0,
    val errorModal: String? = null,
    val favourites: List<StopPlace> = emptyList(),
    val language: AppLanguage = AppLanguage.Default,
) {
    val strings: SanntidStrings get() = stringsFor(language)
}

private const val LOG_TAG = "Sanntid"


class SanntidViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = SanntidApi()

    private val _uiState = MutableStateFlow(SanntidUiState())
    val uiState: StateFlow<SanntidUiState> = _uiState.asStateFlow()

    private var currentStop: StopPlace? = null
    private var skipRefreshOnNextScreenShow = true

    private val apiExceptionHandler = CoroutineExceptionHandler { _, error ->
        viewModelScope.launch(Dispatchers.Main) { showApiFailure(error) }
    }

    init {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            runCatching { loadStoredState() }
                .onFailure { showSearchInput(canCancel = false) }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (skipRefreshOnNextScreenShow) {
            skipRefreshOnNextScreenShow = false
            return
        }
        resetToMainScreen()
    }

    /**
     * On every re-entry, return to the saved stop's departures (the main screen) rather
     * than reopening on whatever subpage (search input/results) was last shown, then
     * refresh. The saved stop is kept — no need to search again. No-op when there is no
     * saved stop yet, so first-time setup stays on the search screen.
     *
     * A loading screen is shown only when we're coming from a subpage (nothing to display
     * during the fetch); when already on departures we refresh in place. Either way a
     * failed refresh drops to the search screen with an error (see [showApiFailure]),
     * never an endless Loading state — and unlike weather there is no conditional/304
     * caching here that could otherwise strand us on Loading.
     */
    private fun resetToMainScreen() {
        val stop = currentStop ?: return
        val onDepartures = _uiState.value.mode is SanntidScreenMode.Departures
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshDepartures(stop, showLoadingScreen = !onDepartures)
        }
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private suspend fun updateState(transform: (SanntidUiState) -> SanntidUiState) {
        withContext(Dispatchers.Main) { _uiState.update(transform) }
    }

    private suspend fun showSearchInput(canCancel: Boolean, error: String? = null) {
        updateState {
            it.copy(
                mode = SanntidScreenMode.SearchInput,
                canCancelSearch = canCancel,
                editorSession = it.editorSession + 1,
                errorModal = error,
            )
        }
    }

    private suspend fun showApiFailure(error: Throwable? = null) {
        // Log the real cause: every failure collapses into one generic user-facing string,
        // so without this a parse error, a 500 and a genuine outage are indistinguishable.
        // Cost a long debugging detour in the Messages tool on 2026-07-28.
        android.util.Log.w(LOG_TAG, "API call failed: ${error?.javaClass?.simpleName}: ${error?.message}", error)
        if (_uiState.value.mode is SanntidScreenMode.Loading) {
            // No screen behind the modal — drop to search so the user can retry.
            showSearchInput(canCancel = currentStop != null, error = _uiState.value.strings.networkError)
        } else {
            updateState { it.copy(errorModal = it.strings.networkError) }
        }
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private suspend fun loadStoredState() {
        val prefs = dataStore.data.first()
        val stored = decodeFavourites(prefs[SanntidPreferences.FAVOURITES])
        val language = AppLanguage.fromCode(prefs[SanntidPreferences.LANGUAGE])
        updateState { it.copy(favourites = stored, language = language) }

        val id = prefs[SanntidPreferences.STOP_ID]
        val name = prefs[SanntidPreferences.STOP_NAME]
        if (id == null || name == null) {
            showSearchInput(canCancel = false)
            return
        }
        val stop = StopPlace(id, name)
        currentStop = stop
        refreshDepartures(stop, showLoadingScreen = true)
    }

    private suspend fun refreshDepartures(stop: StopPlace, showLoadingScreen: Boolean) {
        if (showLoadingScreen) {
            updateState { it.copy(mode = SanntidScreenMode.Loading) }
        }
        api.fetchDepartures(stop.id).fold(
            onSuccess = { departures ->
                updateState {
                    it.copy(
                        mode = SanntidScreenMode.Departures(stop, departures.groupedByMode()),
                        canCancelSearch = true,
                    )
                }
            },
            onFailure = { showApiFailure(it) },
        )
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    fun submitSearch(rawText: CharSequence) {
        val text = rawText.toString().trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorModal = it.strings.emptySearch) }
            return
        }
        val canCancel = _uiState.value.canCancelSearch
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            updateState { it.copy(mode = SanntidScreenMode.Loading, errorModal = null) }
            api.searchStopPlaces(text, size = 5).fold(
                onSuccess = { stops ->
                    updateState {
                        it.copy(mode = SanntidScreenMode.SearchResults(query = text, results = stops))
                    }
                },
                onFailure = { error ->
                    when (error) {
                        is StopPlaceNotFoundException ->
                            showSearchInput(canCancel = canCancel, error = _uiState.value.strings.stopNotFound(error.query))
                        else -> showApiFailure(error)
                    }
                },
            )
        }
    }

    fun selectStop(stop: StopPlace) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            currentStop = stop
            runCatching {
                dataStore.edit { prefs ->
                    prefs[SanntidPreferences.STOP_ID] = stop.id
                    prefs[SanntidPreferences.STOP_NAME] = stop.name
                }
            }
            refreshDepartures(stop, showLoadingScreen = true)
        }
    }

    fun searchNewStop() {
        showSearchInputBlocking(canCancel = currentStop != null)
    }

    // ── Stop picker, favourites, nearby ───────────────────────────────────────

    /** "BYTT STED" now opens the chooser rather than jumping straight to the text editor. */
    fun openStopPicker() {
        _uiState.update { it.copy(mode = SanntidScreenMode.StopPicker, errorModal = null) }
    }

    fun closeStopPicker() {
        val stop = currentStop
        if (stop == null) {
            showSearchInputBlocking(canCancel = false)
            return
        }
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshDepartures(stop, showLoadingScreen = true)
        }
    }

    /** Adds or removes the currently displayed stop. Matched on [StopPlace.id] rather than whole
     *  objects, because the same stop arrives with a different label depending on whether it came
     *  from a search, a favourite or a nearby lookup. */
    fun toggleFavourite() {
        val stop = currentStop ?: return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val updated = _uiState.value.favourites.toMutableList()
            val existing = updated.indexOfFirst { it.id == stop.id }
            if (existing >= 0) updated.removeAt(existing) else updated.add(stop)
            runCatching {
                dataStore.edit { prefs ->
                    prefs[SanntidPreferences.FAVOURITES] = encodeFavourites(updated)
                }
            }
            updateState { it.copy(favourites = updated) }
        }
    }

    fun removeFavourite(stop: StopPlace) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val updated = _uiState.value.favourites.filterNot { it.id == stop.id }
            runCatching {
                dataStore.edit { prefs ->
                    prefs[SanntidPreferences.FAVOURITES] = encodeFavourites(updated)
                }
            }
            updateState { it.copy(favourites = updated) }
        }
    }

    /**
     * The screen supplies the two capabilities it alone can provide: [hasPermission] reads the
     * real OS grant, and [coordinates] the sealed context's location reader. Keeping the
     * sequencing here rather than in the composable means the whole flow - no permission, no fix,
     * no stops nearby - has one place to go wrong and one place to report it.
     */
    fun loadNearby(
        hasPermission: suspend () -> Boolean,
        coordinates: suspend () -> LightCoordinates?,
    ) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            updateState { it.copy(mode = SanntidScreenMode.Loading, errorModal = null) }

            if (!hasPermission()) {
                showPickerWithError(_uiState.value.strings.locationPermission)
                return@launch
            }
            val fix = coordinates()
            if (fix == null) {
                showPickerWithError(_uiState.value.strings.locationUnavailable)
                return@launch
            }
            api.nearbyStopPlaces(fix.latitude, fix.longitude).fold(
                onSuccess = { stops ->
                    if (stops.isEmpty()) {
                        showPickerWithError(_uiState.value.strings.noStopsNearby)
                    } else {
                        updateState { it.copy(mode = SanntidScreenMode.NearbyResults(stops)) }
                    }
                },
                onFailure = { showApiFailure(it) },
            )
        }
    }

    private suspend fun showPickerWithError(message: String) {
        updateState { it.copy(mode = SanntidScreenMode.StopPicker, errorModal = message) }
    }

    fun cancelSearch() {
        if (!_uiState.value.canCancelSearch) return
        val stop = currentStop ?: return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshDepartures(stop, showLoadingScreen = true)
        }
    }

    private fun showSearchInputBlocking(canCancel: Boolean) {
        _uiState.update {
            it.copy(
                mode = SanntidScreenMode.SearchInput,
                canCancelSearch = canCancel,
                editorSession = it.editorSession + 1,
                errorModal = null,
            )
        }
    }

    /** Manual re-fetch of the current stop's departures, in place (no loading flash). */
    fun refresh() {
        val stop = currentStop ?: return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshDepartures(stop, showLoadingScreen = false)
        }
    }

    /** Cycles to the next available language and persists it. */
    fun toggleLanguage() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val entries = AppLanguage.entries
            val next = entries[(entries.indexOf(_uiState.value.language) + 1) % entries.size]
            runCatching {
                dataStore.edit { prefs -> prefs[SanntidPreferences.LANGUAGE] = next.code }
            }
            updateState { it.copy(language = next) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorModal = null) }
    }

    override fun onCleared() {
        super.onCleared()
        api.close()
    }
}
