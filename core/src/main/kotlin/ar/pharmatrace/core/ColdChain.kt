package ar.pharmatrace.core

/** How the app presents the chaincode verdict of a cargo's cold chain. */
enum class Verdict(val label: String) {
    Monitoring("En monitoreo"),
    Compliant("En rango"),
    Compromised("Comprometido"),
    Pending("Sin veredicto"),
    ;

    companion object {
        fun from(text: String?): Verdict = when (text?.uppercase()) {
            "MONITORING" -> Monitoring
            "COMPLIANT" -> Compliant
            "COMPROMISED" -> Compromised
            else -> Pending
        }
    }
}

/** Cargo states of the chaincode (contract/model.go), in Spanish for the UI. */
object CargoStates {
    fun label(state: String?): String = when (state) {
        "OPEN" -> "Armando"
        "IN_TRANSIT" -> "En tránsito"
        "DELIVERED" -> "Entregado"
        "DISSOLVED" -> "Disuelto"
        null -> "—"
        else -> state
    }
}

/** Seconds as "1 h 05 min" / "3 min 20 s" / "45 s". */
fun formatDuration(seconds: Long): String {
    if (seconds < 0) return "—"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "%d h %02d min".format(h, m)
        m > 0 -> "%d min %02d s".format(m, s)
        else -> "$s s"
    }
}
