package com.example.stickiespost.domain.assistant

import com.example.stickiespost.domain.model.AssistantCommand

interface CommandInterpreter {
    suspend fun interpret(utterance: String): AssistantCommand
}
