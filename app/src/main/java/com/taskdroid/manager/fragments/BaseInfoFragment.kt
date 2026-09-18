package com.taskdroid.manager.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.taskdroid.manager.databinding.FragmentScrollBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

abstract class BaseInfoFragment : Fragment() {
    protected lateinit var content: LinearLayout
    open val intervalMs = 1000L

    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    abstract fun populate()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentScrollBinding.inflate(inflater, container, false)
        content = binding.content
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        job = scope.launch {
            while (isActive) {
                populate()
                delay(intervalMs)
            }
        }
    }

    override fun onPause() {
        job?.cancel()
        super.onPause()
    }
}