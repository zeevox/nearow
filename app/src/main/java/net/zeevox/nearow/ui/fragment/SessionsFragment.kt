package net.zeevox.nearow.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zeevox.nearow.R
import net.zeevox.nearow.data.DataProcessor
import net.zeevox.nearow.db.TrackDatabase
import net.zeevox.nearow.db.model.Session
import net.zeevox.nearow.db.model.TrackDao
import net.zeevox.nearow.output.FitFileExporter
import net.zeevox.nearow.ui.recyclerview.SessionsListAdapter
import java.io.File

/**
 * A fragment representing a list of Items.
 */
class SessionsFragment : Fragment() {

    /**
     * The application database
     */
    private lateinit var db: TrackDatabase

    /**
     * Interface with the table where individual rate-location records are stored
     */
    private lateinit var track: TrackDao

    /**
     * Coroutine scope for database queries and other long-running operations
     */
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            requireContext(),
            TrackDatabase::class.java, DataProcessor.DATABASE_NAME
        ).build()

        track = db.trackDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sessions_list, container, false)

        // Set the adapter
        if (view is RecyclerView) with(view) {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
            val sessionsAdapter = SessionsListAdapter(::onSessionShareButtonClicked)
            adapter = sessionsAdapter
            scope.launch {
                sessionsAdapter.submitList(track.getSessions())
            }
        }
        return view
    }

    private fun onSessionShareButtonClicked(session: Session) {
        CoroutineScope(Dispatchers.Main).launch {
            // display a snackbar while we export the activity
            // this is best practice as opposed to showing a dialog
            val snackbar = Snackbar.make(
                requireView(),
                getString(R.string.info_loading_session_export),
                Snackbar.LENGTH_INDEFINITE
            ).apply { show() }

            // actually export the activity to a file
            val file: File? = withContext(Dispatchers.Default) {
                val sessionData = track.loadSession(session.trackId)
                if (sessionData.size <= 2) null
                else FitFileExporter(requireContext())
                    .exportTrackPoints(sessionData)
            }

            // cancel the snackbar once we have finished exporting
            snackbar.dismiss()

            if (file == null) Snackbar.make(requireView(),
                getString(R.string.info_track_too_short),
                Snackbar.LENGTH_LONG).show()
            else onTrackExported(file)
        }
    }

    /**
     * Called once a session has been finished and successfully
     * exported to a file.
     * https://developer.android.com/training/secure-file-sharing/share-file
     */
    private fun onTrackExported(exportedFile: File) {
        val fileUri: Uri = try {
            FileProvider.getUriForFile(
                requireContext(),
                "net.zeevox.nearow.fileprovider",
                exportedFile
            )
        } catch (e: IllegalArgumentException) {
            Log.e(
                "File Selector",
                "The selected file can't be shared: $exportedFile"
            )
            null
        } ?: return

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_TITLE, "Exported fit file")
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Share the exported FIT file with another application"
                    )
                    setDataAndType(fileUri, "application/vnd.ant.fit")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }, null
            )
        )
    }
}