package hudson.plugins.octopusdeploy.services;

import static org.apache.commons.lang3.StringUtils.trim;

public class StringUtil {
    public static String sanitizeValue(String value) {
        return trim(value);
    }
}
