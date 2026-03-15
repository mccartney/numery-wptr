package pl.waw.oledzki.wptr

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val favoriteIds = setOf("360", "365", "368")
    private val favoriteOrder = listOf("368", "360", "365")

    private var cachedEntries: List<PlateEntry> = emptyList()

    data class PlateEntry(
        val id: String,
        val districtName: String,
        val plateNumber: String,
        val date: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<SwipeRefreshLayout>(R.id.swipeRefresh).setOnRefreshListener {
            fetchPlates()
        }

        findViewById<EditText>(R.id.searchField).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                displayFiltered(s?.toString().orEmpty())
            }
        })

        fetchPlates()
    }

    private fun fetchPlates() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    val html = fetchHtml()
                    parseEntries(html)
                }

                cachedEntries = entries

                if (entries.isEmpty()) {
                    statusText.text = "Brak danych"
                } else {
                    statusText.visibility = View.GONE
                    val query = findViewById<EditText>(R.id.searchField).text.toString()
                    displayFiltered(query)
                }
            } catch (e: Exception) {
                statusText.text = getString(R.string.error_prefix, e.message)
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun fetchHtml(): String {
        val url = "https://wptr.pl/index.php?dz=praktyka&pdz=numery&sekcja=nn&ps=W"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val bytes = response.body?.bytes() ?: return ""
        return String(bytes, Charset.forName("ISO-8859-2"))
    }

    private fun parseEntries(html: String): List<PlateEntry> {
        val entries = mutableListOf<PlateEntry>()

        val entryPattern = Regex(
            """<a\s+name="(\d+)">\s*</a>\s*""" +
            """<div\s+class="BoxLt">\s*""" +
            """<div[^>]*>\s*([\d.]+)\s*</div>\s*""" +
            """<div[^>]*>\s*(.*?)\s*</div>\s*""" +
            """.*?""" +
            """<div\s+class="TbP"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL
        )

        for (match in entryPattern.findAll(html)) {
            val id = match.groupValues[1]
            val date = match.groupValues[2].trim()
            val districtName = match.groupValues[3].trim()
            val plateNumber = match.groupValues[4].trim()
                .replace("&nbsp;", " ")
                .replace("\u00A0", " ")

            entries.add(PlateEntry(id, districtName, plateNumber, date))
        }

        return entries
    }

    private fun displayFiltered(query: String) {
        if (cachedEntries.isEmpty()) return

        val statusText = findViewById<TextView>(R.id.statusText)
        val trimmed = query.trim()

        val entries = if (trimmed.isEmpty()) {
            cachedEntries
                .filter { it.id in favoriteIds }
                .sortedBy { favoriteOrder.indexOf(it.id) }
        } else {
            filterEntries(trimmed)
        }

        if (entries.isEmpty()) {
            statusText.text = getString(R.string.no_results)
            statusText.visibility = View.VISIBLE
        } else {
            statusText.visibility = View.GONE
        }

        displayPlates(entries)
    }

    private fun filterEntries(query: String): List<PlateEntry> {
        val q = stripDiacritics(query.lowercase())
        return cachedEntries.filter { entry ->
            val nameNorm = stripDiacritics(entry.districtName.lowercase())
            val plateNorm = entry.plateNumber.replace(" ", "").lowercase()
            nameNorm.contains(q) || plateNorm.startsWith(q)
        }
    }

    private fun stripDiacritics(s: String): String = s
        .replace('ą', 'a')
        .replace('ć', 'c')
        .replace('ę', 'e')
        .replace('ł', 'l')
        .replace('ń', 'n')
        .replace('ó', 'o')
        .replace('ś', 's')
        .replace('ź', 'z')
        .replace('ż', 'z')

    private fun displayPlates(entries: List<PlateEntry>) {
        val container = findViewById<LinearLayout>(R.id.platesContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (entry in entries) {
            val view = inflater.inflate(R.layout.item_plate, container, false)
            view.findViewById<TextView>(R.id.districtName).text = entry.districtName
            view.findViewById<PlateTextView>(R.id.plateNumber).setPlateText(entry.plateNumber)
            view.findViewById<TextView>(R.id.dateText).text = entry.date
            container.addView(view)
        }
    }
}
