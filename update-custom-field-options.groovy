import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.customfields.manager.OptionsManager
import com.atlassian.jira.issue.customfields.option.Option
import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.BaseScript

import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

@BaseScript CustomEndpointDelegate delegate

updatebusinesstag(httpMethod: "POST", groups: ["jira-users"]) { MultivaluedMap queryParams, String body ->
    def jsonSlurper = new JsonSlurper()
    def jsonBody = jsonSlurper.parseText(body)
    String rawText = jsonBody.rawText
    String customFieldKey = jsonBody.customFieldKey

    // Step 1: Get options from a certain custom field
    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
    CustomField customField = customFieldManager.getCustomFieldObject(customFieldKey)
    OptionsManager optionsManager = ComponentAccessor.getComponent(OptionsManager)

    // Step 2: Define the list of values to add and remove duplicates
    List<String> rawData = rawText.split("\\n").collect { it.trim() }
    List<String> data = rawData.unique()

    // Step 3: Check if the custom field exists
    if (customField != null) {
        // Step 4: Get the existing options and values
        List<Option> currentOptions = optionsManager.getOptions(customField.getConfigurationSchemes().first().getOneAndOnlyConfig())

        // Step 5: Disable options that are not in the data list
        currentOptions.each { option ->
            if (!data.contains(option.getValue())) {
                option.setDisabled(true)
            }
        }

        // Step 6: Enable options that are in the data list
        data.each { value ->
            Option existingOption = currentOptions.find { it.getValue().equalsIgnoreCase(value) }

            if (existingOption == null) {
                Option newOption = optionsManager.createOption(customField.getConfigurationSchemes().first().getOneAndOnlyConfig(), null, null, value)
                currentOptions.add(newOption)
                optionsManager.updateOptions(currentOptions)
                currentOptions = optionsManager.getOptions(customField.getConfigurationSchemes().first().getOneAndOnlyConfig())
                currentOptions.sort { it.getValue().toLowerCase() }
            } else {
                // Enable every option in the list
                existingOption.setDisabled(false)
                log.warn("Option '$value' already exists and is enabled for custom field ${customField.name}")
            }
        }

        // Step 7: Sort the options in alphabetical order
        currentOptions.sort { it.getValue().toLowerCase() }

        // Step 8: Enable "해당사항없음" option and move it to the top
        Option noneOption = currentOptions.find { it.getValue().equalsIgnoreCase("해당사항없음") }
        if (noneOption != null) {
            noneOption.setDisabled(false)
            currentOptions.remove(noneOption)
            currentOptions.add(0, noneOption)
        } else {
            log.warn("Option '해당사항없음' does not exist in the custom field ${customField.name}")
        }
    } else {
        log.error("Custom field ${customFieldKey} does not exist")
    }

    return Response.ok(new JsonBuilder([success: true]).toString()).build()
}
