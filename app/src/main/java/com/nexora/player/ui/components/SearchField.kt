package com.nexora.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SearchField(
    query: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expanded) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                            }
                        }
                        IconButton(onClick = { onExpandedChange(false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar búsqueda")
                        }
                    }
                },
                placeholder = { Text("Buscar música y videos") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )
        } else {
            FilledTonalIconButton(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Abrir búsqueda",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
