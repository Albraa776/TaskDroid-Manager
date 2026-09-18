package com.taskdroid.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.taskdroid.manager.databinding.ActivityMainBinding
import com.taskdroid.manager.fragments.AppsFragment
import com.taskdroid.manager.fragments.BatteryFragment
import com.taskdroid.manager.fragments.CpuFragment
import com.taskdroid.manager.fragments.NetworkFragment
import com.taskdroid.manager.fragments.OverviewFragment
import com.taskdroid.manager.fragments.ServicesFragment
import com.taskdroid.manager.fragments.SystemFragment
import com.taskdroid.manager.fragments.CareFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        requestRuntimePermissions()

        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = TABS.size
            override fun createFragment(position: Int): Fragment {
                return when (TABS[position]) {
                    "Overview" -> OverviewFragment()
                    "Network" -> NetworkFragment()
                    "Battery" -> BatteryFragment()
                    "CPU" -> CpuFragment()
                    "Apps" -> AppsFragment()
                    "Services" -> ServicesFragment()
                    "System" -> SystemFragment()
                    else -> CareFragment()
                }
            }
        }

        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = TABS[position]
        }.attach()
    }

    private fun requestRuntimePermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    companion object {
        val TABS = listOf("Overview", "Network", "Battery", "CPU", "Apps", "Services", "System", "Care")
    }
}