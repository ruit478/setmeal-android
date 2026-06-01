package com.setmeal.ui.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.GroceryDao
import com.setmeal.data.db.entity.GroceryItemEntity
import com.setmeal.data.db.entity.ManualGroceryItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val groceryDao: GroceryDao
) : ViewModel() {

    val manualItems: StateFlow<List<ManualGroceryItemEntity>> = groceryDao.getAllManualItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // For Phase 1, only manual items are shown.
    // Phase 4 will merge with plan-based auto items.
    private val _autoItems = MutableStateFlow<List<GroceryItemEntity>>(emptyList())
    val autoItems: StateFlow<List<GroceryItemEntity>> = _autoItems.asStateFlow()

    fun addManualItem(name: String, quantity: String?, category: String) {
        viewModelScope.launch {
            groceryDao.insertManualItem(
                ManualGroceryItemEntity(
                    name = name,
                    quantity = quantity,
                    category = category
                )
            )
        }
    }

    fun toggleManualItem(item: ManualGroceryItemEntity) {
        viewModelScope.launch {
            groceryDao.updateManualItem(item.copy(checked = !item.checked))
        }
    }

    fun deleteManualItem(item: ManualGroceryItemEntity) {
        viewModelScope.launch {
            groceryDao.deleteManualItem(item)
        }
    }

    fun clearChecked() {
        viewModelScope.launch {
            groceryDao.clearCheckedManualItems()
        }
    }
}
