package com.taskdroid.manager.fragments

import android.app.ActivityManager
import android.os.SystemClock
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taskdroid.manager.databinding.FragmentListBinding
import com.taskdroid.manager.databinding.ItemServiceBinding
import com.taskdroid.manager.util.MemStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServiceRow(val title: String, val subtitle: String, val meta: String)

class ServicesFragment : androidx.fragment.app.Fragment() {
    private var binding: FragmentListBinding? = null
    private var adapter: ServiceAdapter? = null
    private var job: Job? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?
    ): android.view.View {
        val b = FragmentListBinding.inflate(inflater, container, false)
        binding = b
        adapter = ServiceAdapter()
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        return b.root
    }

    override fun onResume() {
        super.onResume()
        val scope = CoroutineScope(Dispatchers.Main)
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(2000)
            }
        }
    }

    override fun onPause() {
        job?.cancel()
        super.onPause()
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val ctx = requireContext()
        val rows = mutableListOf<ServiceRow>()
        try {
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = ctx.packageManager

            @Suppress("DEPRECATION")
            val procs = am.runningAppProcesses
            val procCount = procs?.size ?: 0
            var pssTotal = 0L
            if (procs != null) {
                for (p in procs) {
                    pssTotal += p.memoryInfo?.totalPss ?: 0
                }
            }

            // header row
            rows.add(ServiceRow("Running processes", "$procCount processes", "total PSS ${MemStorage.formatBytes(pssTotal * 1024)}"))

            @Suppress("DEPRECATION")
            val services = am.runningServices(Int.MAX_VALUE)
            rows.add(ServiceRow("Running services", "${services.size} services", "note: Android limits visibility of other apps' services"))

            for (s in services) {
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(s.service.packageName, 0)).toString() } catch (_: Throwable) { s.service.packageName }
                val type = when (s.foreground) {
                    true -> "FOREGROUND"
                    false -> "background"
                    null -> "?"
                }
                val clientLabel = s.clientCount?.let { " $it client(s)" } ?: ""
                rows.add(
                    ServiceRow(
                        title = label,
                        subtitle = "$type  ·  ${s.service.flattenToShortString()}",
                        meta = "started ${s.activeSince}s ago${clientLabel}"
                    )
                )
            }
        } catch (e: Throwable) {
            rows.add(ServiceRow("Error", e.message ?: "unknown", "swipe? none"))
        }
        withContext(Dispatchers.Main) {
            if (isAdded) adapter?.submit(rows)
        }
    }
}

class ServiceAdapter : RecyclerView.Adapter<ServiceAdapter.VH>() {
    private val items = mutableListOf<ServiceRow>()

    fun submit(list: List<ServiceRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val b = ItemServiceBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.binding.lblName.text = r.title
        holder.binding.lblPkg.text = r.subtitle
        holder.binding.lblMeta.text = r.meta
    }

    class VH(val binding: ItemServiceBinding) : RecyclerView.ViewHolder(binding.root)
}