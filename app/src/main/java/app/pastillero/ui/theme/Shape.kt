package app.pastillero.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PastilleroShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp), // Text fields.
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp), // Cards.
    large = RoundedCornerShape(16.dp), // Floating action buttons.
    extraLarge = RoundedCornerShape(28.dp), // Dialogs.
)
