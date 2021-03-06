package app.akilesh.qacc.ui.customisation

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import androidx.work.WorkInfo
import app.akilesh.qacc.Const.prefix
import app.akilesh.qacc.R
import app.akilesh.qacc.databinding.ColorCustomisationFragmentBinding
import app.akilesh.qacc.model.Accent
import app.akilesh.qacc.utils.AppUtils.showSnackbar
import app.akilesh.qacc.utils.AppUtils.toHex
import app.akilesh.qacc.ui.home.AccentViewModel
import app.akilesh.qacc.ui.colorpicker.ColorPickerViewModel
import kotlin.properties.Delegates

class ColorCustomisationFragment: Fragment() {

    private lateinit var binding: ColorCustomisationFragmentBinding
    private lateinit var model: CustomisationViewModel
    private lateinit var accentViewModel: AccentViewModel
    private val args: ColorCustomisationFragmentArgs by navArgs()
    private var colorLight by Delegates.notNull<Int>()
    private var colorDark by Delegates.notNull<Int>()
    private var separateAccents by Delegates.notNull<Boolean>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = ColorCustomisationFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textInputLayout.visibility = if (args.fromHome) View.VISIBLE else View.GONE
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        separateAccents = sharedPreferences.getBoolean("separate_accent", false)
        if (SDK_INT < Q) separateAccents = false

        var accentLight = args.lightAccent
        var accentDark = args.darkAccent
        var accentName = args.accentName

        if (args.fromHome) {
            binding.name.setText(accentName)
            binding.name.doAfterTextChanged {
                accentName = it.toString().trim()
            }
        }

        colorLight = Color.parseColor(accentLight)
        setPreviewLight(colorLight, accentLight)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorLight, hsl)
        binding.lightSliders.hue.value = hsl[0]
        binding.lightSliders.saturation.value = hsl[1]
        binding.lightSliders.lightness.value = hsl[2]

        model = ViewModelProvider(this).get(CustomisationViewModel::class.java)
        accentViewModel = ViewModelProvider(this).get(AccentViewModel::class.java)

        val lightAccentObserver = Observer<String> {
            accentLight = it
            colorLight = Color.parseColor(accentLight)
            setPreviewLight(colorLight, accentLight)
        }
        model.lightAccent.observe(viewLifecycleOwner, lightAccentObserver)
        editAccentLight()

        if (!separateAccents && SDK_INT < Q) {
            binding.toggleButton.visibility = View.GONE
            binding.previewDark.root.visibility = View.GONE
        }
        else {
            colorDark = if (accentDark.isNotBlank()) Color.parseColor(accentDark) else colorLight
            setPreviewDark(colorDark, accentDark)
            ColorUtils.colorToHSL(colorDark, hsl)
            binding.darkSliders.hue.value = hsl[0]
            binding.darkSliders.saturation.value = hsl[1]
            binding.darkSliders.lightness.value = hsl[2]

            val darkAccentObserver = Observer<String> {
                accentDark = it
                colorDark = Color.parseColor(accentDark)
                setPreviewDark(colorDark, accentDark)
            }
            model.darkAccent.observe(viewLifecycleOwner, darkAccentObserver)
        }

        binding.toggleButton.addOnButtonCheckedListener { group, _, _ ->
            when(group.checkedButtonId) {
                binding.lightToggle.id -> {
                    binding.lightSliders.root.visibility = View.VISIBLE
                    binding.darkSliders.root.visibility = View.GONE
                    editAccentLight()
                }
                binding.darkToggle.id -> {
                    binding.lightSliders.root.visibility = View.GONE
                    binding.darkSliders.root.visibility = View.VISIBLE
                    editAccentDark()
                }
            }
        }

        binding.resetChip.setOnClickListener {
            val action =
                ColorCustomisationFragmentDirections.reset(
                    args.lightAccent,
                    args.darkAccent,
                    args.accentName
                )
            findNavController().navigate(action)
        }

        binding.buttonPrevious.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.buttonNext.setOnClickListener {

            if (accentName.isNotBlank()) {
                var suffix = "hex_" + accentLight.removePrefix("#")
                val dark: String
                if (SDK_INT < Q)
                    dark = accentLight
                else {
                    suffix += "_" + accentDark.removePrefix("#")
                    dark = accentDark
                }
                val pkgName = prefix + suffix
                val accent = Accent(pkgName, accentName, accentLight, dark)
                Log.d("accent-s", accent.toString())
                val creatorViewModel = ViewModelProvider(this).get(ColorPickerViewModel::class.java)
                creatorViewModel.create(accent)
                creatorViewModel.createWorkerId?.let { uuid ->
                    creatorViewModel.workManager.getWorkInfoByIdLiveData(uuid).observe(
                        viewLifecycleOwner, Observer { workInfo ->
                            Log.d("id", workInfo.id.toString())
                            Log.d("tag", workInfo.tags.toString())
                            Log.d("state", workInfo.state.name)

                            if (workInfo.state == WorkInfo.State.RUNNING && SDK_INT < P)
                                Toast.makeText(requireContext(), String.format(getString(R.string.creating, accentName)), Toast.LENGTH_SHORT).show()

                            if (workInfo.state == WorkInfo.State.SUCCEEDED && workInfo.state.isFinished) {
                                val accentViewModel = ViewModelProvider(this).get(AccentViewModel::class.java)
                                accentViewModel.insert(accent)
                                showSnackbar(view, String.format(getString(R.string.accent_created), accentName))
                                findNavController().navigate(R.id.action_global_home)
                            }
                            if (workInfo.state == WorkInfo.State.FAILED)
                                Toast.makeText(requireContext(), getString(R.string.error), Toast.LENGTH_LONG).show()
                        })
                }
            }
            else Toast.makeText(context, getString(R.string.toast_name_not_set), Toast.LENGTH_SHORT).show()
        }

    }

    private fun setPreviewLight(color: Int, hex: String) {
        val colorName = if (SDK_INT < Q) args.accentName else requireContext().resources.getString(R.string.light)
        val textColorLight = Palette.Swatch(color, 1).bodyTextColor
        val colorStateList = ColorStateList.valueOf(color)

        binding.apply {
            previewLight.colorName.text = String.format(requireContext().resources.getString(R.string.colour), colorName, hex)
            previewLight.colorName.setTextColor(textColorLight)
            previewLight.colorCard.backgroundTintList = colorStateList
        }

        binding.lightSliders.apply {
            hue.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
            saturation.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
            lightness.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
        }
        setPreview(color)
    }

    private fun setPreviewDark(color: Int, hex: String) {
        val textColorDark = Palette.Swatch(color, 1).bodyTextColor
        val colorStateList = ColorStateList.valueOf(color)

        binding.apply {
            previewDark.colorName.text = String.format(requireContext().resources.getString(R.string.colour), requireContext().resources.getString(R.string.dark), hex)
            previewDark.colorName.setTextColor(textColorDark)
            previewDark.colorCard.backgroundTintList = colorStateList
        }

        binding.darkSliders.apply {
            hue.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
            saturation.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
            lightness.apply {
                thumbColor = colorStateList
                haloColor = colorStateList
                trackColorActive = colorStateList
                trackColorInactive = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 50))
            }
        }

        setPreview(color)
    }

    private fun editAccentLight() {

        binding.lightSliders.root.visibility = View.VISIBLE
        binding.darkSliders.root.visibility = View.GONE
        setPreview(colorLight)

        var newColor: Int
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorLight, hsl)

        binding.lightSliders.hue.addOnChangeListener { _, value, _ ->
            hsl[0] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.lightAccent.value = toHex(newColor)
        }

        binding.lightSliders.saturation.addOnChangeListener { _, value, _ ->
            hsl[1] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.lightAccent.value = toHex(newColor)
        }

        binding.lightSliders.lightness.addOnChangeListener { _, value, _ ->
            hsl[2] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.lightAccent.value = toHex(newColor)
        }
    }

    private fun editAccentDark() {

        setPreview(colorDark)
        var newColor: Int
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorDark, hsl)

        binding.darkSliders.hue.addOnChangeListener { _, value, _ ->
            hsl[0] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.darkAccent.value = toHex(newColor)
        }

        binding.darkSliders.saturation.addOnChangeListener { _, value, _ ->
            hsl[1] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.darkAccent.value = toHex(newColor)
        }

        binding.darkSliders.lightness.addOnChangeListener { _, value, _ ->
            hsl[2] = value
            newColor = ColorUtils.HSLToColor(hsl)
            model.darkAccent.value = toHex(newColor)
        }
    }

    private fun setPreview(color: Int) {
        val colorStateList = ColorStateList.valueOf(color)
        binding.apply {
            resetChip.chipIconTint = colorStateList
            textInputLayout.apply {
                hintTextColor = colorStateList
                setBoxStrokeColorStateList(colorStateList)
            }
            if (SDK_INT >= Q) {
                name.textCursorDrawable?.setTintList(colorStateList)
                name.textSelectHandle?.setTintList(colorStateList)
                name.textSelectHandleLeft?.setTintList(colorStateList)
                name.textSelectHandleRight?.setTintList(colorStateList)
            }
            buttonPrevious.setTextColor(color)
            buttonPrevious.rippleColor = colorStateList
            buttonNext.backgroundTintList = colorStateList

            val typedValue = TypedValue()
            requireContext().theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
            val disabledColor = ColorUtils.setAlphaComponent(typedValue.data, 127)
            val disabledStateList =  ColorStateList.valueOf(disabledColor)
            when (toggleButton.checkedButtonId) {
                lightToggle.id -> {
                    lightToggle.apply {
                        iconTint = colorStateList
                        rippleColor = colorStateList
                        strokeColor = colorStateList
                        backgroundTintList = colorStateList.withAlpha(50)
                        setTextColor(color)
                    }
                    darkToggle.apply {
                        iconTint = disabledStateList
                        rippleColor = disabledStateList
                        strokeColor = disabledStateList
                        backgroundTintList = disabledStateList.withAlpha(0)
                        setTextColor(disabledColor)
                    }
                }
                darkToggle.id -> {
                    darkToggle.apply {
                        iconTint = colorStateList
                        rippleColor = colorStateList
                        strokeColor = colorStateList
                        backgroundTintList = colorStateList.withAlpha(50)
                        setTextColor(color)
                    }
                    lightToggle.apply {
                        iconTint = disabledStateList
                        rippleColor = disabledStateList
                        strokeColor = disabledStateList
                        backgroundTintList = disabledStateList.withAlpha(0)
                        setTextColor(disabledColor)
                    }
                }
            }
        }
    }
}
