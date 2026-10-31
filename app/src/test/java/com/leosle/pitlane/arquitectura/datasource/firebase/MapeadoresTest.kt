package com.leosle.pitlane.arquitectura.datasource.firebase

import com.leosle.pitlane.modelo.Avance
import com.leosle.pitlane.modelo.CambioEstado
import com.leosle.pitlane.modelo.Conformidad
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.ObservacionCliente
import com.leosle.pitlane.modelo.Orden
import com.leosle.pitlane.modelo.ResumenCliente
import com.leosle.pitlane.modelo.ResumenTecnico
import com.leosle.pitlane.modelo.ResumenVehiculo
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.modelo.Solicitud
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.modelo.Usuario
import com.leosle.pitlane.modelo.Vehiculo
import com.leosle.pitlane.util.Busqueda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas de la conversión entre el modelo y los documentos de Firestore. */
class MapeadoresTest {

    private val foto = Foto(id = "f1", ruta = "recurso:foto_1", fechaHora = 1_800_000_000_000L)

    @Test
    fun usuarioNoEscribeLaContrasenaYSeLeeSinElla() {
        val usuario = Usuario("u1", "Ana", "Ruiz", "12345678", "999111222", "ana@correo.com", "secreta", Rol.JEFE)

        val documento = usuario.aDocumento()

        assertFalse(documento.containsKey("contrasena"))
        assertFalse(documento.containsKey("id"))
        assertEquals(usuario.copy(contrasena = ""), documento.aUsuario("u1"))
    }

    @Test
    fun usuarioSinCuentaConserva_tieneCuentaYActivo() {
        val usuario = Usuario("u2", "Luis", "Paz", "87654321", "", "", "", Rol.CLIENTE, activo = false, tieneCuenta = false)

        assertEquals(usuario, usuario.aDocumento().aUsuario("u2"))
    }

    @Test
    fun soloElClienteLlevaTerminosDeBusqueda() {
        val cliente = Usuario("u4", "María José", "Pérez", "72345678", "", "", "", Rol.CLIENTE)
        val tecnico = cliente.copy(id = "u5", rol = Rol.TECNICO)
        val jefe = cliente.copy(id = "u6", rol = Rol.JEFE)

        assertEquals(Busqueda.terminosDePersona("María José", "Pérez", "72345678"), cliente.aDocumento()["terminos"])
        assertFalse(tecnico.aDocumento().containsKey("terminos"))
        assertFalse(jefe.aDocumento().containsKey("terminos"))
        assertEquals(cliente.copy(contrasena = ""), cliente.aDocumento().aUsuario("u4"))
    }

    @Test
    fun vehiculoLlevaTerminosDePlacaYLaLecturaLosIgnora() {
        val vehiculo = Vehiculo("v2", "u1", "ABC-123", "Toyota", "Yaris", 2018, "Rojo")

        val documento = vehiculo.aDocumento()

        assertEquals(Busqueda.terminosDePlaca("ABC-123"), documento["terminosPlaca"])
        assertEquals(vehiculo, documento.aVehiculo("v2"))
    }

    @Test
    fun usuarioConRolDesconocidoFallaEnLugarDeAdivinar() {
        val documento = mapOf<String, Any?>("nombres" to "X", "rol" to "SUPERUSUARIO")

        val resultado = runCatching { documento.aUsuario("u3") }

        assertEquals(true, resultado.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun vehiculoSeConvierteIdaYVuelta() {
        val vehiculo = Vehiculo("v1", "u1", "ABC-123", "Toyota", "Yaris", 2018, "Rojo", activo = false)

        assertEquals(vehiculo, vehiculo.aDocumento().aVehiculo("v1"))
    }

    @Test
    fun solicitudSeConvierteIdaYVueltaConFotosYOrden() {
        val solicitud = Solicitud(
            id = "s1", clienteId = "u1", vehiculoId = "v1", descripcion = "No arranca",
            sintomas = listOf("Luces tenues", "Ruido"), fechaLlegada = 10L, fotos = listOf(foto),
            estado = EstadoSolicitud.ATENDIDA, creadaEn = 5L, ordenId = "o1"
        )

        assertEquals(solicitud, solicitud.aDocumento().aSolicitud("s1"))
    }

    private val resumenCliente = ResumenCliente("Ana", "Ruiz", "12345678", "999111222")
    private val resumenVehiculo = ResumenVehiculo("ABC-123", "Toyota", "Yaris", 2018, "Rojo")
    private val resumenTecnico = ResumenTecnico("Luis", "Ramos")

    @Test
    fun solicitudConInstantaneasLasEscribeComoMapasYLasLee() {
        val solicitud = Solicitud(
            id = "s1", clienteId = "u1", vehiculoId = "v1", descripcion = "No arranca", sintomas = emptyList(),
            fechaLlegada = 10L, fotos = emptyList(), estado = EstadoSolicitud.PENDIENTE, creadaEn = 5L,
            cliente = resumenCliente, vehiculo = resumenVehiculo
        )

        val documento = solicitud.aDocumento()

        assertEquals(
            mapOf("nombres" to "Ana", "apellidos" to "Ruiz", "dni" to "12345678", "telefono" to "999111222"),
            documento["cliente"]
        )
        assertEquals(
            mapOf("placa" to "ABC-123", "marca" to "Toyota", "modelo" to "Yaris", "anio" to 2018, "color" to "Rojo"),
            documento["vehiculo"]
        )
        assertFalse(documento.containsKey("tecnico"))
        assertEquals(solicitud, documento.aSolicitud("s1"))
    }

    @Test
    fun solicitudAnteriorAlRellenoSeLeeSinInstantaneasYNoLasEscribe() {
        val legado = mapOf<String, Any?>(
            "clienteId" to "u1", "vehiculoId" to "v1", "descripcion" to "No arranca", "estado" to "PENDIENTE", "creadaEn" to 5L
        )

        val solicitud = legado.aSolicitud("s1")

        assertNull(solicitud.cliente)
        assertNull(solicitud.vehiculo)
        assertFalse(solicitud.aDocumento().containsKey("cliente"))
        assertFalse(solicitud.aDocumento().containsKey("vehiculo"))
    }

    @Test
    fun ordenConInstantaneasSeConvierteIdaYVueltaYAnteriorAlRellenoSeLeeSinEllas() {
        val orden = Orden(
            id = "o1", numero = "ORD-0001", clienteId = "u1", vehiculoId = "v1", solicitudId = null, falla = "Batería",
            kilometraje = 10, combustible = NivelCombustible.MEDIO, observaciones = "", fotos = emptyList(),
            tecnicoId = "u9", estado = EstadoOrden.INGRESADO, historial = emptyList(), creadaEn = 1L,
            cliente = resumenCliente, vehiculo = resumenVehiculo, tecnico = resumenTecnico
        )

        val documento = orden.aDocumento()

        assertEquals(mapOf("nombres" to "Luis", "apellidos" to "Ramos"), documento["tecnico"])
        assertEquals(orden, documento.aOrden("o1"))
        val legada = orden.copy(cliente = null, vehiculo = null, tecnico = null).aDocumento()
            .filterKeys { it !in setOf("cliente", "vehiculo", "tecnico") }.aOrden("o1")
        assertNull(legada.cliente)
        assertNull(legada.vehiculo)
        assertNull(legada.tecnico)
        assertEquals("Batería", legada.falla)
    }

    @Test
    fun ordenCompletaSeConvierteIdaYVuelta() {
        val orden = Orden(
            id = "o1", numero = "ORD-0001", clienteId = "u1", vehiculoId = "v1", solicitudId = "s1",
            falla = "Batería", kilometraje = 45_000, combustible = NivelCombustible.TRES_CUARTOS,
            observaciones = "Rayón", fotos = listOf(foto), tecnicoId = "u9", estado = EstadoOrden.LISTO,
            historial = listOf(
                CambioEstado(EstadoOrden.INGRESADO, 1L, "u2", usuarioNombre = "Freddy Correa"),
                CambioEstado(EstadoOrden.ANULADA, 2L, "u2", motivo = "Duplicada")
            ),
            creadaEn = 1L, motivoAnulacion = "Duplicada", avisosEnviados = 2, ultimoAviso = 3L,
            editadaPor = "u2", editadaEn = 4L, conformidad = Conformidad(5L, "u1", usuarioNombre = "Freddy Correa"),
            observacionesCliente = listOf(ObservacionCliente("Falta limpiar", "Se limpia", 6L, "u1"))
        )

        assertEquals(orden, orden.aDocumento().aOrden("o1"))
    }

    @Test
    fun ordenConCamposOpcionalesAusentesUsaSusValoresPorDefecto() {
        val minimo = mapOf<String, Any?>(
            "numero" to "ORD-0002", "combustible" to "LLENO", "estado" to "INGRESADO"
        )

        val orden = minimo.aOrden("o2")

        assertNull(orden.solicitudId)
        assertNull(orden.conformidad)
        assertNull(orden.ultimoAviso)
        assertEquals(0, orden.avisosEnviados)
        assertEquals(emptyList<Foto>(), orden.fotos)
        assertEquals(emptyList<CambioEstado>(), orden.historial)
    }

    @Test
    fun firestoreEntregaLosNumerosComoLargosYSeAceptanComoEnteros() {
        val documento = mapOf<String, Any?>(
            "numero" to "ORD-0003", "combustible" to "MEDIO", "estado" to "ENTREGADO",
            "kilometraje" to 12_000L, "creadaEn" to 7, "avisosEnviados" to 1L
        )

        val orden = documento.aOrden("o3")

        assertEquals(12_000, orden.kilometraje)
        assertEquals(7L, orden.creadaEn)
        assertEquals(1, orden.avisosEnviados)
    }

    @Test
    fun avanceSeConvierteIdaYVuelta() {
        val avance = Avance("a1", "o1", "u9", TipoAvance.TRABAJO_REALIZADO, "Cambio de fusible", listOf(foto), 8L)

        assertEquals(avance, avance.aDocumento().aAvance("a1"))
    }

    @Test
    fun notificacionSeConvierteIdaYVuelta() {
        val aviso = Notificacion("n1", "u1", "Listo", "Tu vehículo está listo", 9L, leida = true, ordenId = "o1", esIngreso = true)

        assertEquals(aviso, aviso.aDocumento().aNotificacion("n1"))
    }
}
