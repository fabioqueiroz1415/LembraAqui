package com.lembraaqui.app.domain

data class ParsedCoordinates(val latitude: Double, val longitude: Double)

sealed interface CoordinateParseResult {
    data class Success(val coordinates: ParsedCoordinates) : CoordinateParseResult
    data class Error(val message: String) : CoordinateParseResult
}

object CoordinateParser {
    private val numberRegex = Regex("[-+]?\\d{1,3}(?:[.,]\\d+)?")

    fun parse(raw: String): CoordinateParseResult {
        if (raw.isBlank()) return CoordinateParseResult.Error("Cole duas coordenadas.")
        val matches = numberRegex.findAll(raw).map { it.value }.toList()
        if (matches.size != 2) {
            return CoordinateParseResult.Error("Use latitude e longitude, por exemplo: -12,1231234, -47,9238498234")
        }
        val latitude = matches[0].replace(',', '.').toDoubleOrNull()
            ?: return CoordinateParseResult.Error("Latitude inválida.")
        val longitude = matches[1].replace(',', '.').toDoubleOrNull()
            ?: return CoordinateParseResult.Error("Longitude inválida.")
        if (latitude !in -90.0..90.0) return CoordinateParseResult.Error("A latitude deve estar entre -90 e 90.")
        if (longitude !in -180.0..180.0) return CoordinateParseResult.Error("A longitude deve estar entre -180 e 180.")
        return CoordinateParseResult.Success(ParsedCoordinates(latitude, longitude))
    }
}
