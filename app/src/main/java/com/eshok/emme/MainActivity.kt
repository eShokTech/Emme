package com.eshok.emme

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.content.Context
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val bg = Color.rgb(16,16,25)
    private val panel = Color.rgb(27,27,40)
    private val purple = Color.rgb(128,100,200)
    private val muted = Color.rgb(164,162,180)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("emme_local", Context.MODE_PRIVATE) }
    private val history = JSONArray()
    private val modelName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
    private val modelUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6bb3e4a1eb3f663153f0c1c12b735d64be657a7/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
    private val minBytes = 300L * 1024L * 1024L
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var ready = false
    private var busy = false
    private val response = StringBuilder()
    private var responseView: TextView? = null
    private lateinit var feed: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var download: TextView
    private lateinit var sendButton: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        restoreHistory()
        buildUi()
        renderHistory()
        val file = File(filesDir, modelName)
        if (file.exists() && file.length() >= minBytes) {
            download.visibility = View.GONE
            status.text = "Modelo encontrado. Preparando EMME…"
            initializeModel(file)
        } else {
            status.text = "La IA aún no está instalada en este teléfono."
            download.visibility = View.VISIBLE
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(15),dp(18),dp(12)) }
        val logo = TextView(this).apply { text = "E"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rounded(purple,15) }
        header.addView(logo,LinearLayout.LayoutParams(dp(46),dp(46)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        titles.addView(TextView(this).apply { text = "EMME"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        titles.addView(TextView(this).apply { text = "Asistente técnica · Computech"; textSize = 12f; setTextColor(muted) })
        header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(TextView(this).apply { text = "● LOCAL"; textSize = 10f; setTextColor(Color.rgb(117,211,160)) })
        root.addView(header)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(43,42,58)) },LinearLayout.LayoutParams(-1,dp(1)))
        val modelPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); setBackgroundColor(panel) }
        status = TextView(this).apply { textSize = 12f; setTextColor(muted) }
        modelPanel.addView(status)
        progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        modelPanel.addView(progress,LinearLayout.LayoutParams(-1,dp(5)).apply { topMargin = dp(7) })
        download = TextView(this).apply {
            text = "DESCARGAR IA · 329 MB"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(purple,12)
            setPadding(dp(14),dp(12),dp(14),dp(12)); visibility = View.GONE
            setOnClickListener { downloadModel() }
        }
        modelPanel.addView(download,LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(8) })
        root.addView(modelPanel)
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        feed = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(feed)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(14)); setBackgroundColor(panel) }
        input = EditText(this).apply {
            hint = "Escribe tu consulta técnica…"; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 15f
            setPadding(dp(15),dp(12),dp(15),dp(12)); background = rounded(Color.rgb(39,38,54),22)
            imeOptions = EditorInfo.IME_ACTION_SEND; setSingleLine(true); isEnabled = false
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        }
        composer.addView(input,LinearLayout.LayoutParams(0,dp(50),1f))
        sendButton = TextView(this).apply {
            text = "➤"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(76,73,91),18); layoutParams = LinearLayout.LayoutParams(dp(50),dp(50)).apply { leftMargin = dp(8) }
            setOnClickListener { send() }
        }
        composer.addView(sendButton)
        root.addView(composer)
        setContentView(root)
    }

    private fun downloadModel() {
        download.isEnabled = false
        download.text = "DESCARGANDO…"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        status.text = "Descargando modelo de IA (aprox. 329 MB). Mantén EMME abierta."
        downloadScope.launch {
            val target = File(filesDir,modelName)
            val temp = File(filesDir,modelName + ".part")
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(modelUrl).openConnection() as HttpURLConnection)
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent","EMME-Android/0.2.0")
                connection.connect()
                if (connection.responseCode !in 200..299) throw Exception("HTTP " + connection.responseCode)
                val total = connection.contentLengthLong
                if (total > 0L) handler.post { progress.isIndeterminate = false }
                var received = 0L
                connection.inputStream.use { stream ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(262144)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n < 0) break
                            output.write(buffer,0,n)
                            received += n
                            if (total > 0L) {
                                val pct = ((received * 100L) / total).toInt().coerceIn(0,100)
                                handler.post {
                                    progress.progress = pct
                                    status.text = "Descargando modelo: " + pct + "% · " + (received / 1048576L) + " MB"
                                }
                            }
                        }
                    }
                }
                if (temp.length() < minBytes) throw Exception("Descarga incompleta: " + (temp.length()/1048576L) + " MB")
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) { temp.copyTo(target,true); temp.delete() }
                handler.post {
                    download.visibility = View.GONE
                    status.text = "Modelo descargado. Iniciando motor de IA…"
                    initializeModel(target)
                }
            } catch (e: Exception) {
                temp.delete()
                handler.post {
                    status.text = "No se pudo descargar: " + (e.message ?: "error de red") + ". Revisa internet e inténtalo otra vez."
                    download.isEnabled = true
                    download.text = "REINTENTAR DESCARGA"
                    progress.visibility = View.GONE
                }
            } finally { connection?.disconnect() }
        }
    }

    private fun initializeModel(file: File) {
        status.text = "Cargando Qwen3 en el teléfono. La primera carga puede tardar…"
        input.isEnabled = false
        sendButton.isEnabled = false
        engineScope.launch {
            try {
                val created = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), cacheDir = cacheDir.absolutePath))
                created.initialize()
                engine = created
                val seed = buildSeedMessages()
                conversation = created.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "Eres EMME, asistente técnica de Computech. Hablas español mexicano natural, eres clara, práctica y cuidadosa. " +
                            "Ayudas con reparación de celulares, tablets, computadoras, Android, iOS, electrónica y software. " +
                            "No inventes compatibilidades, procedimientos ni resultados. Distingue hechos confirmados, hipótesis y pruebas. " +
                            "Prioriza diagnósticos reversibles y de bajo riesgo. Advierte antes de flashear, borrar datos, modificar particiones " +
                            "o hacer acciones eléctricas peligrosas. Si faltan datos, pregunta por modelo exacto, síntomas y pruebas realizadas."
                        ),
                        initialMessages = seed,
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.85, temperature = 0.65)
                    )
                )
                ready = true
                handler.post {
                    progress.visibility = View.GONE
                    status.text = "IA lista · Qwen3 0.6B ejecutándose localmente. Ya puedes conversar sin conexión."
                    input.isEnabled = true
                    sendButton.isEnabled = true
                    sendButton.background = rounded(purple,18)
                }
            } catch (e: Exception) {
                ready = false
                handler.post {
                    status.text = "El modelo está descargado, pero no pudo iniciar: " + (e.message ?: "error del motor")
                    download.visibility = View.VISIBLE
                    download.isEnabled = true
                    download.text = "VOLVER A INTENTAR"
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun buildSeedMessages(): List<Message> {
        val messages = mutableListOf<Message>()
        val start = (history.length()-12).coerceAtLeast(0)
        for (i in start until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            val text = item.optString("text")
            if (text.isNotBlank()) messages.add(if (item.optBoolean("user")) Message.user(text) else Message.model(text))
        }
        if (messages.isEmpty()) {
            messages.add(Message.user("Preséntate brevemente en español como EMME, asistente técnica de Computech."))
            messages.add(Message.model("Soy EMME, tu asistente técnica. Cuéntame qué equipo vamos a revisar y qué problema presenta."))
        }
        return messages
    }

    private fun send() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty() || !ready || busy) return
        input.setText("")
        addMessage(prompt,true)
        history.put(JSONObject().put("text",prompt).put("user",true))
        saveHistory()
        busy = true
        input.isEnabled = false
        sendButton.isEnabled = false
        response.setLength(0)
        responseView = addMessage("",false)
        engineScope.launch {
            try {
                val active = conversation ?: throw Exception("Conversación no inicializada")
                active.sendMessageAsync(prompt).collect { chunk ->
                    response.append(chunk.toString())
                    val snapshot = response.toString()
                    handler.post {
                        responseView?.text = snapshot
                        scroll.fullScroll(View.FOCUS_DOWN)
                    }
                }
                val answer = response.toString().trim()
                history.put(JSONObject().put("text",answer).put("user",false))
                saveHistory()
                handler.post {
                    if (answer.isBlank()) responseView?.text = "El modelo terminó sin devolver texto. Intenta reformular."
                    busy = false
                    input.isEnabled = true
                    sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                handler.post {
                    responseView?.text = "No pude generar la respuesta: " + (e.message ?: "error del modelo")
                    busy = false
                    input.isEnabled = true
                    sendButton.isEnabled = true
                }
            }
        }
    }

    private fun addMessage(text: String,user: Boolean): TextView {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (user) Gravity.END else Gravity.START; setPadding(0,dp(5),0,dp(5)) }
        val bubble = TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(14),dp(12),dp(14),dp(12))
            background = rounded(if (user) purple else panel,17)
        }
        wrap.addView(bubble,LinearLayout.LayoutParams(if (user) (resources.displayMetrics.widthPixels * .82f).toInt() else -1,-2))
        feed.addView(wrap)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return bubble
    }

    private fun renderHistory() {
        if (history.length() == 0) {
            feed.addView(TextView(this).apply { text = "✦"; textSize = 42f; gravity = Gravity.CENTER; setTextColor(purple); setPadding(0,dp(35),0,dp(4)) })
            feed.addView(TextView(this).apply { text = "Tu taller, con una IA en el bolsillo."; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(12),dp(8),dp(12),dp(8)) })
            feed.addView(TextView(this).apply { text = "Descarga el modelo una vez. Después, EMME podrá conversar sin conexión."; textSize = 13f; gravity = Gravity.CENTER; setTextColor(muted); setPadding(dp(18),dp(4),dp(18),dp(18)) })
        } else {
            val start = (history.length()-40).coerceAtLeast(0)
            for (i in start until history.length()) {
                val item = history.optJSONObject(i) ?: continue
                addMessage(item.optString("text"),item.optBoolean("user"))
            }
        }
    }

    private fun restoreHistory() {
        try {
            val stored = JSONArray(prefs.getString("history","[]") ?: "[]")
            for (i in 0 until stored.length()) history.put(stored.getJSONObject(i))
        } catch (_: Exception) { }
    }

    private fun saveHistory() { prefs.edit().putString("history",history.toString()).apply() }
    private fun rounded(color: Int,radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        super.onDestroy()
        engineScope.launch {
            try { conversation?.close() } catch (_: Exception) { }
            conversation = null
            try { engine?.close() } catch (_: Exception) { }
            engine = null
            dispatcher.close()
            executor.shutdown()
        }
        downloadScope.cancel()
    }
}
