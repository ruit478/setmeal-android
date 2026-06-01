package com.weekmenu.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekmenu.data.db.dao.RecipeDao
import com.weekmenu.data.db.entity.IngredientEntity
import com.weekmenu.data.db.entity.RecipeEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class IngredientRow(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val category: String = "mercearia"
)

data class AddMealUiState(
    val name: String = "",
    val category: String = "carne",
    val ingredients: List<IngredientRow> = listOf(IngredientRow()),
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedSuccessfully: Boolean = false
)

@HiltViewModel
class AddMealViewModel @Inject constructor(
    private val recipeDao: RecipeDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMealUiState())
    val uiState: StateFlow<AddMealUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onCategoryChanged(category: String) {
        _uiState.update { it.copy(category = category) }
    }

    fun onIngredientNameChanged(index: Int, name: String) {
        _uiState.update { state ->
            val updated = state.ingredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(name = name)
            }
            state.copy(ingredients = updated, error = null)
        }
    }

    fun onIngredientQuantityChanged(index: Int, quantity: String) {
        _uiState.update { state ->
            val updated = state.ingredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(quantity = quantity)
            }
            state.copy(ingredients = updated)
        }
    }

    fun onIngredientCategoryChanged(index: Int, category: String) {
        _uiState.update { state ->
            val updated = state.ingredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(category = category)
            }
            state.copy(ingredients = updated)
        }
    }

    fun addIngredientRow() {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients + IngredientRow())
        }
    }

    fun removeIngredientRow(index: Int) {
        _uiState.update { state ->
            if (state.ingredients.size <= 1) return@update state
            val updated = state.ingredients.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            state.copy(ingredients = updated)
        }
    }

    fun saveRecipe() {
        val state = _uiState.value

        // Validate
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Recipe name is required") }
            return
        }

        val validIngredients = state.ingredients.filter { it.name.isNotBlank() }
        if (validIngredients.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one ingredient") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val recipeId = UUID.randomUUID().toString()
                val recipe = RecipeEntity(
                    id = recipeId,
                    name = state.name.trim(),
                    category = state.category
                )

                val ingredients = validIngredients.mapIndexed { index, row ->
                    IngredientEntity(
                        id = UUID.randomUUID().toString(),
                        recipeId = recipeId,
                        name = row.name.trim(),
                        quantity = row.quantity.trim().ifBlank { null },
                        category = row.category,
                        sortOrder = index
                    )
                }

                recipeDao.insertRecipeWithIngredients(recipe, ingredients)
                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Save error: ${e.message}")
                }
            }
        }
    }
}
