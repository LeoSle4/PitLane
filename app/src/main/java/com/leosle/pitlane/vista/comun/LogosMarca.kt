package com.leosle.pitlane.vista.comun

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.leosle.pitlane.R
import java.text.Normalizer

/**
 * Muestra el logo de la marca de un vehículo. Reconoce marcas conocidas sin importar mayúsculas, tildes, guiones ni
 * espacios, y si la marca no tiene logo dibuja un círculo con sus iniciales para que nunca quede un ícono genérico.
 */
object LogosMarca {

    private val logos: Map<String, Int> = mapOf(
        "acura" to R.drawable.marca_acura,
        "alfa romeo" to R.drawable.marca_alfa_romeo,
        "alfaromeo" to R.drawable.marca_alfa_romeo,
        "audi" to R.drawable.marca_audi,
        "baic" to R.drawable.marca_baic_motor,
        "baic motor" to R.drawable.marca_baic_motor,
        "baicmotor" to R.drawable.marca_baic_motor,
        "beijing" to R.drawable.marca_baic_motor,
        "bentley" to R.drawable.marca_bentley,
        "benz" to R.drawable.marca_mercedes_benz,
        "bmw" to R.drawable.marca_bmw,
        "buick" to R.drawable.marca_buick,
        "build your dreams" to R.drawable.marca_byd,
        "buildyourdreams" to R.drawable.marca_byd,
        "byd" to R.drawable.marca_byd,
        "cadillac" to R.drawable.marca_cadillac,
        "chang an" to R.drawable.marca_changan,
        "changan" to R.drawable.marca_changan,
        "chery" to R.drawable.marca_chery,
        "chery automobile" to R.drawable.marca_chery,
        "cheryautomobile" to R.drawable.marca_chery,
        "chevrolet" to R.drawable.marca_chevrolet,
        "chevy" to R.drawable.marca_chevrolet,
        "chrysler" to R.drawable.marca_chrysler,
        "citroen" to R.drawable.marca_citroen,
        "cupra" to R.drawable.marca_cupra,
        "dacia" to R.drawable.marca_dacia,
        "daewoo" to R.drawable.marca_daewoo,
        "daihatsu" to R.drawable.marca_daihatsu,
        "dfm" to R.drawable.marca_dongfeng,
        "dodge" to R.drawable.marca_dodge,
        "dong feng" to R.drawable.marca_dongfeng,
        "dongfeng" to R.drawable.marca_dongfeng,
        "faw" to R.drawable.marca_faw,
        "ferrari" to R.drawable.marca_ferrari,
        "fiat" to R.drawable.marca_fiat,
        "ford" to R.drawable.marca_ford,
        "foton" to R.drawable.marca_foton,
        "gac" to R.drawable.marca_gac_group,
        "gac group" to R.drawable.marca_gac_group,
        "gacgroup" to R.drawable.marca_gac_group,
        "geely" to R.drawable.marca_geely,
        "gmc" to R.drawable.marca_gmc,
        "great wall" to R.drawable.marca_great_wall,
        "great wall motors" to R.drawable.marca_great_wall,
        "greatwall" to R.drawable.marca_great_wall,
        "greatwallmotors" to R.drawable.marca_great_wall,
        "gwm" to R.drawable.marca_great_wall,
        "haima" to R.drawable.marca_haima,
        "haval" to R.drawable.marca_haval,
        "hino" to R.drawable.marca_hino,
        "honda" to R.drawable.marca_honda,
        "hongqi" to R.drawable.marca_hongqi,
        "hyundai" to R.drawable.marca_hyundai,
        "infiniti" to R.drawable.marca_infiniti,
        "isuzu" to R.drawable.marca_isuzu,
        "jac" to R.drawable.marca_jac,
        "jac motors" to R.drawable.marca_jac,
        "jacmotors" to R.drawable.marca_jac,
        "jaguar" to R.drawable.marca_jaguar,
        "jeep" to R.drawable.marca_jeep,
        "jetour" to R.drawable.marca_jetour,
        "jiangling" to R.drawable.marca_jmc,
        "jmc" to R.drawable.marca_jmc,
        "kgm" to R.drawable.marca_ssangyong,
        "kia" to R.drawable.marca_kia,
        "lada" to R.drawable.marca_lada,
        "lamborghini" to R.drawable.marca_lamborghini,
        "land rover" to R.drawable.marca_land_rover,
        "landrover" to R.drawable.marca_land_rover,
        "lexus" to R.drawable.marca_lexus,
        "lifan" to R.drawable.marca_lifan,
        "lincoln" to R.drawable.marca_lincoln,
        "lynk" to R.drawable.marca_lynk_and_co,
        "lynk co" to R.drawable.marca_lynk_and_co,
        "lynkco" to R.drawable.marca_lynk_and_co,
        "mahindra" to R.drawable.marca_mahindra,
        "maserati" to R.drawable.marca_maserati,
        "maxus" to R.drawable.marca_maxus,
        "mazda" to R.drawable.marca_mazda,
        "mb" to R.drawable.marca_mercedes_benz,
        "mclaren" to R.drawable.marca_mclaren,
        "mercedes" to R.drawable.marca_mercedes_benz,
        "mercedes benz" to R.drawable.marca_mercedes_benz,
        "mercedesbenz" to R.drawable.marca_mercedes_benz,
        "mg" to R.drawable.marca_mg,
        "mg motor" to R.drawable.marca_mg,
        "mgmotor" to R.drawable.marca_mg,
        "mini" to R.drawable.marca_mini,
        "mini cooper" to R.drawable.marca_mini,
        "minicooper" to R.drawable.marca_mini,
        "mitsubishi" to R.drawable.marca_mitsubishi,
        "morris garages" to R.drawable.marca_mg,
        "morrisgarages" to R.drawable.marca_mg,
        "nio" to R.drawable.marca_nio,
        "nissan" to R.drawable.marca_nissan,
        "omoda" to R.drawable.marca_omoda,
        "opel" to R.drawable.marca_opel,
        "peugeot" to R.drawable.marca_peugeot,
        "polestar" to R.drawable.marca_polestar,
        "porsche" to R.drawable.marca_porsche,
        "ram" to R.drawable.marca_ram,
        "range rover" to R.drawable.marca_land_rover,
        "rangerover" to R.drawable.marca_land_rover,
        "renault" to R.drawable.marca_renault,
        "rolls royce" to R.drawable.marca_rolls_royce,
        "rollsroyce" to R.drawable.marca_rolls_royce,
        "seat" to R.drawable.marca_seat,
        "skoda" to R.drawable.marca_skoda,
        "smart" to R.drawable.marca_smart,
        "ssang yong" to R.drawable.marca_ssangyong,
        "ssangyong" to R.drawable.marca_ssangyong,
        "subaru" to R.drawable.marca_subaru,
        "suzuki" to R.drawable.marca_suzuki,
        "tata" to R.drawable.marca_tata,
        "tesla" to R.drawable.marca_tesla,
        "toyota" to R.drawable.marca_toyota,
        "volkswagen" to R.drawable.marca_volkswagen,
        "volvo" to R.drawable.marca_volvo,
        "vw" to R.drawable.marca_volkswagen,
        "wuling" to R.drawable.marca_wuling,
        "xiaopeng" to R.drawable.marca_xpeng,
        "xpeng" to R.drawable.marca_xpeng,
        "zeekr" to R.drawable.marca_zeekr,
        "zotye" to R.drawable.marca_zotye,
    )

    /** Devuelve el recurso del logo de [marca], o `null` si no hay uno disponible. */
    @DrawableRes
    fun recurso(marca: String): Int? = logos[normalizar(marca)] ?: logos[normalizar(marca).replace(" ", "")]

    /** Pone en [imagen] el logo de [marca] a color o, si no existe, un círculo con las iniciales de la marca. */
    fun aplicar(imagen: ImageView, marca: String) {
        imagen.imageTintList = null
        val recurso = recurso(marca)
        if (recurso != null) {
            imagen.setImageResource(recurso)
        } else {
            imagen.setImageDrawable(MonogramaMarca(marca, ContextCompat.getColor(imagen.context, R.color.texto_secundario)))
        }
    }

    /** Pasa a minúsculas, quita tildes y signos y deja espacios simples para comparar marcas escritas de distinta forma. */
    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex(" +"), " ")
            .trim()

    /** Dibujo con las dos primeras iniciales de la marca centradas, para marcas sin logo. */
    private class MonogramaMarca(marca: String, private val color: Int) : Drawable() {
        private val iniciales = marca.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
        private val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        override fun draw(lienzo: Canvas) {
            val limites = bounds
            pincel.color = color
            pincel.textSize = limites.height() * 0.5f
            val y = limites.exactCenterY() - (pincel.descent() + pincel.ascent()) / 2f
            lienzo.drawText(iniciales, limites.exactCenterX(), y, pincel)
        }

        override fun setAlpha(alpha: Int) { pincel.alpha = alpha }
        override fun setColorFilter(filtro: ColorFilter?) { pincel.colorFilter = filtro }
        @Deprecated("Requerido por Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
