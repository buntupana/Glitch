package com.vodafone.glitchtest.ui.main

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.vodafone.glitchtest.R
import com.vodafone.glitchtest.databinding.BottomSheetFragmentBinding

class BottomSheetFragment : Fragment() {

    companion object {
        fun newInstance() = BottomSheetFragment()
    }

    private val adapter = Adapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = BottomSheetFragmentBinding.inflate(inflater, container, false)

        binding.recyclerView.adapter = adapter

        adapter.submitList(itemList)

        return binding.root
    }


        private val itemList = listOf(
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
            "Hello",
            "Bye",
        )
}