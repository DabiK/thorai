package app.regie.domain.model

enum class SessionNoticeKind { QUESTION, FINISHED, FAILED }

data class SessionNotice(val sessionId: SessionId, val kind: SessionNoticeKind, val title: String, val text: String)
