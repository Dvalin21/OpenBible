package com.openbible.ui.readingplan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openbible.data.db.entity.ReadingPlanEntity

/**
 * Reading plans screen.
 *
 * Shows available plans and tracks daily reading progress.
 * The "Bible in a Year" plan is auto-seeded on first launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingPlanScreen(
    defaultTranslation: String = "kjv",
    onOpenChapter: (translationId: String, bookId: Int, chapter: Int) -> Unit,
    viewModel: ReadingPlanViewModel = viewModel()
) {
    val plans by viewModel.plans.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val activePlanId by viewModel.activePlanId.collectAsState()
    val currentDay by viewModel.currentDay.collectAsState()
    val currentPlanDay by viewModel.currentPlanDay.collectAsState()
    val currentDayProgress by viewModel.currentDayProgress.collectAsState()
    val planProgress by viewModel.planProgress.collectAsState()

    // Seed on first composition
    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading Plans") }
            )
        }
    ) { padding ->
        if (activePlanId == null || plans.isEmpty()) {
            // ── No active plan: show plan list ─────────────────
            PlanList(
                plans = plans,
                onStartPlan = { planId -> viewModel.selectPlan(planId) },
                modifier = Modifier.padding(padding)
            )
        } else if (currentPlanDay == null) {
            // Loading or no data
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // ── Active plan: show today's reading ──────────────
            val isCompleted = currentDayProgress?.completed == true
            val readings = viewModel.parseReadings(currentPlanDay?.readings ?: "[]")

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Plan header
                Text(
                    text = activePlan?.name ?: "Reading Plan",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { planProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Day $currentDay of ${activePlan?.durationDays ?: 365}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Day title
                Text(
                    text = currentPlanDay?.title ?: "Day $currentDay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Readings list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(readings) { reading ->
                        ReadingCard(
                            reading = reading,
                            onClick = {
                                onOpenChapter(defaultTranslation, reading.bookId, reading.chapter)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Day navigation + mark complete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.goToDay(currentDay - 1) },
                        enabled = currentDay > 1
                    ) {
                        Text("← Previous")
                    }

                    Button(
                        onClick = { viewModel.markDayComplete() },
                        enabled = !isCompleted
                    ) {
                        Text(if (isCompleted) "✓ Completed" else "Mark Day Complete")
                    }

                    TextButton(
                        onClick = { viewModel.goToDay(currentDay + 1) },
                        enabled = currentDay < (activePlan?.durationDays ?: 365)
                    ) {
                        Text("Next →")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PlanList(
    plans: List<ReadingPlanEntity>,
    onStartPlan: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (plans.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(plans) { plan ->
                PlanCard(
                    plan = plan,
                    onStart = { onStartPlan(plan.id) }
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: ReadingPlanEntity,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onStart
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            plan.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${plan.durationDays} days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ReadingCard(
    reading: ReadingItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayText = if (reading.bookName.startsWith("Book "))
                "${reading.bookName}, Chapter ${reading.chapter}"
            else
                "${reading.bookName} ${reading.chapter}"
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
