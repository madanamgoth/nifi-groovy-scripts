def flowFile = session.get()
if (!flowFile) return

try {
    // Get the long code from flow file attribute or content
    def longCode = flowFile.getAttribute("userLongCode") ?: "123*1234*234234*234234*2342#"
    def requestedIndex = flowFile.getAttribute("index")?.toInteger() ?: 0
    
    // Remove the trailing # and leading * if present, then split by *
    def cleanCode = longCode.replaceAll(/#$/, '')  // Remove trailing #
    cleanCode = cleanCode.replaceAll(/^\*/, '')    // Remove leading * if present
    def parts = cleanCode.split('\\*')
    
    log.info("Original code: ${longCode}")
    log.info("Split parts: ${parts}")
    log.info("Requested index: ${requestedIndex}")
    
    // Validate index
    if (requestedIndex < 0 || requestedIndex >= parts.length) {
        throw new Exception("Index ${requestedIndex} is out of bounds. Available indices: 0-${parts.length - 1}")
    }
    
    // Extract the value at requested index
    def extractedValue = parts[requestedIndex]
    
    // Determine if this is the last node (before the # symbol)
    def isLastInput = (requestedIndex == parts.length - 1) ? "Y" : "N"
    
    // Add extracted value to flow file attributes
    flowFile = session.putAttribute(flowFile, "userInput", extractedValue)
    flowFile = session.putAttribute(flowFile, "extracted.index", requestedIndex.toString())
    flowFile = session.putAttribute(flowFile, "total.parts", parts.length.toString())
    flowFile = session.putAttribute(flowFile, "isLastInput", isLastInput)
    flowFile = session.putAttribute(flowFile, "newRequest", "N")
    
    // Optionally add all parts as separate attributes
    parts.eachWithIndex { part, index ->
        flowFile = session.putAttribute(flowFile, "part.${index}", part)
    }
    
    session.transfer(flowFile, REL_SUCCESS)
    log.info("Successfully extracted value '${extractedValue}' at index ${requestedIndex}, isLastInput: ${isLastInput}")

} catch (Exception e) {
    log.error("Error extracting value from long code: " + e.getMessage(), e)
    flowFile = session.putAttribute(flowFile, "extraction.error", e.getMessage())
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}