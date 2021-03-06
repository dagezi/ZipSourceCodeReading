package karino2.livejournal.com.zipsourcecodereading

import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import com.google.re2j.Pattern
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class SearchActivity : AppCompatActivity() {

    val sourceArchive : SourceArchive by lazy {
        SourceArchive(ZipFile(MainActivity.lastZipPath(this)))
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val lineNumberTV = (view.findViewById(R.id.lineNumber) as TextView)
        val filePathTV = (view.findViewById(R.id.filePath) as TextView)
        val matchLineTV = (view.findViewById(R.id.matchLine) as TextView)
    }

    // fun showMessage(msg : String) = MainActivity.showMessage(this, msg)

    inner class MatchEntryAdapter : ObserverAdapter<RegexpReader.MatchEntry, ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.lineNumberTV.text = (item.lineNumber!!).toString()
            // holder.lineNumberTV.text = "___"
            holder.filePathTV.text = item.fentry
            holder.matchLineTV.text = item.line

            with(holder.view) {
                tag = item
                setOnClickListener { openFile(holder.filePathTV.text.toString(),  holder.lineNumberTV.text.toString().toInt()) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(inflater.inflate(R.layout.search_result_item, parent, false))
        }

    }

    val searchAdapter = MatchEntryAdapter()

    var prevSearch : Disposable? = null

    fun showSearchingIndicator() {
        (findViewById(R.id.progressBar)).visibility = View.VISIBLE
    }

    fun hideSearchingIndicator() {
        (findViewById(R.id.progressBar)).visibility = View.GONE

    }

    fun startSearch() {
        prevSearch?.dispose()
        prevSearch = null


        searchAdapter.items.clear()
        searchAdapter.notifyDataSetChanged()


        val fpat = (findViewById(R.id.fileEntryField) as EditText).text.toString()
        val spat =  (findViewById(R.id.searchEntryField) as EditText).text.toString()

        val ffilter = fun(path : String) : Boolean {
            if(fpat == "")
                return true
            return path.contains(fpat)
        }


        val reader = RegexpReader(Pattern.compile(spat))

        val obs = sourceArchive.listFiles()
                .filter{  ffilter(it.name) }
                .flatMap{ reader.Read(sourceArchive.getInputStream(it), it.toString(), 0) }
                .subscribeOn(Schedulers.io())
                .buffer(1, TimeUnit.SECONDS, 5)

        showSearchingIndicator()

        prevSearch = obs.observeOn(AndroidSchedulers.mainThread())
                .doOnComplete {
                    prevSearch=null
                    hideSearchingIndicator()
                }
                .subscribe { matches ->
                    if (matches.size > 0)
                        searchAdapter.addAll(matches)
                }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val toolbar = (findViewById(R.id.toolbar) as Toolbar)
        setSupportActionBar(toolbar)

        assert(MainActivity.lastZipPath(this) != null)

        val recycle = (findViewById(R.id.searchResult) as RecyclerView)
        recycle.adapter = searchAdapter
        searchAdapter.datasetChangedNotifier()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { ada ->
                    // ada.notifyItemRangeInserted(0, ada.items.size)
                    ada.notifyDataSetChanged()
                }


        (findViewById(R.id.searchEntryField) as EditText).setOnEditorActionListener(fun(view, actionId, keyEvent)  : Boolean {
            if(actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideSoftkey()
                startSearch()
                return true;
            }
            return false;
        })

    }

    fun hideSoftkey() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }

    private fun  openFile(ent: String, lineNum: Int) {
        val intent = Intent(this, SourceViewActivity::class.java)
        intent.putExtra("ZIP_FILE_ENTRY", ent)
        intent.putExtra("LINE_NUM", lineNum)
        startActivity(intent)
    }

    fun showMessage(msg : String) = MainActivity.showMessage(this, msg)

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when(item?.itemId) {
            R.id.action_choose -> {
                val intent = Intent(this, ZipChooseActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
