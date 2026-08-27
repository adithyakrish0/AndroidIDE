import com.google.ai.client.generativeai.type.*
import org.json.JSONObject

fun main() {
    val textPart = TextPart("hello")
    println(textPart.text)

    val fnCall = FunctionCallPart("my_fn", mapOf("arg1" to "val1"))
    println(fnCall.name + " " + fnCall.args)

    val fnResp = FunctionResponsePart("my_fn", JSONObject().put("res", "val"))
    println(fnResp.name + " " + fnResp.response)

    val content = content {
        part(textPart)
    }
    println(content.role)
}
