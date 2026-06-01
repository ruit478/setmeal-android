package com.setmeal.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.RecipeDao
import com.setmeal.data.db.entity.IngredientEntity
import com.setmeal.data.db.entity.RecipeEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IngredientEdit(
    val id: String = "",
    val name: String = "",
    val quantity: String = "",
    val category: String = "mercearia"
)

data class RecipeDetailUiState(
    val recipe: RecipeEntity? = null,
    val ingredients: List<IngredientEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val editName: String = "",
    val editCategory: String = "",
    val editIngredients: List<IngredientEdit> = emptyList(),
    val isSaving: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeDao: RecipeDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    private var recipeId: String = ""

    fun loadRecipe(id: String) {
        recipeId = id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val recipe = recipeDao.getRecipeById(id)
            val ingredients = recipeDao.getIngredientsForRecipe(id)

            if (recipe != null) {
                _uiState.update {
                    it.copy(
                        recipe = recipe,
                        ingredients = ingredients,
                        isLoading = false,
                        editName = recipe.name,
                        editCategory = recipe.category,
                        editIngredients = ingredients.map { ing ->
                            IngredientEdit(
                                id = ing.id,
                                name = ing.name,
                                quantity = ing.quantity ?: "",
                                category = ing.category
                            )
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Recipe not found") }
            }
        }
    }

    fun startEditing() {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                isEditing = true,
                editName = state.recipe?.name ?: "",
                editCategory = state.recipe?.category ?: "",
                editIngredients = state.ingredients.map { ing ->
                    IngredientEdit(
                        id = ing.id,
                        name = ing.name,
                        quantity = ing.quantity ?: "",
                        category = ing.category
                    )
                }
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun onEditNameChanged(name: String) {
        _uiState.update { it.copy(editName = name) }
    }

    fun onEditCategoryChanged(category: String) {
        _uiState.update { it.copy(editCategory = category) }
    }

    fun onEditIngredientNameChanged(index: Int, name: String) {
        _uiState.update { state ->
            val updated = state.editIngredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(name = name)
            }
            state.copy(editIngredients = updated)
        }
    }

    fun onEditIngredientQuantityChanged(index: Int, quantity: String) {
        _uiState.update { state ->
            val updated = state.editIngredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(quantity = quantity)
            }
            state.copy(editIngredients = updated)
        }
    }

    fun onEditIngredientCategoryChanged(index: Int, category: String) {
        _uiState.update { state ->
            val updated = state.editIngredients.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(category = category)
            }
            state.copy(editIngredients = updated)
        }
    }

    fun addIngredientRow() {
        _uiState.update { state ->
            state.copy(editIngredients = state.editIngredients + IngredientEdit())
        }
    }

    fun removeIngredientRow(index: Int) {
        _uiState.update { state ->
            if (state.editIngredients.size <= 1) return@update state
            val updated = state.editIngredients.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
            }
            state.copy(editIngredients = updated)
        }
    }

    fun saveEdits() {
        val state = _uiState.value
        if (state.editName.isBlank()) {
            _uiState.update { it.copy(error = "Name is required") }
            return
        }

        val validIngredients = state.editIngredients.filter { it.name.isNotBlank() }
        if (validIngredients.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one ingredient") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val updatedRecipe = state.recipe!!.copy(
                    name = state.editName.trim(),
                    category = state.editCategory
                )

                val updatedIngredients = validIngredients.mapIndexed { index, edit ->
                    IngredientEntity(
                        id = edit.id.ifBlank { java.util.UUID.randomUUID().toString() },
                        recipeId = recipeId,
                        name = edit.name.trim(),
                        quantity = edit.quantity.trim().ifBlank { null },
                        category = edit.category,
                        sortOrder = index
                    )
                }

                recipeDao.updateRecipeWithIngredients(updatedRecipe, updatedIngredients)

                // Reload
                val freshIngredients = recipeDao.getIngredientsForRecipe(recipeId)
                _uiState.update {
                    it.copy(
                        recipe = updatedRecipe,
                        ingredients = freshIngredients,
                        isEditing = false,
                        isSaving = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Save error: ${e.message}")
                }
            }
        }
    }

    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            try {
                recipeDao.deleteRecipe(_uiState.value.recipe!!)
                _uiState.update { it.copy(deleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Delete error: ${e.message}") }
            }
        }
    }
}
