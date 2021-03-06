package app.akilesh.qacc.ui.home

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EdgeEffect
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.akilesh.qacc.Const.Paths.overlayPath
import app.akilesh.qacc.Const.prefix
import app.akilesh.qacc.R
import app.akilesh.qacc.databinding.HomeFragmentBinding
import app.akilesh.qacc.model.Accent
import app.akilesh.qacc.utils.AppUtils.getColorAccent
import app.akilesh.qacc.utils.AppUtils.showSnackbar
import app.akilesh.qacc.utils.AppUtils.toHex
import com.topjohnwu.superuser.Shell


class HomeFragment: Fragment() {

    private lateinit var accentViewModel: AccentViewModel
    private lateinit var binding: HomeFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = HomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val useSystemAccent = sharedPreferences.getBoolean("system_accent", false)
        if (useSystemAccent) {
            binding.recyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                    return EdgeEffect(view.context).apply {
                        color = requireContext().getColorAccent()
                    }
                }
            }
        }

        val accentListAdapter =
            AccentListAdapter(requireContext(),
                {
                    val navDirections =
                        HomeFragmentDirections.edit(
                            it.colorLight,
                            it.colorDark,
                            it.name,
                            true
                        )
                    findNavController().navigate(navDirections)
                },
                { accent ->
                    val appName = accent.pkgName.substringAfter(prefix)
                    if (SDK_INT >= P) {
                        Shell.su(
                            "rm -f $overlayPath/$appName.apk"
                        ).exec().apply {
                            if (isSuccess) {
                                accentViewModel.delete(accent)
                                showSnackbar(
                                    view,
                                    String.format(getString(R.string.accent_removed), accent.name)
                                )
                            }
                        }
                    } else {
                        Toast.makeText(
                            requireContext().applicationContext,
                            requireContext().getString(R.string.uninstalling, accent.name),
                            Toast.LENGTH_LONG
                        ).show()
                        val result = Shell.su("pm uninstall ${accent.pkgName}").exec()
                        Log.d("pm-uninstall", accent.name + " - " + result.out)
                        if (result.isSuccess) {
                            accentViewModel.delete(accent)
                            showSnackbar(view, getString(R.string.accent_removed, accent.name))
                        }
                    }
                }
            )
        binding.recyclerView.apply {
            adapter = accentListAdapter
            layoutManager = GridLayoutManager(requireContext(), 2, GridLayoutManager.VERTICAL, false)
        }
        accentViewModel = ViewModelProvider(this).get(AccentViewModel::class.java)
        accentViewModel.allAccents.observe(viewLifecycleOwner, Observer { accents ->
            accents?.let { accentListAdapter.setAccents(it) }
            insertMissing(accents)
        })
    }

    private fun insertMissing(accents: MutableList<Accent>) {
        val inDB = mutableSetOf<String>()
        val installed = mutableSetOf<String>()

        if (accents.isNotEmpty())
           inDB.addAll(accents.map { it.pkgName })

        val installedAccents: MutableList<String> =  Shell.su(
            "pm list packages -f $prefix | sed s/package://"
        ).exec().out

        if (installedAccents.isNotEmpty())
            installed.addAll(
                    installedAccents.map { it.substringAfterLast('=') }
            )
        if (SDK_INT >= P) {
            val list = Shell.su("ls -1 $overlayPath").exec().out
            if (list.isNotEmpty()) {
                val inModule = list.map {
                    prefix + it.removeSuffix(".apk")
                }
                val deleted = installed.subtract(inModule)
                if (deleted.isNotEmpty()) {
                    installed.removeAll(deleted)
                }
            }
        }

        val missingAccents = installed.subtract(inDB)
        if (missingAccents.isNotEmpty()) {
            Log.w("Missing in db", missingAccents.toString())
            missingAccents.forEach { pkgName ->
                val packageInfo = requireContext().packageManager.getPackageInfo(pkgName, 0)
                val applicationInfo = packageInfo.applicationInfo
                val accentName =
                    requireContext().packageManager.getApplicationLabel(applicationInfo).toString()
                val resources = requireContext().packageManager.getResourcesForApplication(applicationInfo)
                val accentLightId =
                    resources.getIdentifier("accent_device_default_light", "color", pkgName)
                val accentDarkId =
                    resources.getIdentifier("accent_device_default_dark", "color", pkgName)
                if (accentLightId != 0 && accentDarkId != 0) {
                    val colorLight = resources.getColor(accentLightId, null)
                    val colorDark = resources.getColor(accentDarkId, null)
                    val accent = Accent(
                        pkgName,
                        accentName,
                        toHex(colorLight),
                        toHex(colorDark)
                    )
                    Log.d("Inserting accent", accent.toString())
                    accentViewModel.insert(accent)
                }
            }
        }
    }
}