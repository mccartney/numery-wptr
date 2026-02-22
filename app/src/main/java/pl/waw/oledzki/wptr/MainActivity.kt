package pl.waw.oledzki.wptr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

    private val targetIds = setOf("360", "365", "368")

    private val displayOrder = listOf("368", "360", "365")

    data class PlateEntry(
        val id: String,
        val districtName: String,
        val plateNumber: String,
        val date: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fetchPlates()
    }

    private fun fetchPlates() {
        val statusText = findViewById<TextView>(R.id.statusText)
        statusText.text = getString(R.string.loading)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    val html = fetchHtml()
                    parseEntries(html)
                }

                if (entries.isEmpty()) {
                    statusText.text = "Brak danych"
                } else {
                    statusText.visibility = View.GONE
                    displayPlates(entries)
                }
            } catch (e: Exception) {
                statusText.text = getString(R.string.error_prefix, e.message)
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
            if (id !in targetIds) continue

            val date = match.groupValues[2].trim()
            val districtName = match.groupValues[3].trim()
            val plateNumber = match.groupValues[4].trim()
                .replace("&nbsp;", " ")
                .replace("\u00A0", " ")

            entries.add(PlateEntry(id, districtName, plateNumber, date))
        }

        return entries.sortedBy { displayOrder.indexOf(it.id) }
    }

    private fun displayPlates(entries: List<PlateEntry>) {
        val container = findViewById<LinearLayout>(R.id.platesContainer)
        val inflater = LayoutInflater.from(this)

        for (entry in entries) {
            val view = inflater.inflate(R.layout.item_plate, container, false)
            view.findViewById<TextView>(R.id.districtName).text = entry.districtName
            view.findViewById<TextView>(R.id.plateNumber).text = entry.plateNumber
            view.findViewById<TextView>(R.id.dateText).text = entry.date
            container.addView(view)
        }
    }
}
