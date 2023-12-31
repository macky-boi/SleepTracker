/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

                private val viewModelJob = Job()
                private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
                private var tonight = MutableLiveData<SleepNight?>()
                private var nights = database.getAllNights()
                val nightsString = Transformations.map(nights) { nights ->
                        formatNights(nights, application.resources)
                }
                private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
                val navigateToSleepQuality : LiveData<SleepNight>
                        get() = _navigateToSleepQuality
                var startButtonVisible = Transformations.map(tonight) {
                        it == null
                }
                var stopButtonVisible = Transformations.map(tonight) {
                        it != null
                }
                var clearButtonVisible = Transformations.map(nights) {
                        it?.isNotEmpty()
                }
                private val _showSnackBarEvent = MutableLiveData<Boolean>()
                val showSnackBarEvent : LiveData<Boolean>
                        get() = _showSnackBarEvent

        init {
                initializeTonight()
        }

        private fun initializeTonight() {
                uiScope.launch {
                        tonight.value = getTonightFromDatabase()
                        Log.i("SleepTrackerViewModel", "called initializeTonight() \n" +
                                "tonight: ${tonight.value}")
                }
        }

        private suspend fun getTonightFromDatabase(): SleepNight? {
                return withContext(Dispatchers.IO) {
                        var night = database.getTonight()
                        if (night?.startTimeMilli != night?.endTimeMilli) {
                                night = null
                        }
                        night
                }
        }

        fun onStartTracking() {
                uiScope.launch {
                        val newNight = SleepNight()
                        insert(newNight)
                        tonight.value = getTonightFromDatabase()
                }
        }

        private suspend fun insert(night: SleepNight) {
                withContext(Dispatchers.IO) {
                        database.insert(night)
                }
        }

        fun onStopTracking() {
                uiScope.launch {
                        Log.i("SleepTrackerViewModel", "called onStopTracking() \n" +
                                "tonight: ${tonight.value}")
                        val oldNight = tonight.value ?: return@launch
                        oldNight.endTimeMilli = System.currentTimeMillis()
                        update(oldNight)
                        Log.i("SleepTrackerViewModel", "called update(oldNight) \n" +
                                "tonight: ${tonight.value}")
                        _navigateToSleepQuality.value = oldNight
                }
        }

        private suspend fun update(night: SleepNight) {
                withContext(Dispatchers.IO) {
                        database.update(night)
                }
        }

        fun onClear() {
                uiScope.launch {
                        clear()
                        tonight.value = null
                        _showSnackBarEvent.value = true
                }
        }

        private suspend fun clear() {
                withContext(Dispatchers.IO) {
                        database.clear()
                }
        }

        fun doneNavigating() {
                _navigateToSleepQuality.value = null
        }

        fun doneShowingSnackBar() {
                _showSnackBarEvent.value = false
        }

        override fun onCleared() {
                super.onCleared()
                viewModelJob.cancel()
        }
}

