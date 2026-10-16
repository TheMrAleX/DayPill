package app.pastillero.ui.nav

object Routes {
    const val TODAY = "today"
    const val MEDICATIONS = "medications"
    const val HISTORY = "history"
    const val NEW_MEDICATION = "medication/new"
    const val EDIT_MEDICATION = "medication/edit/{medicationId}"
    const val SETTINGS = "settings"

    fun editMedication(medicationId: Long): String {
        require(medicationId > 0)
        return "medication/edit/$medicationId"
    }
}
