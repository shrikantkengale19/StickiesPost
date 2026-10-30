package com.example.stickiespost.data.remote

import android.net.Uri
import com.example.stickiespost.data.security.ApiKeyStore
import com.example.stickiespost.domain.assistant.CommandInterpreter
import com.example.stickiespost.domain.model.AssistantCommand
import com.example.stickiespost.domain.model.NoteCategory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class GeminiCommandInterpreter(private val apiKeyStore: ApiKeyStore) : CommandInterpreter {
    override suspend fun interpret(utterance: String): AssistantCommand = withContext(Dispatchers.IO) {
        val apiKey = apiKeyStore.get()
            ?: throw IllegalStateException("Add your Gemini API key in Settings before using the assistant.")
        val requestBody = createRequest(utterance)
        val endpoint = URL("$API_ENDPOINT?key=${Uri.encode(apiKey)}")
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext parseCommand(sendRequest(endpoint, requestBody))
            } catch (exception: GeminiHttpException) {
                if (!exception.isRetryable || attempt == MAX_ATTEMPTS - 1) {
                    if (exception.isRetryable) {
                        throw IOException(
                            "${exception.message} Gemini is temporarily busy; please try again shortly.",
                            exception
                        )
                    }
                    throw exception
                }
                val backoff = INITIAL_RETRY_DELAY_MILLIS * (1L shl attempt)
                delay(maxOf(backoff, exception.retryAfterMillis ?: 0L).coerceAtMost(MAX_RETRY_DELAY_MILLIS))
            }
        }
        throw IOException("Gemini request could not be completed.")
    }

    private fun sendRequest(endpoint: URL, requestBody: JSONObject): String {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        try {
            connection.outputStream.use { output ->
                output.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    JSONObject(responseText).getJSONObject("error").optString("message")
                } catch (_: JSONException) {
                    null
                }?.takeIf { it.isNotBlank() } ?: "Gemini request failed (HTTP $status)."
                val retryAfterMillis = connection.getHeaderField("Retry-After")
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0)
                    ?.times(1_000)
                throw GeminiHttpException(status, detail, retryAfterMillis)
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private class GeminiHttpException(
        val status: Int,
        message: String,
        val retryAfterMillis: Long?
    ) : IOException(message) {
        val isRetryable: Boolean
            get() = status == TOO_MANY_REQUESTS_STATUS || status in 500..599
    }

    private fun createRequest(utterance: String): JSONObject {
        val now = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.now().atZone(ZoneId.systemDefault())
        )
        val prompt = """
            You are the command parser for a personal notes and reminders app.
            Current local date and time: $now
            Interpret the user's request and return only one JSON object with these fields:
            {"action":"SAVE or LIST","category":"GROCERIES, VEGETABLES, HOUSEHOLD, BEAUTY, WORK, PERSONAL, OTHER, or ALL",
             "items":["short individual note items"],"reminderAt":"ISO-8601 date-time with UTC offset or null",
             "reply":"brief friendly confirmation"}
            Use SAVE when the user tells the app to remember, add, or note new information.
            Split lists into separate concise items, retaining useful quantities and details.
            Use LIST when the user asks to see, recall, or be reminded of things already saved.
            For LIST, infer a category from the request; use ALL only when no category is implied.
            For SAVE, choose exactly one category other than ALL.
            Use VEGETABLES for fresh vegetables, GROCERIES for general food and grocery shopping,
            HOUSEHOLD for home supplies and cleaning items, and BEAUTY for beauty and grooming items.
            Set reminderAt only when the user explicitly requests a timed reminder and a time can
            be determined from the current local date and time above. Interpret relative dates
            using that current time and include the local UTC offset. Otherwise use null.
            Never invent note contents or a reminder time. Keep reply concise.
            User request: ${JSONObject.quote(utterance)}
        """.trimIndent()

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.1)
                    .put("responseMimeType", "application/json")
            )
    }

    private fun parseCommand(responseText: String): AssistantCommand {
        try {
            val candidate = JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val jsonStart = candidate.indexOf('{')
            val jsonEnd = candidate.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd < jsonStart) throw IOException("Gemini returned an invalid command.")
            val parsed = JSONObject(candidate.substring(jsonStart, jsonEnd + 1))
            val action = when (parsed.getString("action").uppercase()) {
                "SAVE" -> AssistantCommand.Action.SAVE
                "LIST" -> AssistantCommand.Action.LIST
                else -> throw IOException("Gemini returned an unsupported command.")
            }
            val itemsJson = parsed.optJSONArray("items") ?: JSONArray()
            val items = List(itemsJson.length()) { index -> itemsJson.getString(index) }
            val reminderText = parsed.optString("reminderAt").takeUnless {
                it.isBlank() || it.equals("null", ignoreCase = true)
            }
            return AssistantCommand(
                action = action,
                category = NoteCategory.fromModelValue(parsed.getString("category")),
                items = items,
                reminderAtMillis = reminderText?.let(::parseReminderTime),
                reply = parsed.optString("reply")
            )
        } catch (exception: JSONException) {
            throw IOException("Gemini returned a response that could not be understood.", exception)
        } catch (exception: IllegalArgumentException) {
            throw IOException("Gemini returned an invalid note category.", exception)
        }
    }

    private fun parseReminderTime(value: String): Long =
        try {
            java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (exception: DateTimeParseException) {
            throw IOException("Gemini returned an invalid reminder time.", exception)
        }

    private companion object {
        const val API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_ATTEMPTS = 3
        const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 10_000L
        const val TOO_MANY_REQUESTS_STATUS = 429
    }
}
