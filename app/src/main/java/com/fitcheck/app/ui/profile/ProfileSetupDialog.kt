package com.fitcheck.app.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitcheck.app.data.UserProfile

@Composable
fun ProfileSetupDialog(onSave: (UserProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var genderExpanded by remember { mutableStateOf(false) }
    var professionExpanded by remember { mutableStateOf(false) }
    val genders = listOf("Female", "Male", "Non-binary", "Prefer not to say")
    val professions = listOf("College student", "School student", "Working professional", "Other")
    val validAge = age.toIntOrNull()?.let { it in 5..120 } == true
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to FitCheck") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tell us a little about you so recommendations fit your lifestyle. This is saved only on this device.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("Age") }, singleLine = true)
            Box {
                OutlinedButton(onClick = { genderExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (gender.isBlank()) "Select gender" else gender) }
                DropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) { genders.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { gender = value; genderExpanded = false }) } }
            }
            Box {
                OutlinedButton(onClick = { professionExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (profession.isBlank()) "Select profession" else profession) }
                DropdownMenu(expanded = professionExpanded, onDismissRequest = { professionExpanded = false }) { professions.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { profession = value; professionExpanded = false }) } }
            }
            if (age.isNotBlank() && !validAge) Text("Enter an age between 5 and 120.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(enabled = name.isNotBlank() && validAge && gender.isNotBlank() && profession.isNotBlank(), onClick = { onSave(UserProfile(name.trim(), age.toInt(), gender, profession)) }) { Text("Continue") } }
    )
}
