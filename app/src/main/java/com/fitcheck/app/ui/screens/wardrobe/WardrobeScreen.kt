package com.fitcheck.app.ui.screens.wardrobe

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitcheck.app.data.DataGraph
import com.fitcheck.app.data.local.entity.Category
import com.fitcheck.app.data.local.entity.WardrobeItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WardrobeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DataGraph.get(app).wardrobeRepository
    val items = repo.observeAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun add(name: String, category: Category) = viewModelScope.launch {
        repo.insertItem(WardrobeItemEntity(name = name, category = category))
    }
    fun delete(item: WardrobeItemEntity) = viewModelScope.launch { repo.deleteItem(item) }
}

@Composable
fun WardrobeScreen(vm: WardrobeViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Outlined.Add, "Add item") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("WARDROBE", style = androidx.compose.material3.MaterialTheme.typography.displaySmall) }
            item { Text("${items.size} items · local only") }
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(item.name); Text(item.category.name, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                    IconButton(onClick = { vm.delete(item) }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            }
        }
    }
    if (showAdd) AddItemDialog({ showAdd = false }) { name, category -> vm.add(name, category); showAdd = false }
}

@Composable
private fun AddItemDialog(onDismiss: () -> Unit, onAdd: (String, Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.TOP) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add wardrobe item") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            Text("Category: ${category.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.values().forEach { Button(onClick = { category = it }) { Text(it.name.take(3)) } }
            }
        }
    }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), category) }) { Text("Add") } }, dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } })
}
