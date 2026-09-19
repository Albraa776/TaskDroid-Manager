package com.taskdroid.manager.fragments

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.UserHandle
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.taskdroid.manager.R
import com.taskdroid.manager.databinding.FragmentAppsBinding
import com.taskdroid.manager.databinding.ItemAppBinding
import com.taskdroid.manager.util.MemStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.isVisible

data class AppInfo(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val isSystem: Boolean,
    val enabled: Boolean,
    val firstInstall: Long,
    val updateTime: Long,
    val icon: Drawable?
)

class AppsFragment : androidx.fragment.app.Fragment() {

    private var binding: FragmentAppsBinding? = null
    private var adapter: AppAdapter? = null
    private var allApps: List<AppInfo> = emptyList()
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?
    ): android.view.View {
        val b = FragmentAppsBinding.inflate(inflater, container, false)
        binding = b
        adapter = AppAdapter()
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                adapter?.setQuery(q)
            }
        })
        return b.root
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onPause() {
        loadJob?.cancel()
        super.onPause()
    }

    private fun loadApps() {
        loadJob?.cancel()
        loadJob = CoroutineScope(Dispatchers.IO).launch {
            val ctx = requireContext()
            val pm = ctx.packageManager
            val infos = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val userApps = mutableListOf<AppInfo>()

            for (a in infos) {
                try {
                    val label = pm.getApplicationLabel(a).toString()
                    val pkgInfo = pm.getPackageInfo(a.packageName, 0)
                    var size = -1L
                    try {
                        val ssm = ctx.getSystemService<android.app.usage.StorageStatsManager>()
                        if (ssm != null && Build.VERSION.SDK_INT >= 26) {
                            val uuid = try {
                                Class.forName("android.os.StorageManager")
                                    .getField("UUID_DEFAULT").get(null) as? java.util.UUID
                            } catch (_: Throwable) { null }
                            if (uuid != null) {
                                val stats = ssm.queryStatsForPackage(
                                    uuid, a.packageName, android.os.Process.myUserHandle()
                                )
                                size = stats.totalBytes
                            }
                        }
                    } catch (_: Throwable) {
                        size = -1
                    }
                    userApps.add(
                        AppInfo(
                            name = label,
                            packageName = a.packageName,
                            versionName = pkgInfo.versionName ?: "?",
                            versionCode = pkgInfo.longVersionCode,
                            sizeBytes = size,
                            isSystem = (a.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            enabled = a.enabled,
                            firstInstall = pkgInfo.firstInstallTime,
                            updateTime = pkgInfo.lastUpdateTime,
                            icon = a.loadIcon(pm)
                        )
                    )
                } catch (_: Throwable) {
                }
            }
            userApps.sortWith(compareBy<AppInfo> { it.isSystem }.thenBy { it.name.lowercase() })
            allApps = userApps

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    binding?.lblCount?.text = "${userApps.size} installed apps  (${userApps.count { !it.isSystem }} user · ${userApps.count { it.isSystem }} system)"
                    adapter?.submit(allApps)
                }
            }
        }
    }
}

class AppAdapter : RecyclerView.Adapter<AppAdapter.VH>() {
    data class Row(val app: AppInfo)

    private val items = mutableListOf<Row>()
    private var query = ""

    fun setQuery(q: String) {
        query = q.lowercase()
        submitProgress()
    }

    fun submit(list: List<AppInfo>) {
        allAppListCache = list
        submitProgress()
    }

    private var allAppListCache: List<AppInfo> = emptyList()

    private fun submitProgress() {
        items.clear()
        val src = allAppListCache
        val filtered = if (query.isBlank()) src else src.filter {
            it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
        items.addAll(filtered.map { Row(it) })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val b = ItemAppBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position].app
        val ctx = holder.binding.root.context
        holder.binding.lblName.text = app.name
        holder.binding.lblPkg.text = app.packageName
        val icon = app.icon
        holder.binding.icon.setImageDrawable(icon ?: ContextCompat.getDrawable(ctx, android.R.drawable.sym_def_app_icon))
        holder.binding.icon.isVisible = icon != null
        val systemTag = if (app.isSystem) "system · " else ""
        val sizeTxt = if (app.sizeBytes > 0) MemStorage.formatBytes(app.sizeBytes) else "size n/a"
        val enabledTxt = if (app.enabled) "" else " · DISABLED"
        holder.binding.lblMeta.text = "$systemTag v${app.versionName} · $sizeTxt$enabledTxt"

        holder.binding.root.setOnClickListener {
            try {
                val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) ctx.startActivity(intent)
                else {
                    val mi = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${app.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    ctx.startActivity(mi)
                }
            } catch (_: Throwable) {
            }
        }
    }

    class VH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}