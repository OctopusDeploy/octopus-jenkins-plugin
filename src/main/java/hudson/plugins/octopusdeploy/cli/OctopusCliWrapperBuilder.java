package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.OctopusDeployPlugin;
import hudson.plugins.octopusdeploy.OctopusDeployServer;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import org.apache.commons.lang.StringUtils;

import static com.google.common.base.Preconditions.checkState;

/**
 * Builder for creating OctopusCliExecutor instances.
 * Supports both legacy .NET CLI and new Golang-based CLI.
 */
public class OctopusCliWrapperBuilder {
    // Required parameters
    private final String toolId;
    private final FilePath workspace;
    private final Launcher launcher;
    private final EnvVars environment;
    private final TaskListener listener;

    // Optional parameters
    private String serverUrl;
    private String apiKey;
    private String spaceId;
    private String projectName;
    private boolean verboseLogging;
    private boolean ignoreSslErrors;
    private boolean useNewCli = false;

    public OctopusCliWrapperBuilder(String toolId, FilePath workspace, Launcher launcher,
                                    EnvVars environment, TaskListener listener) {
        this.toolId = toolId;
        this.workspace = workspace;
        this.launcher = launcher;
        this.environment = environment;
        this.listener = listener;
    }

    /**
     * Configure from server ID - looks up server configuration and extracts URL, API key, SSL settings
     */
    public OctopusCliWrapperBuilder serverId(String serverId) {
        OctopusDeployServer server = OctopusDeployPlugin.getOctopusDeployServer(serverId);
        String serverUrl = server.getUrl();
        String apiKey = server.getApiKey().getPlainText();
        boolean ignoreSslErrors = server.getIgnoreSslErrors();

        checkState(StringUtils.isNotBlank(serverUrl),
                String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Octopus URL"));
        checkState(StringUtils.isNotBlank(apiKey),
                String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "API Key"));

        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.ignoreSslErrors = ignoreSslErrors;
        return this;
    }

    public OctopusCliWrapperBuilder serverUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public OctopusCliWrapperBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public OctopusCliWrapperBuilder spaceId(String spaceId) {
        this.spaceId = spaceId;
        return this;
    }

    public OctopusCliWrapperBuilder projectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public OctopusCliWrapperBuilder verboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
        return this;
    }

    public OctopusCliWrapperBuilder ignoreSslErrors(boolean ignoreSslErrors) {
        this.ignoreSslErrors = ignoreSslErrors;
        return this;
    }

    /**
     * Set whether to use the new Golang-based CLI (true) or legacy .NET CLI (false).
     * Defaults to false (legacy CLI).
     */
    public OctopusCliWrapperBuilder useNewCli(boolean useNewCli) {
        this.useNewCli = useNewCli;
        return this;
    }

    /**
     * Build the CLI executor instance.
     * Returns either LegacyCliWrapper or CliWrapper based on useNewCli flag.
     */
    public OctopusCliExecutor build() {
        if (useNewCli) {
            return new CliWrapper(toolId, workspace, launcher, environment, listener,
                    serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
        } else {
            return new LegacyCliWrapper(toolId, workspace, launcher, environment, listener,
                    serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
        }
    }

    // Package-private getters for wrapper constructors
    String getToolId() { return toolId; }
    FilePath getWorkspace() { return workspace; }
    Launcher getLauncher() { return launcher; }
    EnvVars getEnvironment() { return environment; }
    TaskListener getListener() { return listener; }
    String getServerUrl() { return serverUrl; }
    String getApiKey() { return apiKey; }
    String getSpaceId() { return spaceId; }
    String getProjectName() { return projectName; }
    boolean isVerboseLogging() { return verboseLogging; }
    boolean isIgnoreSslErrors() { return ignoreSslErrors; }
}
