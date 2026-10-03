package io.github.pesterevnikita.focusgate.maintenance

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/**
 * Android's content CLI splits --extra on colons, which corrupts URLs, regexes and profile JSON.
 * A single --arg JSON object transports those values losslessly as named strings.
 */
object MaintenanceArguments {
    /** Choose exactly one transport so duplicated values cannot acquire an ambiguous precedence. */
    fun resolve(arg: String?, extras: Map<String,String>): Map<String,String> {
        if(arg==null) return extras
        require(extras.isEmpty()) { "Do not combine argument envelope and extras" }
        return parseJson(arg)
    }

    /** Bound Binder input, reject duplicate keys and require strictly quoted string values. */
    fun parseJson(json: String): Map<String,String> {
        require(json.toByteArray(Charsets.UTF_8).size<=98304) { "Argument envelope exceeds 96 KiB" }
        return try {
            JsonReader(StringReader(json)).use { reader ->
                reader.strictness=Strictness.STRICT
                require(reader.peek()==JsonToken.BEGIN_OBJECT)
                reader.beginObject()
                val values=linkedMapOf<String,String>()
                while(reader.hasNext()) {
                    val key=reader.nextName()
                    require(key !in values && reader.peek()==JsonToken.STRING)
                    values[key]=reader.nextString()
                }
                reader.endObject()
                require(reader.peek()==JsonToken.END_DOCUMENT)
                values
            }
        } catch(error: Exception) {
            // Parser errors can include input paths; provide a fixed diagnostic instead of echoing data.
            throw IllegalArgumentException("Use a JSON object with unique names and string values")
        }
    }
}
