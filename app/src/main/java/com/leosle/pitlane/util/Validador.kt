package com.leosle.pitlane.util

import java.time.LocalDate

/**
 * Reglas de formato de los campos de los formularios.
 * Cada función devuelve el mensaje de error a mostrar debajo del campo, o nulo cuando el valor es válido.
 */
object Validador {

    private val patronCorreo = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val patronPlaca = Regex("^[A-Za-z0-9]{3}-?[A-Za-z0-9]{3}$")

    const val MAXIMO_BYTES_FOTO: Long = 5L * 1024 * 1024

    /** Exige que el texto no esté vacío; [mensaje] es el error a mostrar. */
    fun obligatorio(valor: String, mensaje: String = "Este campo es obligatorio"): String? =
        if (valor.isBlank()) mensaje else null

    /** Exige que el texto tenga al menos [minimo] caracteres sin contar los espacios de los extremos. */
    fun longitudMinima(valor: String, minimo: Int, mensaje: String): String? =
        if (valor.trim().length < minimo) mensaje else null

    /** Valida un nombre o apellido: obligatorio. */
    fun nombre(valor: String, etiqueta: String): String? =
        obligatorio(valor, "Ingresa $etiqueta")

    /** Valida el DNI: exactamente 8 dígitos numéricos. */
    fun dni(valor: String): String? =
        if (valor.length == 8 && valor.all { it.isDigit() }) null else "El DNI debe tener 8 dígitos"

    /** Valida el celular: 9 dígitos y el primero es 9. */
    fun telefono(valor: String): String? =
        if (valor.length == 9 && valor.all { it.isDigit() } && valor.startsWith("9")) null
        else "El celular debe tener 9 dígitos y empezar con 9"

    /** Valida el correo electrónico con un patrón básico. */
    fun correo(valor: String): String? =
        if (patronCorreo.matches(valor.trim())) null else "Ingresa un correo válido"

    /** Valida un correo que puede quedar vacío; si se escribe debe tener formato válido. */
    fun correoOpcional(valor: String): String? =
        if (valor.isBlank()) null else correo(valor)

    /** Valida la contraseña: mínimo 8 caracteres con al menos una letra y un número. */
    fun contrasena(valor: String): String? =
        if (valor.length >= 8 && valor.any { it.isLetter() } && valor.any { it.isDigit() }) null
        else "Mínimo 8 caracteres, con una letra y un número"

    /** Verifica que la confirmación coincida con la contraseña escrita. */
    fun confirmacion(contrasena: String, confirmacion: String): String? =
        if (contrasena == confirmacion) null else "Las contraseñas no coinciden"

    /** Valida la placa: 6 caracteres alfanuméricos con guion opcional (ABC-123). */
    fun placa(valor: String): String? =
        if (patronPlaca.matches(valor.trim())) null else "La placa debe tener 6 caracteres (ej. ABC-123)"

    /** Convierte la placa a mayúsculas y con guion después del tercer carácter (ABC-123). */
    fun normalizarPlaca(valor: String): String {
        val limpia = valor.trim().replace("-", "").uppercase()
        return if (limpia.length == 6) limpia.substring(0, 3) + "-" + limpia.substring(3) else limpia
    }

    /** Valida el año del vehículo: entre 1950 y el año actual más uno. */
    fun anio(valor: String, anioActual: Int = LocalDate.now().year): String? {
        val numero = valor.toIntOrNull()
        return if (numero != null && numero in 1950..(anioActual + 1)) null else "Año no válido"
    }

    /** Valida el kilometraje: número entero mayor o igual a cero. */
    fun kilometraje(valor: String): String? {
        val numero = valor.toIntOrNull()
        return if (numero != null && numero >= 0) null else "Ingresa un kilometraje válido"
    }

    /** Valida que una foto sea JPG o PNG y no supere los 5 MB. */
    /** Deduce el tipo de imagen a partir de la extensión del archivo; nulo si no es JPG ni PNG. */
    fun tipoMimeDeImagen(extension: String): String? = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> null
    }

    fun foto(tipoMime: String?, tamanoBytes: Long): String? = when {
        tipoMime != "image/jpeg" && tipoMime != "image/png" -> "La foto debe ser JPG o PNG"
        tamanoBytes > MAXIMO_BYTES_FOTO -> "La foto no debe superar los 5 MB"
        else -> null
    }
}
