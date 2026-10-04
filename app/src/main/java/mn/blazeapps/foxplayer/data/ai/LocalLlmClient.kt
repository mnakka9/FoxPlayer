package mn.blazeapps.foxplayer.data.ai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for local on-device LLM runtimes supporting OpenAI-compatible APIs,
 * such as Qualcomm GenieX runtime (e.g. running local Qwen), Ollama, or llama.cpp.
 *
 * Implements agentic tool calling: when asked a question requiring current knowledge, lore,
 * or book context, the local model invokes the `web_search` tool, which is executed
 * on-device and fed back into the model for final synthesis.
 */
class LocalLlmClient(
    var endpointUrl: String = DEFAULT_ENDPOINT,
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8080/v1"
        private const val TAG = "FoxPlayer-LocalLLM"

        private fun logE(message: String, throwable: Throwable? = null) {
            try {
                Log.e(TAG, message, throwable)
            } catch (_: RuntimeException) {
                // JVM test fallback
            }
        }
    }

    /**
     * Checks if a local LLM daemon (e.g. GenieX or local runner) is active.
     */
    fun isAvailable(): Boolean {
        return try {
            val url = URL("$endpointUrl/models")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 800
                readTimeout = 800
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Runs an agentic tool-calling loop:
     * 1. Asks the local LLM with the `web_search` function declaration.
     * 2. If the LLM returns a tool call, executes [searchCallback] to perform the search.
     * 3. Sends tool results back to the LLM to get the synthesized response.
     *
     * Returns Pair(synthesizedText, usedLocalLlm = true), or null if the local endpoint is unavailable or errors.
     */
    fun chatWithAgenticSearch(
        query: String,
        searchCallback: (String) -> String,
    ): Pair<String, Boolean>? {
        return try {
            val initialJson = JSONObject().apply {
                put("model", "qwen")
                put("temperature", 0.7)
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an intelligent audiobook and knowledge assistant. When asked about entities, mythology, characters, or topics requiring facts, invoke the web_search tool.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                }
                put("messages", messagesArray)

                val toolsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", "web_search")
                            put("description", "Search the web across Wikipedia, DuckDuckGo, and Brave Search for facts, definitions, and lore.")
                            put("parameters", JSONObject().apply {
                                put("type", "object")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "The search query to look up")
                                    })
                                })
                                put("required", JSONArray().apply { put("query") })
                            })
                        })
                    })
                }
                put("tools", toolsArray)
            }

            val firstResponse = postJson("$endpointUrl/chat/completions", initialJson, timeoutMs = 6000) ?: return null
            val firstChoice = firstResponse.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val messageObj = firstChoice.optJSONObject("message") ?: return null

            val toolCalls = messageObj.optJSONArray("tool_calls")
            if (toolCalls != null && toolCalls.length() > 0) {
                val call = toolCalls.getJSONObject(0)
                val functionObj = call.optJSONObject("function")
                val fnName = functionObj?.optString("name")
                val fnArgsStr = functionObj?.optString("arguments").orEmpty()
                val fnArgs = try { JSONObject(fnArgsStr) } catch (_: Exception) { JSONObject() }
                val searchQuery = fnArgs.optString("query").ifBlank { query }

                // Step 2: Execute search tool in Android Kotlin
                val searchResultsText = searchCallback(searchQuery)

                // Step 3: Send tool results back to local LLM for final synthesis
                val followUpMessages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are an intelligent knowledge assistant. Synthesize the provided search results into a clean, complete, markdown formatted answer.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                    put(messageObj) // assistant tool call message
                    put(JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", call.optString("id", "call_1"))
                        put("name", fnName ?: "web_search")
                        put("content", searchResultsText)
                    })
                }

                val secondJson = JSONObject().apply {
                    put("model", "qwen")
                    put("temperature", 0.7)
                    put("messages", followUpMessages)
                }

                val finalResp = postJson("$endpointUrl/chat/completions", secondJson, timeoutMs = 12000)
                val finalChoice = finalResp?.optJSONArray("choices")?.optJSONObject(0)
                val finalText = finalChoice?.optJSONObject("message")?.optString("content")?.trim()

                if (!finalText.isNullOrBlank()) {
                    return Pair(finalText, true)
                }
            }

            val directContent = messageObj.optString("content").trim()
            if (directContent.isNotBlank()) {
                Pair(directContent, true)
            } else {
                null
            }
        } catch (e: Exception) {
            logE("Local LLM agentic error: ${e.message}", e)
            null
        }
    }

    private fun postJson(urlStr: String, json: JSONObject, timeoutMs: Int): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { it.write(json.toString()) }
        }
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            return null
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(text)
    }
}
