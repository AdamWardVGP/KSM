package dev.adamwardvgp.sample.adventure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras

actual fun CreationExtras.createAdventureSavedStateHandle(): SavedStateHandle =
  createSavedStateHandle()
