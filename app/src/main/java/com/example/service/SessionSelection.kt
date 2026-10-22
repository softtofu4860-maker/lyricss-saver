package com.example.service

internal data class SessionCandidate(val key: String, val playing: Boolean, val updateTime: Long, val hasTitle: Boolean)
internal object SessionSelection {
    fun choose(sessions: List<SessionCandidate>, current: String?): String? {
        val valid = sessions.filter { it.hasTitle }
        val playing = valid.filter { it.playing }
        if (playing.isNotEmpty()) return playing.sortedWith(compareByDescending<SessionCandidate> { it.updateTime }
            .thenByDescending { it.key == current }).first().key
        return valid.firstOrNull { it.key == current }?.key ?: valid.firstOrNull()?.key
    }
}
