package dev.adamwardvgp.sample.adventure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

expect fun CreationExtras.createAdventureSavedStateHandle(): SavedStateHandle
