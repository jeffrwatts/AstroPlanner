package com.islandskiesastro.astroplanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun PlansScreen(
    location: LocationData?,
    locationName: String,
    equipmentRepository: EquipmentRepository,
    repository: CelestialObjectRepository,
    planRepository: PlanRepository,
    timeZone: TimeZone,
    onBackActionChanged: ((() -> Unit)?) -> Unit
) {
    var plans by remember { mutableStateOf(planRepository.getAll()) }
    var selectedPlan by remember { mutableStateOf<ObservingPlan?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    fun reload() { plans = planRepository.getAll() }

    LaunchedEffect(showCreate, selectedPlan) {
        when {
            showCreate           -> onBackActionChanged { showCreate = false }
            selectedPlan != null -> onBackActionChanged { selectedPlan = null }
            else                 -> onBackActionChanged(null)
        }
    }

    when {
        showCreate -> PlanCreationScreen(
            preFill             = null,
            location            = location,
            locationName        = locationName,
            equipmentRepository = equipmentRepository,
            repository          = repository,
            planRepository      = planRepository,
            timeZone            = timeZone,
            onBack              = { showCreate = false },
            onPlanSaved         = { reload(); showCreate = false }
        )
        selectedPlan != null -> PlanDetailScreen(
            plan      = selectedPlan!!,
            onDeleted = { planRepository.delete(selectedPlan!!.id); reload(); selectedPlan = null },
            onBack    = { selectedPlan = null }
        )
        else -> PlansListView(
            plans      = plans,
            onSelect   = { selectedPlan = it },
            onAddClick = { showCreate = true }
        )
    }
}

@Composable
private fun PlansListView(
    plans: List<ObservingPlan>,
    onSelect: (ObservingPlan) -> Unit,
    onAddClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (plans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No plans yet — tap + to generate one",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(plans) { plan ->
                    PlanListItem(plan = plan, onClick = { onSelect(plan) })
                    HorizontalDivider()
                }
            }
        }
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New plan")
        }
    }
}

@Composable
private fun PlanListItem(plan: ObservingPlan, onClick: () -> Unit) {
    val dateStr = remember(plan.createdAtMs) {
        val local = Instant.fromEpochMilliseconds(plan.createdAtMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.date}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(plan.targetDisplayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            plan.equipmentSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${plan.locationName}  •  Bortle ${plan.bortleScale}  •  $dateStr",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        val preview = plan.planMarkdown.take(80).let {
            if (plan.planMarkdown.length > 80) "$it…" else it
        }
        Text(
            preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}
