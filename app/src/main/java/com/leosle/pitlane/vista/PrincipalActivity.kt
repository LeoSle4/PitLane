package com.leosle.pitlane.vista

import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.leosle.pitlane.R
import com.leosle.pitlane.vista.ordenes.FragComprobante
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.leosle.pitlane.databinding.ActivityPrincipalBinding
import com.leosle.pitlane.modelo.Notificacion
import com.leosle.pitlane.modelo.Rol
import com.leosle.pitlane.util.Fechas
import com.leosle.pitlane.viewmodel.AuthViewModel
import com.leosle.pitlane.viewmodel.NotificacionViewModel
import com.leosle.pitlane.vista.acceso.AccesoActivity
import com.leosle.pitlane.vista.comun.FragmentoBase
import com.leosle.pitlane.vista.comun.ItemPestana
import com.leosle.pitlane.vista.comun.Navegador
import com.leosle.pitlane.vista.notificaciones.MensajesPushService
import com.leosle.pitlane.vista.navegacion.RutasCliente
import com.leosle.pitlane.vista.navegacion.RutasJefe
import com.leosle.pitlane.vista.navegacion.RutasTecnico
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Actividad principal con sesión abierta: muestra la barra de pestañas del rol, apila las pantallas de detalle
 * y presenta los avisos emergentes de notificaciones nuevas.
 */
class PrincipalActivity : AppCompatActivity(), Navegador {

    private lateinit var vinculo: ActivityPrincipalBinding
    private val authViewModel: AuthViewModel by viewModels()
    private val notificacionViewModel: NotificacionViewModel by viewModels()

    private var pestanas: List<ItemPestana> = emptyList()
    private var rol: Rol = Rol.CLIENTE
    private var insetSuperior = 0
    private var insetInferior = 0
    private var altoTeclado = 0
    private var notificacionMostrada: Notificacion? = null

    private val solicitudPermisoAvisos =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val usuario = authViewModel.usuarioActual.value
        if (usuario == null) {
            abrirAcceso()
            return
        }
        rol = usuario.rol
        vinculo = ActivityPrincipalBinding.inflate(layoutInflater)
        setContentView(vinculo.root)

        pestanas = pestanasDelRol(rol)
        vinculo.barraNavegacion.configurar(pestanas) { cambiarPestana(it) }
        vinculo.btnCampana.setOnClickListener {
            pestanas.indexOfFirst { it.titulo == "Avisos" }.takeIf { it >= 0 }?.let { cambiarPestana(it) }
        }
        configurarMargenesDelSistema()
        configurarVisibilidadDeLaBarra()
        configurarBotonAtras()

        val indiceGuardado = savedInstanceState?.getInt(CLAVE_PESTANA, 0) ?: 0
        if (savedInstanceState == null) cambiarPestana(0) else vinculo.barraNavegacion.marcar(indiceGuardado)

        observarSesion()
        observarAvisos(usuario.id)
        prepararAvisosDelSistema(usuario.id)
        if (savedInstanceState == null) abrirOrdenDeLaNotificacion(intent)
    }

    override fun onNewIntent(intentNuevo: Intent) {
        super.onNewIntent(intentNuevo)
        setIntent(intentNuevo)
        if (::vinculo.isInitialized) abrirOrdenDeLaNotificacion(intentNuevo)
    }

    override fun onStart() {
        super.onStart()
        MensajesPushService.enPrimerPlano = true
    }

    override fun onStop() {
        MensajesPushService.enPrimerPlano = false
        super.onStop()
    }

    override fun onSaveInstanceState(salida: Bundle) {
        super.onSaveInstanceState(salida)
        if (::vinculo.isInitialized) salida.putInt(CLAVE_PESTANA, vinculo.barraNavegacion.indiceSeleccionado)
    }

    // ---------------------------------------------------------------------------------------------
    // Navegador
    // ---------------------------------------------------------------------------------------------

    override fun irA(fragmento: Fragment, apilar: Boolean) {
        val transaccion = supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.pantalla_entrar, R.anim.pantalla_salir, R.anim.pantalla_volver_entrar, R.anim.pantalla_volver_salir)
            .replace(vinculo.flContenido.id, fragmento)
        if (apilar) transaccion.addToBackStack(null)
        transaccion.commit()
    }

    override fun volver() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun cambiarPestana(indice: Int) {
        if (indice !in pestanas.indices) return
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.pestana_entrar, R.anim.pestana_salir)
            .replace(vinculo.flContenido.id, pestanas[indice].crearFragmento())
            .commit()
        vinculo.barraNavegacion.marcar(indice)
    }

    /** Muestra la campana con el contador de avisos sin leer cuando el cliente está en una pantalla sin barra inferior. */
    private fun pintarCampana() {
        val insignia = vinculo.txtInsigniaCampana
        insignia.visibility = if (avisosSinLeer > 0) View.VISIBLE else View.GONE
        insignia.text = if (avisosSinLeer > 9) "9+" else avisosSinLeer.toString()
        val campana = vinculo.flCampana
        val mostrar = !barraVisible && authViewModel.usuarioActual.value?.rol == Rol.CLIENTE
        campana.animate().cancel()
        if (mostrar) {
            campana.visibility = View.VISIBLE
            campana.animate().alpha(1f).setDuration(200).start()
        } else {
            campana.animate().alpha(0f).setDuration(150).withEndAction { if (barraVisible) campana.visibility = View.GONE }.start()
        }
    }

    /** Muestra u oculta la barra inferior deslizándola y atenuándola en vez de cambiar su visibilidad de golpe. */
    private fun animarBarra(mostrar: Boolean) {
        barraVisible = mostrar
        pintarCampana()
        val barra = vinculo.barraNavegacion
        if (mostrar == (barra.visibility == View.VISIBLE && barra.tag != OCULTANDO)) return
        barra.animate().cancel()
        if (mostrar) {
            barra.tag = null
            if (barra.visibility != View.VISIBLE) {
                barra.alpha = 0f
                barra.translationY = barra.height.toFloat().coerceAtLeast(40f)
                barra.visibility = View.VISIBLE
            }
            barra.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        } else {
            barra.tag = OCULTANDO
            barra.animate().alpha(0f).translationY(barra.height.toFloat().coerceAtLeast(40f)).setDuration(180)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { if (barra.tag == OCULTANDO) barra.visibility = View.GONE }
                .start()
        }
    }

    override fun cerrarSesion() {
        authViewModel.cerrarSesion()
    }

    override fun abrirInicio() {
        cambiarPestana(0)
    }

    override fun abrirOrden(ordenId: String) {
        when (rol) {
            Rol.CLIENTE -> RutasCliente.abrirOrden(this, ordenId)
            Rol.JEFE -> RutasJefe.abrirOrden(this, ordenId)
            Rol.TECNICO -> RutasTecnico.abrirOrden(this, ordenId)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Configuración interna
    // ---------------------------------------------------------------------------------------------

    /** Devuelve las pestañas que corresponden al rol del usuario. */
    private fun pestanasDelRol(rol: Rol): List<ItemPestana> = when (rol) {
        Rol.CLIENTE -> RutasCliente.pestanas()
        Rol.JEFE -> RutasJefe.pestanas()
        Rol.TECNICO -> RutasTecnico.pestanas()
    }

    /** Ajusta los espacios del contenido y de la barra a las barras del sistema y al teclado. */
    private fun configurarMargenesDelSistema() {
        ViewCompat.setOnApplyWindowInsetsListener(vinculo.flRaiz) { _, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insetSuperior = barras.top
            insetInferior = barras.bottom
            altoTeclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            aplicarMargenes()
            insets
        }
    }

    /** Aplica los espacios calculados al contenido, a la barra inferior y al aviso emergente. */
    private fun aplicarMargenes() {
        val inferior = maxOf(insetInferior, altoTeclado)
        vinculo.flContenido.setPadding(0, insetSuperior, 0, inferior)
        (vinculo.barraNavegacion.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            bottomMargin = insetInferior + (12 * resources.displayMetrics.density).toInt()
        }
        (vinculo.avisoPush.root.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            topMargin = insetSuperior + (12 * resources.displayMetrics.density).toInt()
        }
        (vinculo.flCampana.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            topMargin = insetSuperior + (12 * resources.displayMetrics.density).toInt()
        }
        vinculo.barraNavegacion.requestLayout()
        vinculo.flCampana.requestLayout()
        vinculo.avisoPush.root.requestLayout()
    }

    /** Muestra la barra inferior solo cuando la pantalla visible lo pide y el teclado está cerrado. */
    private fun configurarVisibilidadDeLaBarra() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(administrador: FragmentManager, fragmento: Fragment) {
                if (fragmento is DialogFragment) return
                val mostrar = (fragmento as? FragmentoBase)?.mostrarBarraInferior ?: false
                animarBarra(mostrar)
            }
        }, false)
    }

    /** Con la pila vacía, atrás en una pestaña distinta de la primera vuelve a la primera; en la primera sale. */
    private fun configurarBotonAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                    vinculo.barraNavegacion.indiceSeleccionado > 0 -> cambiarPestana(0)
                    else -> finish()
                }
            }
        })
    }

    /** Vuelve a la pantalla de acceso cuando la sesión se cierra. */
    private fun observarSesion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.usuarioActual.collect { usuario -> if (usuario == null) abrirAcceso() }
            }
        }
    }

    /** Actualiza la insignia de avisos sin leer y presenta cada notificación nueva como aviso emergente. */
    private fun observarAvisos(usuarioId: String) {
        val indiceAvisos = pestanas.indexOfFirst { it.titulo == "Avisos" }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    notificacionViewModel.noLeidas(usuarioId).collect { cantidad ->
                        if (indiceAvisos >= 0) vinculo.barraNavegacion.establecerInsignia(indiceAvisos, cantidad)
                        avisosSinLeer = cantidad
                        pintarCampana()
                    }
                }
                launch {
                    notificacionViewModel.nuevas(usuarioId).collect { mostrarAviso(it) }
                }
            }
        }
    }

    /** Muestra la tarjeta emergente de la notificación durante unos segundos; al tocarla abre la orden. */
    private fun mostrarAviso(notificacion: Notificacion) {
        notificacionMostrada = notificacion
        val tarjeta = vinculo.avisoPush
        tarjeta.txtAvisoTitulo.text = notificacion.titulo
        tarjeta.txtAvisoMensaje.text = notificacion.mensaje
        tarjeta.txtAvisoHora.text = Fechas.hora(notificacion.fechaHora)
        tarjeta.root.alpha = 0f
        tarjeta.root.translationY = -tarjeta.root.height.toFloat().coerceAtLeast(120f)
        tarjeta.root.visibility = View.VISIBLE
        tarjeta.root.animate().alpha(1f).translationY(0f).setDuration(300).start()
        tarjeta.root.setOnClickListener {
            ocultarAviso()
            notificacion.ordenId?.let { id ->
                notificacionViewModel.marcarLeida(notificacion.id)
                if (notificacion.esIngreso) irA(FragComprobante.nuevo(id)) else abrirOrden(id)
            }
        }
        tarjeta.root.postDelayed({
            if (notificacionMostrada?.id == notificacion.id) ocultarAviso()
        }, 4_500)
    }

    /** Oculta la tarjeta emergente con una animación corta. */
    private fun ocultarAviso() {
        notificacionMostrada = null
        val tarjeta = vinculo.avisoPush.root
        tarjeta.animate().alpha(0f).translationY(-tarjeta.height.toFloat()).setDuration(250)
            .withEndAction { tarjeta.visibility = View.GONE }.start()
    }

    /** Guarda el token del dispositivo del usuario y pide el permiso para mostrar notificaciones del sistema. */
    private fun prepararAvisosDelSistema(usuarioId: String) {
        notificacionViewModel.guardarTokenDispositivo(usuarioId, MensajesPushService.tokenDelDispositivo(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !MensajesPushService.puedeNotificar(this)) {
            solicitudPermisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Si la intención viene de una notificación del sistema, la marca como leída y abre su orden. */
    private fun abrirOrdenDeLaNotificacion(origen: Intent) {
        val ordenId = origen.getStringExtra(MensajesPushService.EXTRA_ORDEN_ID) ?: return
        origen.getStringExtra(MensajesPushService.EXTRA_NOTIFICACION_ID)?.let { notificacionViewModel.marcarLeida(it) }
        val esIngreso = origen.getBooleanExtra(MensajesPushService.EXTRA_ES_INGRESO, false)
        origen.removeExtra(MensajesPushService.EXTRA_ORDEN_ID)
        origen.removeExtra(MensajesPushService.EXTRA_ES_INGRESO)
        if (esIngreso && authViewModel.usuarioActual.value?.rol == Rol.CLIENTE) irA(FragComprobante.nuevo(ordenId)) else abrirOrden(ordenId)
    }

    /** Abre la pantalla de acceso y cierra esta actividad; conserva la orden pendiente de abrir si la hay. */
    private fun abrirAcceso() {
        val acceso = Intent(this, AccesoActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent?.getStringExtra(MensajesPushService.EXTRA_ORDEN_ID)?.let { acceso.putExtra(MensajesPushService.EXTRA_ORDEN_ID, it) }
        intent?.getStringExtra(MensajesPushService.EXTRA_NOTIFICACION_ID)?.let { acceso.putExtra(MensajesPushService.EXTRA_NOTIFICACION_ID, it) }
        acceso.putExtra(MensajesPushService.EXTRA_ES_INGRESO, intent?.getBooleanExtra(MensajesPushService.EXTRA_ES_INGRESO, false) == true)
        startActivity(acceso)
        finish()
    }

    private var avisosSinLeer = 0
    private var barraVisible = true

    private companion object {
        const val CLAVE_PESTANA = "pestana_seleccionada"
        const val OCULTANDO = "ocultando"
    }
}
