package com.leosle.pitlane.arquitectura.datasource.local

import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.RelojSistema
import com.leosle.pitlane.modelo.Vehiculo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Almacén en memoria de todos los datos de la aplicación. Cada colección es un [MutableStateFlow] para que
 * las pantallas se actualicen solas cuando cambia un dato. Los datos viven mientras el proceso esté abierto.
 *
 * @param conSemilla si es verdadero, carga cuentas, vehículos y órdenes de ejemplo.
 * @param reloj proporciona la hora actual; se puede reemplazar en las pruebas.
 */
class BaseDatosLocal(
    conSemilla: Boolean = true,
    private val reloj: Reloj = RelojSistema
) {
    val usuarios = MutableStateFlow<List<Usuario>>(emptyList())
    val vehiculos = MutableStateFlow<List<Vehiculo>>(emptyList())
    val solicitudes = MutableStateFlow<List<Solicitud>>(emptyList())
    val ordenes = MutableStateFlow<List<Orden>>(emptyList())
    val avances = MutableStateFlow<List<Avance>>(emptyList())
    val notificaciones = MutableStateFlow<List<Notificacion>>(emptyList())

    /** Token de dispositivo de cada usuario, indexado por el identificador del usuario. */
    val tokensDispositivo = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Usuario con la sesión abierta. */
    val usuarioActual = MutableStateFlow<Usuario?>(null)

    private var contador = 0L

    init {
        if (conSemilla) cargarSemilla()
    }

    /** Devuelve la hora actual en milisegundos. */
    fun ahora(): Long = reloj.ahora()

    /** Genera un identificador único con el prefijo indicado; nunca coincide con los de los datos de ejemplo. */
    fun nuevoId(prefijo: String): String {
        contador += 1
        return "$prefijo-nuevo-$contador"
    }

    /** Calcula el siguiente número correlativo de orden con el formato ORD-0001. */
    fun siguienteNumeroOrden(): String {
        val mayor = ordenes.value.maxOfOrNull { it.numero.removePrefix("ORD-").toIntOrNull() ?: 0 } ?: 0
        return "ORD-%04d".format(mayor + 1)
    }

    /** Reemplaza el usuario con el mismo identificador y actualiza la sesión si era el usuario activo. */
    fun reemplazarUsuario(actualizado: Usuario) {
        usuarios.update { lista -> lista.map { if (it.id == actualizado.id) actualizado else it } }
        if (usuarioActual.value?.id == actualizado.id) usuarioActual.value = actualizado
    }

    // ---------------------------------------------------------------------------------------------
    // Datos de ejemplo
    // ---------------------------------------------------------------------------------------------

    /** Carga las cuentas, vehículos, solicitudes, órdenes, avances y notificaciones de ejemplo. */
    private fun cargarSemilla() {
        val ahora = ahora()
        val hora = 3_600_000L
        val dia = 24 * hora

        usuarios.value = listOf(
            Usuario("u-jefe", "Freddy", "Quispe Mamani", "40123456", "987654321", "jefe@freddy.pe", "Taller2026", Rol.JEFE),
            Usuario("u-tec-luis", "Luis", "Ramos Huanca", "45671234", "976543210", "luis@freddy.pe", "Tecnico123", Rol.TECNICO),
            Usuario("u-tec-marco", "Marco", "Ccori Torres", "46781235", "965432109", "marco@freddy.pe", "Tecnico123", Rol.TECNICO),
            Usuario("u-tec-pedro", "Pedro", "Salas Rojas", "41890123", "954321098", "pedro@freddy.pe", "Tecnico123", Rol.TECNICO, activo = false),
            Usuario("u-cli-ana", "Ana", "Torres Vega", "71234567", "912345678", "cliente@freddy.pe", "Cliente123", Rol.CLIENTE),
            Usuario("u-cli-carlos", "Carlos", "Mendoza Díaz", "72345678", "923456789", "carlos@freddy.pe", "Cliente123", Rol.CLIENTE),
            Usuario("u-cli-jorge", "Jorge", "Paredes Luna", "43219876", "934567890", "", "", Rol.CLIENTE, tieneCuenta = false),
            Usuario("u-cli-lucia", "Lucía", "Rojas Soto", "74561239", "945678901", "nuevo@freddy.pe", "Cliente123", Rol.CLIENTE)
        )

        vehiculos.value = listOf(
            Vehiculo("v-yaris", "u-cli-ana", "ABC-123", "Toyota", "Yaris", 2018, "Plata"),
            Vehiculo("v-accent", "u-cli-ana", "XYZ-789", "Hyundai", "Accent", 2020, "Blanco"),
            Vehiculo("v-rio", "u-cli-carlos", "BCD-456", "Kia", "Rio", 2019, "Rojo"),
            Vehiculo("v-versa", "u-cli-carlos", "JKL-654", "Nissan", "Versa", 2016, "Gris"),
            Vehiculo("v-sail", "u-cli-jorge", "FGH-321", "Chevrolet", "Sail", 2017, "Azul")
        )

        fun foto(id: String, recurso: String, haceMilis: Long) = Foto(id, "recurso:$recurso", ahora - haceMilis)

        solicitudes.value = listOf(
            Solicitud(
                "s-1", "u-cli-ana", "v-yaris",
                "El auto no arranca por las mañanas y el tablero parpadea al girar la llave.",
                listOf("No arranca", "Tablero con fallas"), ahora - 9 * dia, emptyList(),
                EstadoSolicitud.ATENDIDA, ahora - 10 * dia, "o-2"
            ),
            Solicitud(
                "s-2", "u-cli-ana", "v-accent",
                "Las luces delanteras fallan de noche y vuelven a prender solas.",
                listOf("Luces fallan"), ahora - 30 * dia, emptyList(),
                EstadoSolicitud.CANCELADA, ahora - 31 * dia
            ),
            Solicitud(
                "s-3", "u-cli-carlos", "v-versa",
                "La batería se descarga en dos días aunque el auto no se use. Sospecho de una fuga de corriente.",
                listOf("Batería se descarga"), ahora + 2 * dia,
                listOf(foto("f-s3-1", "foto_demo_bateria", 3 * hora)),
                EstadoSolicitud.PENDIENTE, ahora - 3 * hora
            ),
            Solicitud(
                "s-4", "u-cli-carlos", "v-rio",
                "El motor se apaga en los semáforos y se enciende la luz de batería.",
                listOf("No arranca", "Batería se descarga"), ahora - 2 * dia, emptyList(),
                EstadoSolicitud.ATENDIDA, ahora - 3 * dia, "o-3"
            ),
            Solicitud(
                "s-5", "u-cli-ana", "v-accent",
                "El vidrio eléctrico del copiloto no sube ni baja y el botón no responde.",
                listOf("Otro"), ahora + 1 * dia, emptyList(),
                EstadoSolicitud.PENDIENTE, ahora - 5 * hora
            )
        )

        ordenes.value = listOf(
            Orden(
                "o-1", "ORD-0001", "u-cli-jorge", "v-sail", null,
                "Revisión del sistema de carga", 84500, NivelCombustible.MEDIO,
                "Rayón leve en la puerta trasera derecha.",
                listOf(foto("f-o1-1", "foto_demo_frente", 20 * dia), foto("f-o1-2", "foto_demo_lateral", 20 * dia)),
                "u-tec-marco", EstadoOrden.ANULADA,
                listOf(
                    CambioEstado(EstadoOrden.INGRESADO, ahora - 20 * dia, "u-jefe"),
                    CambioEstado(EstadoOrden.ANULADA, ahora - 20 * dia + 10 * 60_000L, "u-jefe", "Registrada por error: el vehículo no ingresó al taller")
                ),
                ahora - 20 * dia, "Registrada por error: el vehículo no ingresó al taller"
            ),
            Orden(
                "o-2", "ORD-0002", "u-cli-ana", "v-yaris", "s-1",
                "El auto no arranca por las mañanas y el tablero parpadea al girar la llave.", 61200, NivelCombustible.TRES_CUARTOS,
                "Sin golpes visibles. Espejo izquierdo con rajadura.",
                listOf(
                    foto("f-o2-1", "foto_demo_frente", 9 * dia),
                    foto("f-o2-2", "foto_demo_lateral", 9 * dia),
                    foto("f-o2-3", "foto_demo_tablero", 9 * dia)
                ),
                "u-tec-luis", EstadoOrden.LISTO,
                listOf(
                    CambioEstado(EstadoOrden.INGRESADO, ahora - 9 * dia, "u-jefe"),
                    CambioEstado(EstadoOrden.EN_REPARACION, ahora - 8 * dia, "u-tec-luis"),
                    CambioEstado(EstadoOrden.LISTO, ahora - 6 * hora, "u-tec-luis")
                ),
                ahora - 9 * dia, null, 1, ahora - 6 * hora
            ),
            Orden(
                "o-3", "ORD-0003", "u-cli-carlos", "v-rio", "s-4",
                "El motor se apaga en los semáforos y se enciende la luz de batería.", 47800, NivelCombustible.MEDIO,
                "Paragolpes delantero con raspones antiguos.",
                listOf(foto("f-o3-1", "foto_demo_frente", 2 * dia), foto("f-o3-2", "foto_demo_motor", 2 * dia)),
                "u-tec-luis", EstadoOrden.EN_REPARACION,
                listOf(
                    CambioEstado(EstadoOrden.INGRESADO, ahora - 2 * dia, "u-jefe"),
                    CambioEstado(EstadoOrden.EN_REPARACION, ahora - 1 * dia, "u-tec-luis")
                ),
                ahora - 2 * dia
            ),
            Orden(
                "o-4", "ORD-0004", "u-cli-jorge", "v-sail", null,
                "Luces de freno intermitentes y radio que se apaga solo.", 90350, NivelCombustible.UN_CUARTO,
                "Tapa del tablero floja.",
                listOf(foto("f-o4-1", "foto_demo_lateral", 3 * hora)),
                "u-tec-marco", EstadoOrden.INGRESADO,
                listOf(CambioEstado(EstadoOrden.INGRESADO, ahora - 3 * hora, "u-jefe")),
                ahora - 3 * hora
            )
        )

        avances.value = listOf(
            Avance(
                "a-1", "o-2", "u-tec-luis", TipoAvance.HALLAZGO,
                "Batería con 9.8 V en reposo y alternador sin carga. Terminales sulfatados.",
                listOf(foto("f-a1-1", "foto_demo_bateria", 7 * dia)), ahora - 7 * dia
            ),
            Avance(
                "a-2", "o-2", "u-tec-luis", TipoAvance.TRABAJO_REALIZADO,
                "Cambio de alternador y limpieza de terminales. Carga estable en 14.2 V.",
                listOf(foto("f-a2-1", "foto_demo_motor", 1 * dia), foto("f-a2-2", "foto_demo_bateria", 1 * dia)), ahora - 1 * dia
            ),
            Avance(
                "a-3", "o-3", "u-tec-luis", TipoAvance.HALLAZGO,
                "Fuga de corriente de 0.9 A en el circuito del radio. Cableado pelado bajo el tablero.",
                listOf(foto("f-a3-1", "foto_demo_tablero", 20 * hora)), ahora - 20 * hora
            )
        )

        notificaciones.value = listOf(
            Notificacion("n-1", "u-cli-ana", "Vehículo ingresado", "Tu vehículo ABC-123 fue ingresado. Orden ORD-0002", ahora - 9 * dia, true, "o-2", true),
            Notificacion("n-2", "u-cli-ana", "Vehículo en reparación", "Tu vehículo ABC-123 está en reparación", ahora - 8 * dia, true, "o-2"),
            Notificacion("n-3", "u-cli-ana", "Vehículo listo", "Tu vehículo ABC-123 está listo para recoger", ahora - 6 * hora, false, "o-2"),
            Notificacion("n-4", "u-cli-carlos", "Vehículo ingresado", "Tu vehículo BCD-456 fue ingresado. Orden ORD-0003", ahora - 2 * dia, true, "o-3", true),
            Notificacion("n-5", "u-cli-carlos", "Vehículo en reparación", "Tu vehículo BCD-456 está en reparación", ahora - 1 * dia, false, "o-3")
        )
    }
}
