package com.setmeal.ui.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.setmeal.data.db.entity.IngredientEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    navController: NavController,
    recipeId: String,
    viewModel: RecipeDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(recipeId) {
        viewModel.loadRecipe(recipeId)
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) "Edit Recipe"
                        else uiState.recipe?.name ?: "Recipe"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isEditing) viewModel.cancelEditing()
                        else navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.recipe != null && !uiState.isLoading) {
                        if (uiState.isEditing) {
                            TextButton(onClick = { viewModel.saveEdits() }) {
                                Text("Save")
                            }
                        } else {
                            IconButton(onClick = { viewModel.startEditing() }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.showDeleteDialog() }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.recipe == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Recipe not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                if (uiState.isEditing) {
                    EditMode(
                        uiState = uiState,
                        onNameChanged = { viewModel.onEditNameChanged(it) },
                        onCategoryChanged = { viewModel.onEditCategoryChanged(it) },
                        onIngredientNameChanged = { i, n -> viewModel.onEditIngredientNameChanged(i, n) },
                        onIngredientQuantityChanged = { i, q -> viewModel.onEditIngredientQuantityChanged(i, q) },
                        onIngredientCategoryChanged = { i, c -> viewModel.onEditIngredientCategoryChanged(i, c) },
                        onAddIngredient = { viewModel.addIngredientRow() },
                        onRemoveIngredient = { viewModel.removeIngredientRow(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    ViewMode(
                        recipe = uiState.recipe!!,
                        ingredients = uiState.ingredients,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Recipe") },
            text = {
                Text("Are you sure you want to delete \"${uiState.recipe?.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error snackbar
    if (uiState.error != null && !uiState.isEditing) {
        Box(modifier = Modifier.fillMaxSize()) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(uiState.error!!)
            }
        }
    }
}

@Composable
private fun ViewMode(
    recipe: com.setmeal.data.db.entity.RecipeEntity,
    ingredients: List<IngredientEntity>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Recipe name
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Category chip
        item {
            SuggestionChip(
                onClick = {},
                label = { Text(recipe.category.replaceFirstChar { it.uppercase() }) }
            )
        }

        // Stats
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Cooked ${recipe.cookCount}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recipe.lastUsedWeek != null) {
                    Text(
                        text = "Last used: week ${recipe.lastUsedWeek}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Ingredients header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Ingredients",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Ingredient list
        itemsIndexed(ingredients) { index, ingredient ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!ingredient.quantity.isNullOrBlank()) {
                        Text(
                            text = "${ingredient.quantity} · ${ingredient.category.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = ingredient.category.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (index < ingredients.lastIndex) {
                HorizontalDivider()
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditMode(
    uiState: RecipeDetailUiState,
    onNameChanged: (String) -> Unit,
    onCategoryChanged: (String) -> Unit,
    onIngredientNameChanged: (Int, String) -> Unit,
    onIngredientQuantityChanged: (Int, String) -> Unit,
    onIngredientCategoryChanged: (Int, String) -> Unit,
    onAddIngredient: () -> Unit,
    onRemoveIngredient: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Name
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.editName,
                onValueChange = onNameChanged,
                label = { Text("Dish name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Category
        item {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.editCategory.replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    recipeCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onCategoryChanged(cat)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Ingredients header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ingredients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAddIngredient) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        // Ingredient rows
        itemsIndexed(uiState.editIngredients) { index, row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ingredient ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.editIngredients.size > 1) {
                            IconButton(
                                onClick = { onRemoveIngredient(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = row.name,
                            onValueChange = { onIngredientNameChanged(index, it) },
                            label = { Text("Name *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = row.quantity,
                            onValueChange = { onIngredientQuantityChanged(index, it) },
                            label = { Text("Quantity") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = row.category.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            ingredientCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        onIngredientCategoryChanged(index, cat)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error
        if (uiState.error != null) {
            item {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Saving indicator
        if (uiState.isSaving) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
