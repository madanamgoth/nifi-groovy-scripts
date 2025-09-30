def flowFile = session.get()
if (!flowFile) return

try {
    // 1. Get attributes from the FlowFile
    def longCode = flowFile.getAttribute("userLongCode")
    if (!longCode) {
        throw new Exception("Attribute 'longCode' is missing or empty.")
    }
    def requestedIndex = flowFile.getAttribute("index")?.toInteger()
    if (requestedIndex == null) {
        throw new Exception("Attribute 'index' is missing or not a valid integer.")
    }

    // 2. Clean and parse the longCode
    def cleanCode = longCode.replaceAll(/#$/, '').replaceAll(/^\*/, '')
    def parts = cleanCode.split('\\*')

    log.info("Original code: '{}', Parsed parts: {}", longCode, parts)

    // 3. Validate the index
    if (requestedIndex < 0 || requestedIndex >= parts.length) {
        throw new Exception("Index ${requestedIndex} is out of bounds for the parsed parts. Available indices: 0 to ${parts.length - 1}.")
    }

    // 4. Extract the value and determine the flags
    def userInput = parts[requestedIndex]
    def isLastInput = (requestedIndex == parts.length - 1) ? "Y" : "N"
    def isInputRequired = userInput.startsWith('$') ? "Y" : "N"
    def isCompositAccess = (isLastInput == "Y") ? "N" : "Y"
    def newRequest = "N"

    // 5. Add the new attributes to the FlowFile
    flowFile = session.putAttribute(flowFile, 'userInput', userInput)
    flowFile = session.putAttribute(flowFile, 'isLastInput', isLastInput)
    flowFile = session.putAttribute(flowFile, 'isInputRequired', isInputRequired)
    flowFile = session.putAttribute(flowFile, 'userSession.isCompositAccess', isCompositAccess)
    flowFile = session.putAttribute(flowFile, 'newRequest', newRequest)
    flowFile = session.putAttribute(flowFile, 'isCompositAccess', isCompositAccess)

    log.info("Successfully parsed index {}: userInput='{}', isLastInput='{}', isInputRequired='{}', userSession.isCompositAccess='{}'", requestedIndex, userInput, isLastInput, isInputRequired, isCompositAccess)
    
    session.transfer(flowFile, REL_SUCCESS)

} catch (Exception e) {
    log.error("Failed to parse dynamic code '{}' at index {}: {}", flowFile.getAttribute("longCode"), flowFile.getAttribute("index"), e.getMessage(), e)
    flowFile = session.putAttribute(flowFile, 'parsing.error', e.getMessage())
    session.transfer(flowFile, REL_FAILURE)
}