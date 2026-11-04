package app.openpulse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openpulse.core.model.Measurement
import app.openpulse.core.units.ValueFormatter
import app.openpulse.ui.format.Formats
import app.openpulse.ui.theme.NumericStyle
import java.time.ZoneId

@Composable
fun EntryRow(m: Measurement, formatter: ValueFormatter, zone: ZoneId, onClick: () -> Unit, showType: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeBadge(m.type, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showType) Text(m.type.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatter.measurement(m), style = MaterialTheme.typography.bodyLarge.merge(NumericStyle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.note.isNotBlank()) {
                Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TagRow(m.tags, Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Formats.time(m.timestamp, zone), style = MaterialTheme.typography.labelLarge.merge(NumericStyle))
            if (m.isDemo) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                    Text("DEMO", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}
