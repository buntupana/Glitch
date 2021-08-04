package com.vodafone.glitchtest.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.vodafone.glitchtest.R
import com.vodafone.glitchtest.databinding.MainFragmentBinding

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var binding: MainFragmentBinding

    private var activeSubFragment: Fragment = Nested0Fragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MainFragmentBinding.inflate(inflater, container, false)

        binding.button.setOnClickListener {
            activeSubFragment = if (activeSubFragment is Nested0Fragment) {
                Nested1Fragment()
            } else {
                Nested0Fragment()
            }
            childFragmentManager.beginTransaction()
                .replace(R.id.content_container, activeSubFragment).commit()
        }

        childFragmentManager.beginTransaction().replace(R.id.content_container, activeSubFragment)
            .commit()

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, BottomSheetFragment.newInstance()).commit()

        return binding.root
    }

}