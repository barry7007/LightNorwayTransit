package com.thelightphone.sanntid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.sanntid.lp3.BuildConfig
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class SanntidHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SanntidViewModel>(sealedActivity) {

    override val viewModelClass: Class<SanntidViewModel>
        get() = SanntidViewModel::class.java

    override fun createViewModel(): SanntidViewModel = SanntidViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val keyboardOptionsFlow = rememberKeyboardOptions()
        val session = state.editorSession
        val strings = state.strings
        val nearMe = rememberNearMeLocation(lightContext)

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is SanntidScreenMode.Loading -> LoadingContent(strings)

                    is SanntidScreenMode.SearchInput -> {
                        val textState = remember(session) { TextFieldState("") }
                        LightTextInputEditor(
                            title = strings.searchStopTitle,
                            editorKey = session,
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            state = textState,
                            onSubmit = viewModel::submitSearch,
                            onBack = viewModel::cancelSearch,
                            submitIcon = LightIcons.SEARCH,
                            leftBottomBarItem = rememberKeyboardToggleItem(),
                            showBackButton = state.canCancelSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is SanntidScreenMode.SearchResults -> {
                        SearchResultsContent(
                            query = mode.query,
                            results = mode.results,
                            onSelect = viewModel::selectStop,
                            onBack = viewModel::searchNewStop,
                            strings = strings,
                        )
                    }

                    is SanntidScreenMode.Departures -> {
                        DeparturesContent(
                            stop = mode.stop,
                            groups = mode.groups,
                            isFavourite = state.favourites.any { it.id == mode.stop.id },
                            onOpenPicker = viewModel::openStopPicker,
                            onToggleFavourite = viewModel::toggleFavourite,
                            onRefresh = viewModel::refresh,
                            strings = strings,
                        )
                    }

                    is SanntidScreenMode.StopPicker -> {
                        StopPickerContent(
                            favourites = state.favourites,
                            canGoBack = state.canCancelSearch,
                            onSelect = viewModel::selectStop,
                            onRemoveFavourite = viewModel::removeFavourite,
                            onSearch = viewModel::searchNewStop,
                            onBack = viewModel::closeStopPicker,
                            onNearMe = {
                                viewModel.loadNearby(
                                    hasPermission = {
                                        nearMe.hasPermission().also { if (!it) nearMe.requestPermission() }
                                    },
                                    coordinates = nearMe::coordinates,
                                )
                            },
                            language = state.language,
                            onToggleLanguage = viewModel::toggleLanguage,
                            strings = strings,
                        )
                    }

                    is SanntidScreenMode.NearbyResults -> {
                        NearbyResultsContent(
                            stops = mode.stops,
                            onSelect = viewModel::selectStop,
                            onBack = viewModel::openStopPicker,
                            strings = strings,
                        )
                    }
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(strings: SanntidStrings) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(strings.appTitle),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        LightText(
            text = strings.loading,
            variant = LightTextVariant.Copy,
            modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    results: List<StopPlace>,
    onSelect: (StopPlace) -> Unit,
    onBack: () -> Unit,
    strings: SanntidStrings,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = strings.backToSearch,
            ),
            center = LightTopBarCenter.Text("\"$query\""),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            results.forEach { stop ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onSelect(stop) })
                        .padding(vertical = 0.75f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = stop.label,
                        variant = LightTextVariant.Heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopPickerContent(
    favourites: List<StopPlace>,
    canGoBack: Boolean,
    onSelect: (StopPlace) -> Unit,
    onRemoveFavourite: (StopPlace) -> Unit,
    onSearch: () -> Unit,
    onNearMe: () -> Unit,
    onBack: () -> Unit,
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    strings: SanntidStrings,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = if (canGoBack) {
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                    contentDescription = strings.backToDepartures,
                )
            } else {
                null
            },
            center = LightTopBarCenter.Text(strings.changePlace),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            if (favourites.isNotEmpty()) {
                LightText(
                    text = strings.favouritesHeading,
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                favourites.forEach { stop ->
                    FavouriteRow(
                        stop = stop,
                        onSelect = { onSelect(stop) },
                        onRemove = { onRemoveFavourite(stop) },
                        strings = strings,
                    )
                }
            }

            LightText(
                text = strings.findStopHeading,
                variant = LightTextVariant.Fine,
                lighten = true,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
            )
            PickerAction(icon = LightIcons.CROSSHAIR, text = strings.nearMe, onClick = onNearMe)
            PickerAction(icon = LightIcons.SEARCH, text = strings.searchStopTitle, onClick = onSearch)

            LightText(
                text = strings.settingsHeading,
                variant = LightTextVariant.Fine,
                lighten = true,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
            )
            // Tapping cycles languages. With only two there is nothing to choose between a
            // toggle and a picker, and a toggle costs no extra screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onToggleLanguage)
                    .padding(vertical = 0.6f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(
                    text = strings.language,
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.weight(1f),
                )
                LightText(
                    text = language.ownLabel,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 0.75f.gridUnitsAsDp()),
                )
            }
            // Not interactive — it exists so a bug report can say which build it came from.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.6f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(
                    text = strings.version,
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.weight(1f),
                )
                LightText(
                    text = BuildConfig.VERSION_NAME,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 0.75f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
private fun FavouriteRow(
    stop: StopPlace,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    strings: SanntidStrings,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = stop.name,
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .lightClickable(onClick = onSelect)
                .padding(vertical = 0.6f.gridUnitsAsDp()),
        )
        LightIcon(
            icon = LightIcons.STAR,
            contentDescription = strings.removeFavourite(stop.name),
            modifier = Modifier
                .lightClickable(onClick = onRemove)
                .padding(horizontal = 0.75f.gridUnitsAsDp(), vertical = 0.6f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun PickerAction(
    icon: LightIconConfiguration,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.6f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = 0.75f.gridUnitsAsDp()),
        )
        LightText(text = text, variant = LightTextVariant.Heading)
    }
}

@Composable
private fun NearbyResultsContent(
    stops: List<NearbyStop>,
    onSelect: (StopPlace) -> Unit,
    onBack: () -> Unit,
    strings: SanntidStrings,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = strings.back,
            ),
            center = LightTopBarCenter.Text(strings.nearMe),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            stops.forEach { nearby ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onSelect(nearby.stop) })
                        .padding(vertical = 0.6f.gridUnitsAsDp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText(
                        text = nearby.stop.name,
                        variant = LightTextVariant.Heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    LightText(
                        text = nearby.distanceLabel(strings),
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 0.75f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeparturesContent(
    stop: StopPlace,
    groups: List<ModeGroup>,
    isFavourite: Boolean,
    onOpenPicker: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRefresh: () -> Unit,
    strings: SanntidStrings,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(stop.name),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (groups.isEmpty()) {
            LightText(
                text = strings.noDepartures,
                variant = LightTextVariant.Copy,
                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                groups.forEach { group ->
                    ModeSection(group, strings)
                }
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = if (isFavourite) LightIcons.STAR else LightIcons.STAR_OUTLINE,
                    onClick = onToggleFavourite,
                    contentDescription = if (isFavourite) {
                        strings.removeFavourite(stop.name)
                    } else {
                        strings.addFavourite(stop.name)
                    },
                ),
                LightBarButton.Text(text = strings.changePlaceBar, onClick = onOpenPicker),
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onRefresh,
                    contentDescription = strings.refresh,
                ),
            ),
        )
    }
}

@Composable
private fun ModeSection(group: ModeGroup, strings: SanntidStrings) {
    Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
        LightText(
            text = strings.modeLabel(group.mode),
            variant = LightTextVariant.Heading,
        )
        group.departures.forEach { departure ->
            DepartureRow(departure, strings)
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure, strings: SanntidStrings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.25f.gridUnitsAsDp()),
    ) {
        LightText(
            text = formatLineDestination(departure),
            variant = LightTextVariant.Copy,
        )
        LightText(
            text = formatDepartureDetail(departure, strings),
            variant = LightTextVariant.Detail,
            lighten = true,
        )
    }
}
