package com.eshok.emme

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.content.Context
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 16, 25)
    private val panel = Color.rgb(27, 27, 40)
    private val purple = Color.rgb(128, 100, 200)
    private val muted = Color.rgb(164, 162, 180)
    private lateinit var feed: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private val prefs by lazy { getSharedPreferences("emme_local", Context.MODE_PRIVATE) }
    private val history = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        restoreHistory()
        if (history.length() == 0) welcome() else renderHistory()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16)); setBackgroundColor(bg)
        }
        val mark = TextView(this).apply {
            text = "E"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; background = rounded(purple, 15); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titles.addView(TextView(this).apply { text = "EMME"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titles.addView(TextView(this).apply { text = "Asistente técnica · Computech"; textSize = 12f; setTextColor(muted) })
        header.addView(mark); header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply { text = "● LOCAL"; textSize = 10f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(117, 211, 160)) })
        root.addView(header)
        val line = View(this).apply { setBackgroundColor(Color.rgb(43, 42, 58)) }
        root.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        feed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(feed)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(14)); setBackgroundColor(panel) }
        input = EditText(this).apply {
            hint = "Escribe tu consulta técnica..."; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 15f
            setPadding(dp(15), dp(12), dp(15), dp(12)); background = rounded(Color.rgb(39, 38, 54), 22)
            imeOptions = EditorInfo.IME_ACTION_SEND; setSingleLine(true)
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(50), 1f))
        val send = TextView(this).apply {
            text = "➤"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(purple, 18); layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { leftMargin = dp(8) }
            setOnClickListener { send() }
        }
        composer.addView(send)
        root.addView(composer)
        setContentView(root)
    }

    private fun welcome() {
        val spacer = TextView(this).apply { text = "✦"; textSize = 42f; gravity = Gravity.CENTER; setTextColor(purple); setPadding(0, dp(45), 0, dp(4)) }
        feed.addView(spacer)
        feed.addView(TextView(this).apply { text = "¿Qué vamos a reparar hoy?"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
        feed.addView(TextView(this).apply { text = "Soy EMME, tu asistente técnica. Cuéntame qué equipo tienes y qué está fallando."; textSize = 14f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(dp(16), dp(10), dp(16), dp(24)) })
        val suggestions = listOf("Diagnosticar un Android", "Reparación de PC", "Recuperar YouTube en iPad", "Consultar un caso técnico")
        suggestions.forEach { s ->
            val chip = TextView(this).apply { text = s + "   ›"; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(16), dp(14), dp(16), dp(14)); background = rounded(panel, 14); setOnClickListener { input.setText(s); send() } }
            feed.addView(chip, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun send() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        input.setText("")
        addMessage(message, true)
        history.put(JSONObject().put("text", message).put("user", true))
        val answer = localReply(message)
        addMessage(answer, false)
        history.put(JSONObject().put("text", answer).put("user", false))
        saveHistory()
    }

    private fun localReply(q: String): String {
        val s = q.lowercase()
        return when {
            s.contains("ipad") && (s.contains("youtube") || s.contains("a1219")) -> "Tengo registrado un caso confirmado: iPad A1219 con iOS 5.1.1. TubeRepair 1.2-Beta-1 no cargaba porque sus archivos estaban en la raíz (/). Se movieron TubeRepair-Tweak.dylib y TubeRepair-Tweak.plist a /Library/MobileSubstrate/DynamicLibraries/, con permisos 755 y 644. Después de reiniciar, el usuario confirmó que YouTube volvió a funcionar.\n\nEs un antecedente de ese equipo, no una garantía para otros modelos. Esta versión de prueba usa respuestas locales; al conectar el motor de IA podré ampliar el diagnóstico paso a paso."
            s.contains("hola") || s.contains("buenas") -> "¡Qué tal! Soy EMME. Dime qué equipo estamos revisando, el modelo exacto y qué síntoma presenta. Vamos paso a paso, sin inventar diagnósticos."
            s.contains("diagnostic") || s.contains("fall") || s.contains("error") -> "Vamos a diagnosticarlo con orden. Necesito identificar el equipo y separar síntomas de posibles causas.\n\n1. Modelo exacto.\n2. Qué sucede y desde cuándo.\n3. Qué pruebas ya realizaste.\n\nPrimero priorizaremos pruebas reversibles y de bajo riesgo."
            s.contains("pc") || s.contains("computadora") || s.contains("laptop") -> "Para revisar esa computadora, dime marca y modelo, procesador si lo conoces, sistema operativo y síntoma exacto. No recomendaré formatear ni borrar datos sin revisar antes opciones menos riesgosas."
            else -> "Recibí tu consulta: \"$q\".\n\nEsta es la primera versión instalable de EMME y todavía trabaja con respuestas locales de prueba. Aún no está conectada a un modelo de IA ni puede consultar internet. Cuando integremos el motor, podré investigar y razonar sobre casos nuevos. Por ahora puedo recuperar el caso documentado del iPad A1219 y ayudarte a estructurar diagnósticos."
        }
    }

    private fun addMessage(text: String, user: Boolean) {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (user) Gravity.END else Gravity.START; setPadding(0, dp(5), 0, dp(5)) }
        val bubble = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(14), dp(12), dp(14), dp(12));
            background = rounded(if (user) purple else panel, 17)
        }
        wrap.addView(bubble, LinearLayout.LayoutParams(if (user) (resources.displayMetrics.widthPixels * .82f).toInt() else -1, -2))
        feed.addView(wrap)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun saveHistory() {
        prefs.edit().putString("history", history.toString()).apply()
    }

    private fun restoreHistory() {
        try {
            val stored = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until stored.length()) history.put(stored.getJSONObject(i))
        } catch (_: Exception) { }
    }

    private fun renderHistory() {
        for (i in 0 until history.length()) {
            val item = history.getJSONObject(i)
            addMessage(item.optString("text"), item.optBoolean("user"))
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
