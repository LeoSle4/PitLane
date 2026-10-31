package com.leosle.pitlane.arquitectura.repositorio

import com.leosle.pitlane.arquitectura.repositorio.impl.VehiculoRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.TecnicoRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.SolicitudRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.PerfilRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.OrdenRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.ClienteRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.AvanceRepository
import com.leosle.pitlane.arquitectura.repositorio.impl.AuthRepository
import com.leosle.pitlane.modelo.Conformidad
import com.leosle.pitlane.modelo.DatosOrden
import com.leosle.pitlane.modelo.DatosRegistroCliente
import com.leosle.pitlane.modelo.EstadoOrden
import com.leosle.pitlane.modelo.EstadoSolicitud
import com.leosle.pitlane.modelo.Foto
import com.leosle.pitlane.modelo.NivelCombustible
import com.leosle.pitlane.modelo.TipoAvance
import com.leosle.pitlane.arquitectura.datasource.local.BaseDatosLocal
import com.leosle.pitlane.arquitectura.datasource.local.DocumentosDataSource
import com.leosle.pitlane.arquitectura.datasource.local.FotoStorage
import com.leosle.pitlane.arquitectura.datasource.local.UsuariosDataSource
import com.leosle.pitlane.util.Reloj
import com.leosle.pitlane.util.Resultado
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test

/** Pruebas de las reglas de negocio de los repositorios locales a través de sus interfaces. */
class ReglasDeNegocioTest {

    private var ahora = 1_800_000_000_000L
    private lateinit var bd: BaseDatosLocal
    private lateinit var auth: IAuthRepository
    private lateinit var vehiculos: IVehiculoRepository
    private lateinit var solicitudes: ISolicitudRepository
    private lateinit var ordenes: IOrdenRepository
    private lateinit var avances: IAvanceRepository
    private lateinit var tecnicos: ITecnicoRepository
    private lateinit var clientes: IClienteRepository
    private lateinit var perfiles: IPerfilRepository
    private lateinit var sesiones: UsuariosDataSource

    @get:Rule
    val carpetaTemporal = TemporaryFolder()

    @Before
    fun preparar() {
        val reloj = Reloj { ahora }
        bd = BaseDatosLocal(conSemilla = true, reloj = reloj)
        val usuarios = UsuariosDataSource(bd)
        sesiones = usuarios
        val documentos = DocumentosDataSource(bd)
        val fotos = FotoStorage(carpetaTemporal.newFolder("fotos"))
        auth = AuthRepository(usuarios)
        vehiculos = VehiculoRepository(usuarios, documentos)
        solicitudes = SolicitudRepository(usuarios, documentos, fotos, reloj)
        ordenes = OrdenRepository(usuarios, documentos, fotos, reloj)
        avances = AvanceRepository(usuarios, documentos, fotos, reloj)
        tecnicos = TecnicoRepository(usuarios, documentos)
        clientes = ClienteRepository(usuarios, documentos)
        perfiles = PerfilRepository(usuarios)
    }

    private fun foto() = Foto("f", "recurso:foto_demo_frente", ahora)

    private fun mensajeDe(resultado: Resultado<*>): String = (resultado as Resultado.Error).mensaje

    // ---------------------------------------------------------------------------------------------
    // Acceso
    // ---------------------------------------------------------------------------------------------

    @Test
    fun iniciarSesionConCredencialesIncorrectasNoIndicaCualFallo() = runBlocking {
        val resultado = auth.iniciarSesion("jefe@freddy.pe", "otra")
        assertEquals("Correo o contraseña incorrectos", mensajeDe(resultado))
    }

    @Test
    fun iniciarSesionConCuentaDesactivadaMuestraAvisoDelTaller() = runBlocking {
        val resultado = auth.iniciarSesion("pedro@freddy.pe", "Tecnico123")
        assertEquals("Tu cuenta está desactivada. Comunícate con el taller", mensajeDe(resultado))
    }

    @Test
    fun iniciarSesionCorrectaDejaAlUsuarioActivo() = runBlocking {
        val resultado = auth.iniciarSesion("jefe@freddy.pe", "Taller2026")
        assertTrue(resultado is Resultado.Exito)
        assertEquals("u-jefe", auth.usuarioActual.value?.id)
    }

    @Test
    fun registrarClienteUneElRegistroDelJefeConElMismoDni() = runBlocking {
        val resultado = auth.registrarCliente(
            DatosRegistroCliente("Jorge", "Paredes Luna", "43219876", "934567890", "jorge@correo.com", "Jorge2026")
        )
        val usuario = (resultado as Resultado.Exito).valor
        assertEquals("u-cli-jorge", usuario.id)
        assertTrue(usuario.tieneCuenta)
        assertTrue(bd.vehiculos.value.any { it.clienteId == "u-cli-jorge" })
        assertEquals(1, bd.usuarios.value.count { it.dni == "43219876" })
    }

    @Test
    fun registrarClienteConCorreoRepetidoMuestraSuMensaje() = runBlocking {
        val resultado = auth.registrarCliente(
            DatosRegistroCliente("Otra", "Persona", "70000001", "900000001", "cliente@freddy.pe", "Clave1234")
        )
        assertEquals("Este correo ya está registrado", mensajeDe(resultado))
    }

    // ---------------------------------------------------------------------------------------------
    // Vehículos
    // ---------------------------------------------------------------------------------------------

    @Test
    fun registrarVehiculoConPlacaExistenteUsaMensajeSegunQuienRegistra() = runBlocking {
        val porCliente = vehiculos.registrarVehiculo("u-cli-lucia", "abc123", "Kia", "Rio", 2020, "Rojo", false)
        assertEquals("Esta placa ya está registrada. Si es tuya, comunícate con el taller", mensajeDe(porCliente))
        val porJefe = vehiculos.registrarVehiculo("u-cli-lucia", "ABC-123", "Kia", "Rio", 2020, "Rojo", true)
        assertEquals("Esta placa ya está registrada a nombre de Ana Torres Vega", mensajeDe(porJefe))
    }

    @Test
    fun registrarVehiculoGuardaLaPlacaEnMayusculasConGuion() = runBlocking {
        val resultado = vehiculos.registrarVehiculo("u-cli-lucia", "qwe987", "Kia", "Rio", 2020, "Rojo", false)
        assertEquals("QWE-987", (resultado as Resultado.Exito).valor.placa)
    }

    @Test
    fun clienteNoEliminaVehiculoQueEstaEnElTaller() = runBlocking {
        val resultado = vehiculos.eliminarVehiculo("v-rio", solicitadoPorJefe = false)
        assertEquals("No puedes eliminar un vehículo que está en el taller", mensajeDe(resultado))
    }

    @Test
    fun jefeNoEliminaVehiculoConHistorialDeOrdenes() = runBlocking {
        val resultado = vehiculos.eliminarVehiculo("v-sail", solicitadoPorJefe = true)
        assertEquals("No se puede eliminar un vehículo con historial de órdenes", mensajeDe(resultado))
    }

    @Test
    fun eliminarVehiculoSinOrdenesLoQuitaYConHistorialSoloLoOculta() = runBlocking {
        assertTrue(vehiculos.eliminarVehiculo("v-accent", solicitadoPorJefe = false) is Resultado.Exito)
        assertFalse(bd.vehiculos.value.any { it.id == "v-accent" })
        bd.ordenes.value = bd.ordenes.value.map { if (it.id == "o-1") it.copy(estado = EstadoOrden.ANULADA) else it }
        bd.ordenes.value = bd.ordenes.value.filterNot { it.id == "o-4" }
        assertTrue(vehiculos.eliminarVehiculo("v-sail", solicitadoPorJefe = false) is Resultado.Exito)
        assertFalse(bd.vehiculos.value.first { it.id == "v-sail" }.activo)
    }

    // ---------------------------------------------------------------------------------------------
    // Solicitudes
    // ---------------------------------------------------------------------------------------------

    @Test
    fun solicitudParaVehiculoConAtencionEnCursoSeRechaza() = runBlocking {
        val resultado = solicitudes.registrarSolicitud(
            "u-cli-ana", "v-accent", "La luz del tablero parpadea siempre", emptyList(), ahora, emptyList()
        )
        assertEquals("Este vehículo ya tiene una atención en curso", mensajeDe(resultado))
    }

    @Test
    fun solicitudConFechaAnteriorAHoyOConDescripcionCortaSeRechaza() = runBlocking {
        val ayer = ahora - 2 * 24 * 3_600_000L
        val conFechaPasada = solicitudes.registrarSolicitud(
            "u-cli-ana", "v-yaris", "El auto no arranca por las mañanas", emptyList(), ayer, emptyList()
        )
        assertEquals("La fecha de llegada no puede ser anterior a hoy", mensajeDe(conFechaPasada))
        val corta = solicitudes.registrarSolicitud("u-cli-ana", "v-yaris", "corta", emptyList(), ahora, emptyList())
        assertEquals("Describe la falla con al menos 10 caracteres", mensajeDe(corta))
    }

    @Test
    fun soloSePuedeCancelarUnaSolicitudPendiente() = runBlocking {
        assertTrue(solicitudes.cancelarSolicitud("s-3") is Resultado.Exito)
        assertEquals(EstadoSolicitud.CANCELADA, bd.solicitudes.value.first { it.id == "s-3" }.estado)
        assertNotNull(mensajeDe(solicitudes.cancelarSolicitud("s-1")))
    }

    // ---------------------------------------------------------------------------------------------
    // Órdenes
    // ---------------------------------------------------------------------------------------------

    private fun datosOrden(vehiculoId: String, clienteId: String, solicitudId: String?, fotos: List<Foto>) = DatosOrden(
        clienteId, vehiculoId, solicitudId, "El motor se apaga solo", 1000, NivelCombustible.MEDIO, "", fotos, "u-tec-marco"
    )

    @Test
    fun soloElJefeRegistraYAnulaOrdenes() = runBlocking {
        val mensaje = "Solo el Jefe de Taller puede realizar esta acción"
        assertEquals(mensaje, mensajeDe(ordenes.registrarOrden(datosOrden("v-versa", "u-cli-carlos", "s-3", listOf(foto())), "u-tec-luis")))
        assertEquals(mensaje, mensajeDe(ordenes.anularOrden("o-4", "Motivo", "u-tec-marco")))
    }

    @Test
    fun registrarOrdenRechazaUnVehiculoQueNoEsDelCliente() = runBlocking {
        val resultado = ordenes.registrarOrden(datosOrden("v-versa", "u-cli-ana", null, listOf(foto())), "u-jefe")
        assertEquals("El vehículo no pertenece a ese cliente", mensajeDe(resultado))
    }

    @Test
    fun buscarClientesExigeTresCaracteresYEncuentraPorDniOPlaca() = runBlocking {
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        assertTrue(clientes.buscarClientes("71").isEmpty())
        assertEquals("u-cli-ana", clientes.buscarClientes("71234").single().cliente.id)
        val porPlaca = clientes.buscarClientes("xyz789").single()
        assertEquals("u-cli-ana", porPlaca.cliente.id)
        assertEquals(listOf("XYZ-789"), porPlaca.placasCoincidentes)
        assertEquals(listOf("ABC-123", "XYZ-789"), porPlaca.placas)
    }

    @Test
    fun jefeNoRegistraClienteConDniRepetido() = runBlocking {
        val resultado = clientes.registrarCliente("Otra", "Persona", "71234567", "955555555", "otra@correo.pe")
        assertEquals("Ya existe un cliente con ese DNI", mensajeDe(resultado))
    }

    @Test
    fun jefeNoEliminaClienteConCuentaNiConHistorialDeOrdenes() = runBlocking {
        assertEquals(
            "Este cliente tiene cuenta en la app y solo él puede eliminarla",
            mensajeDe(clientes.eliminarCliente("u-cli-ana"))
        )
        val sinCuenta = (clientes.registrarCliente("Rosa", "Pinto", "70000001", "955555555", "") as Resultado.Exito).valor
        assertTrue(clientes.eliminarCliente(sinCuenta.id) is Resultado.Exito)
    }

    @Test
    fun trasMarcarListoElAvanceYaNoSeEditaNiSeElimina() = runBlocking {
        avances.registrarAvance("o-3", "u-tec-luis", TipoAvance.TRABAJO_REALIZADO, "Se reparó el cableado del radio", emptyList())
        val avanceId = bd.avances.value.last().id
        ordenes.marcarListo("o-3", "u-tec-luis")
        assertEquals("La orden ya no admite cambios en sus avances", mensajeDe(avances.eliminarAvance(avanceId, "u-tec-luis")))
    }

    @Test
    fun cadaUsuarioSoloVeLasOrdenesQueLeCorresponden() = runBlocking {
        suspend fun ve(ordenId: String, usuarioId: String) = ordenes.observarOrden(ordenId, usuarioId).first() != null
        assertTrue(ve("o-2", "u-jefe"))
        assertTrue(ve("o-2", "u-tec-luis"))
        assertTrue(ve("o-2", "u-cli-ana"))
        assertFalse(ve("o-2", "u-tec-marco"))
        assertFalse(ve("o-2", "u-cli-carlos"))
    }

    @Test
    fun unClienteNoRecibeLosAvancesDeLaOrdenDeOtro() = runBlocking {
        avances.registrarAvance("o-3", "u-tec-luis", TipoAvance.HALLAZGO, "Cable cortado en el radio", emptyList())
        assertEquals(bd.avances.value.count { it.ordenId == "o-3" }, avances.observarAvances("o-3", "u-cli-carlos").first().size)
        assertTrue(avances.observarAvances("o-3", "u-cli-ana").first().isEmpty())
    }

    @Test
    fun registrarUnIngresoSinSolicitudAtiendeLasPendientesDelMismoVehiculo() = runBlocking {
        assertEquals(com.leosle.pitlane.modelo.EstadoSolicitud.PENDIENTE, bd.solicitudes.value.first { it.id == "s-3" }.estado)
        val orden = (ordenes.registrarOrden(datosOrden("v-versa", "u-cli-carlos", null, listOf(foto())), "u-jefe") as Resultado.Exito).valor.orden
        val atendida = bd.solicitudes.value.first { it.id == "s-3" }
        assertEquals(com.leosle.pitlane.modelo.EstadoSolicitud.ATENDIDA, atendida.estado)
        assertEquals(orden.id, atendida.ordenId)
    }

    @Test
    fun soloUnTecnicoActivoPuedeSerAsignadoAUnaOrden() = runBlocking {
        val comoTecnicoElCliente = datosOrden("v-versa", "u-cli-carlos", null, listOf(foto())).copy(tecnicoId = "u-cli-ana")
        assertEquals("Elige un técnico activo", mensajeDe(ordenes.registrarOrden(comoTecnicoElCliente, "u-jefe")))
    }

    @Test
    fun soloElJefeEditaYReenviaAvisosYAgregaFotos() = runBlocking {
        val mensaje = "Solo el Jefe de Taller puede realizar esta acción"
        assertEquals(mensaje, mensajeDe(ordenes.editarOrden("o-4", "Falla nueva", 10, NivelCombustible.MEDIO, "", "u-tec-marco", "u-tec-marco")))
        assertEquals(mensaje, mensajeDe(ordenes.reenviarAviso("o-2", "u-tec-luis")))
        assertEquals(mensaje, mensajeDe(ordenes.agregarFotosOrden("o-4", listOf(foto()), "u-cli-carlos")))
    }

    @Test
    fun trasEntregarLaOrdenSaleDelPanelYElVehiculoQuedaSinOrdenActiva() = runBlocking {
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        assertTrue(ordenes.observarOrdenesActivas().first().any { it.orden.id == "o-2" })
        ordenes.registrarConformidad("o-2", "u-jefe")
        ordenes.marcarEntregada("o-2", "u-jefe")
        assertFalse(ordenes.observarOrdenesActivas().first().any { it.orden.id == "o-2" })
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        val yaris = vehiculos.observarVehiculosDeCliente("u-cli-ana").first().first { it.vehiculo.id == "v-yaris" }
        assertEquals(null, yaris.ordenActiva)
    }

    @Test
    fun clienteConOrdenEnCursoNoEliminaSuCuenta() = runBlocking {
        val resultado = perfiles.eliminarCuenta("u-cli-carlos", "Cliente123")
        assertEquals("Tienes un vehículo en el taller. Podrás eliminar tu cuenta cuando se entregue", mensajeDe(resultado))
    }

    @Test
    fun lasListasDeOrdenesSoloSeEntreganASusDuenosOAlJefe() = runBlocking {
        sesiones.iniciarSesion("carlos@freddy.pe", "Cliente123")
        assertTrue(ordenes.observarOrdenesDeCliente("u-cli-carlos").first().isNotEmpty())
        assertTrue(ordenes.observarOrdenesDeCliente("u-cli-ana").first().isEmpty())
        assertTrue(ordenes.observarOrdenesActivas().first().isEmpty())
        assertTrue(ordenes.observarOrdenesDeTecnico("u-tec-luis").first().isEmpty())
        sesiones.iniciarSesion("luis@freddy.pe", "Tecnico123")
        assertTrue(ordenes.observarOrdenesDeTecnico("u-tec-marco").first().isEmpty())
        assertTrue(ordenes.observarOrdenesDeCliente("u-cli-ana").first().isEmpty())
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        assertTrue(ordenes.observarOrdenesDeCliente("u-cli-ana").first().isNotEmpty())
        assertTrue(ordenes.observarOrdenesActivas().first().isNotEmpty())
    }

    @Test
    fun soloElJefeVeLosClientesLosTecnicosYLasSolicitudesPendientes() = runBlocking {
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        assertTrue(clientes.observarClientes().first().isEmpty())
        assertTrue(tecnicos.observarTecnicos().first().isEmpty())
        assertTrue(solicitudes.observarSolicitudesPendientes().first().isEmpty())
        assertTrue(clientes.buscarClientes("Torres").isEmpty())
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        assertTrue(clientes.observarClientes().first().isNotEmpty())
        assertTrue(tecnicos.observarTecnicos().first().isNotEmpty())
        assertTrue(clientes.buscarClientes("Torres").isNotEmpty())
    }

    @Test
    fun elClienteNoVeVehiculosSolicitudesNiPerfilDeOtro() = runBlocking {
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        assertTrue(vehiculos.observarVehiculosDeCliente("u-cli-ana").first().isNotEmpty())
        assertTrue(vehiculos.observarVehiculosDeCliente("u-cli-carlos").first().isEmpty())
        assertEquals(null, vehiculos.observarVehiculo("v-rio").first())
        assertTrue(solicitudes.observarSolicitudesDeCliente("u-cli-carlos").first().isEmpty())
        assertEquals(null, perfiles.observarPerfil("u-cli-carlos").first())
        assertEquals("u-cli-ana", perfiles.observarPerfil("u-cli-ana").first()?.id)
    }

    @Test
    fun registrarOrdenSinFotosSeRechaza() = runBlocking {
        val resultado = ordenes.registrarOrden(datosOrden("v-versa", "u-cli-carlos", "s-3", emptyList()), "u-jefe")
        assertEquals("Agrega al menos una foto del estado del vehículo", mensajeDe(resultado))
    }

    @Test
    fun registrarOrdenCreaNumeroCorrelativoAtiendeSolicitudYNotificaAlCliente() = runBlocking {
        val resultado = ordenes.registrarOrden(datosOrden("v-versa", "u-cli-carlos", "s-3", listOf(foto())), "u-jefe")
        val ingreso = (resultado as Resultado.Exito).valor
        assertEquals("ORD-0005", ingreso.orden.numero)
        assertEquals(EstadoOrden.INGRESADO, ingreso.orden.estado)
        assertTrue(ingreso.clienteNotificado)
        assertEquals(EstadoSolicitud.ATENDIDA, bd.solicitudes.value.first { it.id == "s-3" }.estado)
        assertTrue(bd.notificaciones.value.any { it.mensaje == "Tu vehículo JKL-654 fue ingresado. Orden ORD-0005" })
    }

    @Test
    fun registrarOrdenDeClienteSinCuentaNoGeneraNotificacion() = runBlocking {
        bd.ordenes.value = bd.ordenes.value.filterNot { it.id == "o-4" }
        val antes = bd.notificaciones.value.size
        val resultado = ordenes.registrarOrden(datosOrden("v-sail", "u-cli-jorge", null, listOf(foto())), "u-jefe")
        assertFalse((resultado as Resultado.Exito).valor.clienteNotificado)
        assertEquals(antes, bd.notificaciones.value.size)
    }

    @Test
    fun registrarOrdenParaVehiculoConOrdenEnCursoSeRechaza() = runBlocking {
        val resultado = ordenes.registrarOrden(datosOrden("v-rio", "u-cli-carlos", null, listOf(foto())), "u-jefe")
        assertEquals("Este vehículo ya tiene una orden en curso", mensajeDe(resultado))
    }

    @Test
    fun soloElTecnicoAsignadoIniciaLaReparacionYElClienteRecibeElAviso() = runBlocking {
        assertEquals("Esta orden no está asignada a ti", mensajeDe(ordenes.iniciarReparacion("o-4", "u-tec-luis")))
        assertTrue(ordenes.iniciarReparacion("o-4", "u-tec-marco") is Resultado.Exito)
        assertEquals(EstadoOrden.EN_REPARACION, bd.ordenes.value.first { it.id == "o-4" }.estado)
        assertEquals(2, bd.ordenes.value.first { it.id == "o-4" }.historial.size)
    }

    @Test
    fun soloElJefePuedeMarcarComoEntregadaUnaOrdenLista() = runBlocking {
        assertEquals(
            "Solo se puede entregar una orden lista para recoger",
            mensajeDe(ordenes.marcarEntregada("o-3", "u-jefe"))
        )
        assertEquals(
            "Solo el Jefe de Taller puede realizar esta acción",
            mensajeDe(ordenes.marcarEntregada("o-2", "u-cli-ana"))
        )
        ordenes.registrarConformidad("o-2", "u-jefe")
        val avisosAntes = bd.notificaciones.value.size
        assertTrue(ordenes.marcarEntregada("o-2", "u-jefe") is Resultado.Exito)
        val entregada = bd.ordenes.value.first { it.id == "o-2" }
        assertEquals(EstadoOrden.ENTREGADO, entregada.estado)
        assertEquals(EstadoOrden.ENTREGADO, entregada.historial.last().estado)
        assertEquals("u-jefe", entregada.historial.last().usuarioId)
        assertEquals(avisosAntes, bd.notificaciones.value.size)
    }

    @Test
    fun unaOrdenListaImpideEliminarElVehiculoHastaQueSeEntrega() = runBlocking {
        assertEquals(
            "No puedes eliminar un vehículo que está en el taller",
            mensajeDe(vehiculos.eliminarVehiculo("v-yaris", solicitadoPorJefe = false))
        )
        ordenes.registrarConformidad("o-2", "u-jefe")
        ordenes.marcarEntregada("o-2", "u-jefe")
        assertTrue(vehiculos.eliminarVehiculo("v-yaris", solicitadoPorJefe = false) is Resultado.Exito)
    }

    @Test
    fun laConformidadLaRegistraElJefeOElClienteDeLaOrdenSoloEnUnaOrdenListaYUnaSolaVez() = runBlocking {
        assertEquals(
            "Solo se puede registrar la conformidad de una orden lista para recoger",
            mensajeDe(ordenes.registrarConformidad("o-3", "u-jefe"))
        )
        assertEquals(
            "Solo el cliente de la orden o el Jefe de Taller pueden registrar la conformidad",
            mensajeDe(ordenes.registrarConformidad("o-2", "u-cli-carlos"))
        )
        assertEquals(
            "Solo el cliente de la orden o el Jefe de Taller pueden registrar la conformidad",
            mensajeDe(ordenes.registrarConformidad("o-2", "u-tec-luis"))
        )
        assertTrue(ordenes.registrarConformidad("o-2", "u-jefe") is Resultado.Exito)
        val orden = bd.ordenes.value.first { it.id == "o-2" }
        val nombreJefe = bd.usuarios.value.first { it.id == "u-jefe" }.nombreCompleto
        assertEquals(Conformidad(ahora, "u-jefe", nombreJefe), orden.conformidad)
        assertEquals(EstadoOrden.LISTO, orden.estado)
        assertEquals("La conformidad ya está registrada", mensajeDe(ordenes.registrarConformidad("o-2", "u-jefe")))
    }

    @Test
    fun laEntregaExigeLaConformidadDelClienteRegistrada() = runBlocking {
        assertEquals(
            "Registra la conformidad del cliente antes de entregar",
            mensajeDe(ordenes.marcarEntregada("o-2", "u-jefe"))
        )
        assertEquals(EstadoOrden.LISTO, bd.ordenes.value.first { it.id == "o-2" }.estado)
        ordenes.registrarConformidad("o-2", "u-jefe")
        assertTrue(ordenes.marcarEntregada("o-2", "u-jefe") is Resultado.Exito)
    }

    @Test
    fun laObservacionDelClienteSeGuardaSinCambiarElEstadoYSeConservaTrasLaConformidad() = runBlocking {
        val mensajeCorta = "Describe la observación con al menos 10 caracteres"
        assertEquals(mensajeCorta, mensajeDe(ordenes.registrarObservacionCliente("o-2", "Muy corta", "Revisar", "u-jefe")))
        assertEquals(mensajeCorta, mensajeDe(ordenes.registrarObservacionCliente("o-2", "   ", "", "u-jefe")))
        assertEquals(
            "Solo se puede registrar una observación en una orden lista para recoger",
            mensajeDe(ordenes.registrarObservacionCliente("o-3", "El freno aún suena al frenar", "", "u-jefe"))
        )
        assertEquals(
            "Solo el Jefe de Taller puede realizar esta acción",
            mensajeDe(ordenes.registrarObservacionCliente("o-2", "El freno aún suena al frenar", "", "u-cli-ana"))
        )
        assertTrue(bd.ordenes.value.first { it.id == "o-2" }.observacionesCliente.isEmpty())
        ahora += 60_000L
        assertTrue(ordenes.registrarObservacionCliente("o-2", "El freno aún suena al frenar", "Se revisa mañana", "u-jefe") is Resultado.Exito)
        val orden = bd.ordenes.value.first { it.id == "o-2" }
        assertEquals(EstadoOrden.LISTO, orden.estado)
        val observacion = orden.observacionesCliente.single()
        assertEquals("El freno aún suena al frenar", observacion.texto)
        assertEquals("Se revisa mañana", observacion.acuerdo)
        assertEquals(ahora, observacion.fechaHora)
        assertEquals("u-jefe", observacion.usuarioId)
        ordenes.registrarConformidad("o-2", "u-jefe")
        assertEquals(1, bd.ordenes.value.first { it.id == "o-2" }.observacionesCliente.size)
    }

    @Test
    fun laBusquedaDeOrdenesListasFiltraPorPlacaNombreDniONumeroYSoloMuestraEseEstado() = runBlocking {
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        for (consulta in listOf("abc-123", "ABC123", "torres", "71234567", "ORD-0002", "0002")) {
            assertEquals(consulta, listOf("o-2"), ordenes.buscarOrdenesListas(consulta).map { it.orden.id })
        }
        assertEquals(listOf("o-2"), ordenes.buscarOrdenesListas("").map { it.orden.id })
        assertTrue(ordenes.buscarOrdenesListas("Carlos").isEmpty())
        assertTrue(ordenes.buscarOrdenesListas("XYZ-789").isEmpty())
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        assertTrue(ordenes.buscarOrdenesListas("ABC-123").isEmpty())
    }

    @Test
    fun elClienteFirmaLaConformidadDeSuPropiaOrdenYLuegoElJefeLaEntrega() = runBlocking {
        val resultado = ordenes.registrarConformidad("o-2", "u-cli-ana")

        assertTrue(resultado is Resultado.Exito)
        val conformidad = bd.ordenes.value.first { it.id == "o-2" }.conformidad
        assertEquals("u-cli-ana", conformidad?.usuarioId)
        assertEquals(bd.usuarios.value.first { it.id == "u-cli-ana" }.nombreCompleto, conformidad?.usuarioNombre)
        assertEquals("La conformidad ya está registrada", mensajeDe(ordenes.registrarConformidad("o-2", "u-jefe")))
        assertTrue(ordenes.marcarEntregada("o-2", "u-jefe") is Resultado.Exito)
    }

    @Test
    fun laOrdenEntregadaResuelveQuienRegistroLaConformidadYLaEntrega() = runBlocking {
        sesiones.iniciarSesion("jefe@freddy.pe", "Taller2026")
        ordenes.registrarConformidad("o-2", "u-jefe")
        ordenes.marcarEntregada("o-2", "u-jefe")
        val detalle = ordenes.observarOrden("o-2", "u-jefe").first()!!
        assertEquals("u-jefe", detalle.conformidadRegistradaPor?.id)
        assertEquals("u-jefe", detalle.entregadaPor?.id)
        val nombreJefe = bd.usuarios.value.first { it.id == "u-jefe" }.nombreCompleto
        assertEquals(nombreJefe, detalle.orden.conformidad?.usuarioNombre)
        assertEquals(nombreJefe, detalle.orden.historial.last { it.estado == EstadoOrden.ENTREGADO }.usuarioNombre)
        sesiones.iniciarSesion("cliente@freddy.pe", "Cliente123")
        assertEquals("u-jefe", ordenes.observarOrden("o-2", "u-cli-ana").first()!!.entregadaPor?.id)
    }

    @Test
    fun marcarListoExigeUnTrabajoRealizado() = runBlocking {
        assertEquals(
            "Registra el trabajo realizado antes de marcar como listo",
            mensajeDe(ordenes.marcarListo("o-3", "u-tec-luis"))
        )
        avances.registrarAvance("o-3", "u-tec-luis", TipoAvance.TRABAJO_REALIZADO, "Se reparó el cableado del radio", emptyList())
        assertTrue(ordenes.marcarListo("o-3", "u-tec-luis") is Resultado.Exito)
        assertEquals(EstadoOrden.LISTO, bd.ordenes.value.first { it.id == "o-3" }.estado)
        assertTrue(bd.notificaciones.value.any { it.mensaje == "Tu vehículo BCD-456 está listo para recoger" })
    }

    @Test
    fun registrarAvanceEnOrdenIngresadaPideIniciarLaReparacion() = runBlocking {
        val resultado = avances.registrarAvance("o-4", "u-tec-marco", TipoAvance.HALLAZGO, "Fusible quemado en la caja", emptyList())
        assertEquals("Primero inicia la reparación", mensajeDe(resultado))
    }

    @Test
    fun reenviarAvisoSolicitaEsperar24HorasDesdeElUltimoAviso() = runBlocking {
        assertEquals("Disponible en 18 h", mensajeDe(ordenes.reenviarAviso("o-2", "u-jefe")))
        ahora += 25 * 3_600_000L
        val resultado = ordenes.reenviarAviso("o-2", "u-jefe")
        assertEquals(2, (resultado as Resultado.Exito).valor.avisosEnviados)
        assertEquals("Disponible en 24 h", mensajeDe(ordenes.reenviarAviso("o-2", "u-jefe")))
    }

    @Test
    fun soloSeAnulaUnaOrdenIngresadaConMotivo() = runBlocking {
        assertEquals("Escribe el motivo de la anulación", mensajeDe(ordenes.anularOrden("o-4", " ", "u-jefe")))
        assertNotNull(mensajeDe(ordenes.anularOrden("o-3", "Motivo", "u-jefe")))
        assertTrue(ordenes.anularOrden("o-4", "Registrada por error", "u-jefe") is Resultado.Exito)
        assertEquals("Registrada por error", bd.ordenes.value.first { it.id == "o-4" }.motivoAnulacion)
    }

    @Test
    fun laOrdenDebeConservarAlMenosUnaFotoMientrasEstaIngresada() = runBlocking {
        assertEquals("La orden debe tener al menos una foto", mensajeDe(ordenes.eliminarFotoOrden("o-4", "f-o4-1", "u-jefe")))
        assertTrue(ordenes.eliminarFotoOrden("o-1", "f-o1-1", "u-jefe") is Resultado.Error)
    }

    // ---------------------------------------------------------------------------------------------
    // Técnicos
    // ---------------------------------------------------------------------------------------------

    @Test
    fun noSeDesactivaUnTecnicoConOrdenesEnCurso() = runBlocking {
        assertEquals(
            "Reasigna sus órdenes en curso antes de desactivarlo",
            mensajeDe(tecnicos.desactivarTecnico("u-tec-luis"))
        )
        assertTrue(tecnicos.reactivarTecnico("u-tec-pedro") is Resultado.Exito)
        assertTrue(bd.usuarios.value.first { it.id == "u-tec-pedro" }.activo)
    }

    @Test
    fun registrarTecnicoConDniRepetidoSeRechaza() = runBlocking {
        val resultado = tecnicos.registrarTecnico("Nuevo", "Tecnico", "45671234", "955555555", "nuevo.tec@freddy.pe", "Clave1234")
        assertEquals("Ya existe un usuario con ese DNI", mensajeDe(resultado))
    }

    @Test
    fun registrarTecnicoConCorreoRepetidoSeRechazaSinCulparAlDni() = runBlocking {
        val resultado = tecnicos.registrarTecnico("Nuevo", "Tecnico", "40999111", "955555555", "luis@freddy.pe", "Clave1234")
        assertEquals("Ya existe un usuario con ese correo", mensajeDe(resultado))
    }

    @Test
    fun registrarClienteDejaElNombreConFormatoDePropio() = runBlocking {
        val datos = DatosRegistroCliente("leopoldo", "CHOque floRES", "42289035", "918187516", "leo.choque@freddy.pe", "Clave1234")
        val cliente = (auth.registrarCliente(datos) as Resultado.Exito<*>).valor as com.leosle.pitlane.modelo.Usuario
        assertEquals("Leopoldo", cliente.nombres)
        assertEquals("Choque Flores", cliente.apellidos)
    }
}
