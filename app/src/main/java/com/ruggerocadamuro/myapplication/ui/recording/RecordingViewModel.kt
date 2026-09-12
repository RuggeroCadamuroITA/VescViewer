package com.ruggerocadamuro.myapplication.ui.recording

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruggerocadamuro.myapplication.data.recording.RecordingState
import com.ruggerocadamuro.myapplication.service.RecorderHolder
import com.ruggerocadamuro.myapplication.service.RideRecordingService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class RecordingViewModel(app: Application) : AndroidViewModel(app) {
    private val recorder = RecorderHolder.recorder(app)
    val state: StateFlow<RecordingState> = recorder.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingState())

    fun start() = RideRecordingService.start(getApplication())
    fun pause() = RideRecordingService.pause(getApplication())
    fun resume() = RideRecordingService.resume(getApplication())
    fun stop() = RideRecordingService.stop(getApplication())
}
