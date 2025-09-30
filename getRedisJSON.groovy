// JAR is now in NiFi lib directory - no @Grab needed!
import org.apache.nifi.processor.io.OutputStreamCallback
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.io.OutputStream
import com.example.utils.RedisConnectionManager

def flowFile = session.get()
if (!flowFile) return

def redisKey = flowFile.getAttribute("userSession.currentNode") ?: "defaultKey"

try {
    // Use withConnection for automatic resource management
    def jsonResult = RedisConnectionManager.withConnection(context, log) { jedis ->
        def jsonGetCommand = [getRaw: { "JSON.GET".bytes }] as redis.clients.jedis.commands.ProtocolCommand
        return jedis.sendCommand(jsonGetCommand, redisKey, ".")
        
    }
    
    if (jsonResult) {
        flowFile = session.write(flowFile, new OutputStreamCallback() {
            @Override
            void process(OutputStream outputStream) throws IOException {
                // Direct write - no JSON parsing needed since Redis returns first object only
                if (jsonResult instanceof byte[]) {
                    outputStream.write(jsonResult)
                } else {
                    outputStream.write(jsonResult.toString().getBytes(StandardCharsets.UTF_8))
                }
            }
        })
        session.transfer(flowFile, REL_SUCCESS)
        log.info("Successfully retrieved Redis JSON for key: ${redisKey}")
    } else {
        log.warn("No data found in Redis for key: ${redisKey}")
        session.transfer(flowFile, REL_FAILURE)
    }

} catch (Exception e) {
    log.error("Error during Redis GET operation: " + e.getMessage(), e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
