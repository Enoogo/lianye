package org.lianye.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.lianye.domain.model.CaptureMode
import org.lianye.ui.common.uiText

/** Visible beside the capture action; changing a mode never begins a capture. */
@Composable
fun CaptureModeSelector(mode: CaptureMode, enabled: Boolean, onSelect: (CaptureMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (choice in CaptureMode.entries) {
            val isSelected = choice == mode
            TextButton(
                onClick = { onSelect(choice) }, enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    selected = isSelected
                    role = Role.RadioButton
                },
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (choice == CaptureMode.AUTO) uiText("自动滚动", "Auto") else uiText("自己滑动", "Manual"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(24.dp).height(2.dp).background(
                        if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}
