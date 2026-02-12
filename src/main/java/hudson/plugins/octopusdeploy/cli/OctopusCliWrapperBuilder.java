package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.OctopusDeployPlugin;
import hudson.plugins.octopusdeploy.OctopusDeployServer;
import hudson.plugins.octopusdeploy.OctoInstallation;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import org.apache.commons.lang.StringUtils;
import java.util.*;
import java.io.IOException;

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

    public OctopusCliWrapperBuilder(String toolId, FilePath workspace, Launcher launcher,
            EnvVars environment, TaskListener listener) {
        this.toolId = toolId;
        this.workspace = workspace;
        this.launcher = launcher;
        this.environment = environment;
        this.listener = listener;
    }

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

    public OctopusCliExecutor build() {
        CliType t;
        try {
            t = inferCliType();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed infering Octopus CLI type", e);
        }

        switch (t) {
            case Current:
                return new CliWrapper(toolId, workspace, launcher, environment, listener,
                    serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
            case Legacy:
                return new LegacyCliWrapper(toolId, workspace, launcher, environment, listener,
                    serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
            default:
                throw new IllegalStateException("Unexpected CLI type: " + t);
        }
    }

    // Package-private getters for wrapper constructors
    String getToolId() {
        return toolId;
    }

    FilePath getWorkspace() {
        return workspace;
    }

    Launcher getLauncher() {
        return launcher;
    }

    EnvVars getEnvironment() {
        return environment;
    }

    TaskListener getListener() {
        return listener;
    }

    String getServerUrl() {
        return serverUrl;
    }

    String getApiKey() {
        return apiKey;
    }

    String getSpaceId() {
        return spaceId;
    }

    String getProjectName() {
        return projectName;
    }

    boolean isVerboseLogging() {
        return verboseLogging;
    }

    boolean isIgnoreSslErrors() {
        return ignoreSslErrors;
    }

    private CliType inferCliType() throws IOException, InterruptedException {
        Node builtOn = workspace.toComputer().getNode();
        String cliPath = OctoInstallation.getOctopusToolPath(toolId, builtOn, environment, listener);
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(cliPath);
        cmdArgs.add("config");
        cmdArgs.add("list");

        Proc process = launcher.launch()
                .cmds(cmdArgs)
                .start();

        int exitCode = process.join();
        if (exitCode == 0) {
            return CliType.Current;
        } else {
            return CliType.Legacy;
        }
    }
}
