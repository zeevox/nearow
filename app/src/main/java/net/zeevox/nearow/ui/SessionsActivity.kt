package net.zeevox.nearow.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.google.android.material.appbar.MaterialToolbar
import net.zeevox.nearow.R
import net.zeevox.nearow.databinding.ActivitySessionsBinding
import net.zeevox.nearow.ui.fragment.SessionsFragment


class SessionsActivity : AppCompatActivity() {

    /** for accessing UI elements, bind this activity to the XML */
    private lateinit var binding: ActivitySessionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Nearow)

        super.onCreate(savedInstanceState)

        binding = ActivitySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                // Android Dev best practices say to always setReorderingAllowed to true
                // https://developer.android.com/guide/fragments/create
                setReorderingAllowed(true)
                add<SessionsFragment>(R.id.fragment_container_view)
            }
        }

        val toolbar: MaterialToolbar = findViewById(R.id.sessions_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }
}
