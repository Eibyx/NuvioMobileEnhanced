package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.logging.SecretRedactor
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.TimeSource

/** Local observations only. No images, requests, retries or network upload are owned here. */
internal object ArtworkDiagnostics {
    const val MAX_BYTES = 1024 * 1024
    private val lock = SynchronizedObject()
    private val sequence = atomic(0L)
    private val _available = MutableStateFlow(false)
    val availability = _available.asStateFlow()
    val available: Boolean get() = _available.value
    private val _enabled = MutableStateFlow(false)
    val capturing = _enabled.asStateFlow()
    val enabled: Boolean get() = _enabled.value
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()
    private var origin = TimeSource.Monotonic.markNow()
    private val lines = ArrayDeque<String>()
    private var bytes = 0
    private var dropped = 0
    private var output: ((String) -> Unit)? = null
    // Bounded correlation/provenance labels ONLY, never artwork or request work.
    private val watched = linkedMapOf<String, Long>()
    private val selectedContent = mutableMapOf<String, String>()
    private var selectedHeroPage: Int? = null
    private val origins = linkedMapOf<String, String>()
    private val aliases = linkedMapOf<String, String>()
    private val owners = linkedMapOf<String, String>()
    private val identityPattern = Regex("(?:^| )identity=([0-9a-f]{16})")
    fun nextId(): Long = sequence.incrementAndGet()
    fun configure(available: Boolean) { _available.value = available; stop() }
    fun start() = synchronized(lock) {
        if (!available || enabled) return@synchronized
        if (lines.isEmpty()) origin = TimeSource.Monotonic.markNow()
        watched.clear(); selectedContent.clear(); selectedHeroPage = null; origins.clear(); aliases.clear(); owners.clear()
        _generation.value++
        _enabled.value = true
        append("capture.start", "")
    }
    fun stop() = synchronized(lock) {
        if (enabled) append("capture.stop", "")
        _enabled.value = false
        _generation.value++
    }
    fun clear() = synchronized(lock) {
        lines.clear(); bytes = 0; dropped = 0
        watched.clear(); selectedContent.clear(); selectedHeroPage = null; origins.clear(); aliases.clear(); owners.clear()
        origin = TimeSource.Monotonic.markNow()
        _generation.value++
    }
    // Retained for the existing diagnostic tests; production uses explicit start/stop instead.
    fun install(sink: ((String) -> Unit)?) = synchronized(lock) {
        output = sink; _available.value = true; _enabled.value = sink != null
        lines.clear(); bytes = 0; dropped = 0; watched.clear(); selectedContent.clear(); selectedHeroPage = null; origins.clear(); aliases.clear(); owners.clear()
        origin = TimeSource.Monotonic.markNow(); _generation.value++
    }
    fun token(value: Any?): String = value?.toString()?.encodeUtf8()?.sha256()?.hex()?.take(16) ?: "none"
    fun content(identity: String): String = synchronized(lock) { aliases[token(identity)] ?: token(identity) }
    fun watch(identity: String): String = synchronized(lock) {
        watchHash(content(identity))
    }
    fun watchHash(key: String): String = synchronized(lock) {
        if (enabled && key !in watched) { watched[key] = nextId(); trim(watched) }
        key
    }
    fun observe(identity: String, url: String?, source: String) {
        if (!enabled) return
        val key = watch(identity)
        synchronized(lock) {
            if (url != null) { owners[token(url)] = key; trim(owners) }
        }
    }
    fun owner(url: String): String? = synchronized(lock) { owners[token(url)] }
    fun alias(identity: String, original: String) = synchronized(lock) {
        val canonical = content(original)
        if (enabled && canonical in watched) { aliases[token(identity)] = canonical; trim(aliases) }
    }
    fun heroPage(page: Int) { if (enabled) synchronized(lock) { selectedHeroPage = page } }
    fun purpose(tag: ArtworkRequestTag?): String = synchronized(lock) {
        if (tag?.purpose?.startsWith("HERO_LAYER_") == true) {
            if (tag.purpose.substringAfterLast('_').toIntOrNull() == selectedHeroPage) "HERO_VISIBLE" else "HERO_NEIGHBOUR_LAYER"
        } else tag?.purpose ?: "UNKNOWN"
    }
    fun operation(identity: String): Long? = synchronized(lock) { watched[identity] }
    fun selected(screen: String, identity: String) {
        if (!enabled) return
        val key = watch(identity)
        val changed = synchronized(lock) {
            if (selectedContent[screen] == key && key in watched) false
            else { selectedContent[screen] = key; watched[key] = nextId(); true }
        }
        if (!changed) return
        event("$screen.selected") { "identity=$key" }
    }
    fun resolved(identity: String, url: String?, source: String) {
        if (!enabled || url == null) return
        val key = content(identity)
        synchronized(lock) {
            if (key in watched) { origins[token(url)] = source; trim(origins) }
        }
    }
    fun source(url: String?, fallback: String): String = synchronized(lock) { origins[token(url)] ?: fallback }
    fun epoch(): Long = _generation.value
    fun event(name: String, fields: () -> String = { "" }) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return@synchronized
            val text = fields()
            val identity = identityPattern.find(text)?.groupValues?.get(1)
            if ((name.startsWith("metadata.") || name.startsWith("tmdb.")) && identity !in watched) return@synchronized
            val op = identity?.let(watched::get)?.let { " op=$it" }.orEmpty()
            append(name, text + op)
        }
    }
    private fun append(name: String, fields: String) {
        val line = sanitize("t=${origin.elapsedNow().inWholeMilliseconds}ms event=$name $fields").take(4096)
        val size = line.encodeToByteArray().size + 1
        while (lines.isNotEmpty() && (bytes + size > MAX_BYTES || lines.size >= 8192)) {
            bytes -= lines.removeFirst().encodeToByteArray().size + 1; dropped++
        }
        lines.addLast(line); bytes += size
        output?.invoke(line)
    }
    private fun <T> trim(map: LinkedHashMap<String, T>) { while (map.size > 256) map.remove(map.keys.first()) }
    fun retainedBytes(): Int = synchronized(lock) { bytes }
    fun report(summary: Boolean = false): String {
        val snapshot = synchronized(lock) { Triple(lines.toList(), dropped, enabled) }
        val counts = listOf("MEMORY_CACHE", "MEMORY", "DISK", "NETWORK").joinToString { source ->
            "$source=${snapshot.first.count { Regex("source=$source(?: |$)").containsMatchIn(it) && "coil.success" in it }}"
        }
        return buildString {
            appendLine("NuvioEibyx — Diagnóstico de imágenes")
            appendLine("Captura: ${if (snapshot.third) "ACTIVA" else "DETENIDA"}; eventos=${snapshot.first.size}; descartados=${snapshot.second}")
            appendLine("Reloj monotónico; t=milisegundos desde inicio/limpieza. identity/op correlacionan contenido; request identifica una solicitud.")
            appendLine("Fuentes de éxitos retenidos: $counts")
            appendLine("MEMORY no es MEMORY_CACHE. NETWORK no implica descarga completa; declaredBytes no mide bytes en el cable.")
            appendLine("draw.submitted NO prueba presentación física, visibilidad, alpha ni fin de crossfade.")
            appendLine("Sesión sólo en memoria (máximo 1 MiB); activar no vuelve a solicitar imágenes. Reproducir después de activar; limpiar para comenzar un registro vacío.")
            appendLine()
            (if (summary) snapshot.first.takeLast(60) else snapshot.first).forEach { appendLine(it) }
        }
    }
    private val urls = Regex("""(?i)https?://[^\s<>"']+""")
    private val credentials = Regex("""(?i)((?:authorization|cookie|set-cookie|token|api[_-]?key|password|secret)\s*[:=]\s*)[^\r\n]+""")
    private fun sanitize(text: String): String = SecretRedactor.redact(
        credentials.replace(urls.replace(text) { "url#${token(it.value)}" }) { it.groupValues[1] + "[redacted]" },
    ).replace('\n', ' ').replace('\r', ' ')
}
