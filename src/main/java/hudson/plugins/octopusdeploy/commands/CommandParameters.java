package hudson.plugins.octopusdeploy.commands;

import hudson.plugins.octopusdeploy.services.OctoCliService;

public abstract class CommandParameters {

    private final OctoCliService octoCliService;
    private final String toolId;
    private final boolean verboseLogging;
    private final String additionalArgs;

    protected CommandParameters(OctoCliService octoCliService, String toolId, boolean verboseLogging, String additionalArgs) {
        this.octoCliService = octoCliService;
        this.toolId = toolId;
        this.verboseLogging = verboseLogging;
        this.additionalArgs = additionalArgs;
    }
    public OctoCliService getOctoCliService() { return this.octoCliService; }

    public String getToolId() { return  this.toolId; }

    public boolean isVerboseLogging() { return verboseLogging; }

    public String getAdditionalArgs() { return additionalArgs; }
}
