import groovy.json.JsonSlurper

def flowFile = session.get()
if (!flowFile) return

def menuKey = flowFile.getAttribute("userSession.menuName")

flowFile = session.write(flowFile) { inputStream, outputStream ->
    def jsonSlurper = new JsonSlurper()
    def data = jsonSlurper.parse(inputStream)

    // Use the attribute value as key to get the array
    def menuItems = data[menuKey] ?: []
    def formattedMenuItems = []

    menuItems.eachWithIndex { item, index ->
        formattedMenuItems.add("${index + 1}. ${item}")
    }

    def menuString = formattedMenuItems.join('\n')
    outputStream.write(menuString.bytes)
}

session.transfer(flowFile, REL_SUCCESS)