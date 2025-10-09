package com.tangentlines.reflowclient.shared.ui.components

import android.media.AudioManager
import android.media.ToneGenerator

private val tone by lazy { ToneGenerator(AudioManager.STREAM_ALARM, 80) }

actual fun beep() {
    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150)
}