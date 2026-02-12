package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.Log;
import hudson.plugins.octopusdeploy.OctoInstallation;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 * Abstract base class for CLI wrappers providing common functionality
 * for executing Octopus CLI commands.
 */
public abstract class BaseCliWrapper implements OctopusCliExecutor {

    // Jenkins dependencies
    private final String toolId;
    private final FilePath workspace;
    private final Launcher launcher;
    private final EnvVars environment;
    private final TaskListener listener;

    // Common configuration (optional)
    protected final String serverUrl;
    protected final String apiKey;
    protected final String spaceId;
    protected final String projectName;
    protected final boolean verboseLogging;
    protected final boolean ignoreSslErrors;

    protected BaseCliWrapper(String toolId, FilePath workspace, Launcher launcher,
                            EnvVars environment, TaskListener listener,
                            String serverUrl, String apiKey, String spaceId,
                            String projectName, boolean verboseLogging, boolean ignoreSslErrors) {
        this.toolId = toolId;
        this.workspace = workspace;
        this.launcher = launcher;
        this.environment = environment;
        this.listener = listener;
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.spaceId = spaceId;
        this.projectName = projectName;
        this.verboseLogging = verboseLogging;
        this.ignoreSslErrors = ignoreSslErrors;
    }

    protected CliExecutionResult execute(List<String> args, Set<Integer> maskedIndices)
            throws IOException, InterruptedException {

        BuildListener buildListener = listener instanceof BuildListener
                ? (BuildListener) listener
                : new BuildListenerAdapter(listener);
        Log log = new Log(buildListener);

        // Get CLI path
        checkState(StringUtils.isNotBlank(toolId),
                String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Octopus CLI"));

        Node builtOn = workspace.toComputer().getNode();
        String cliPath = OctoInstallation.getOctopusToolPath(toolId, builtOn, environment, listener);

        if (StringUtils.isBlank(cliPath)) {
            log.error("OCTOPUS-JENKINS-INPUT-ERROR-0003: The path for the selected Octopus CLI does not exist.");
            return new CliExecutionResult("", 1);
        }

        // Build full command: [cliPath, ...args]
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(cliPath);
        cmdArgs.addAll(args);

        // Build mask array
        boolean[] masks = buildMaskArray(cmdArgs.size(), maskedIndices);

        // Capture stdout
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Launch process
        environment.put("OCTOEXTENSION", "");
        
        // Set Octopus CLI environment variables so they don't rely on config file
        if (StringUtils.isNotBlank(serverUrl)) {
            environment.put("OCTOPUS_URL", serverUrl);
        }
        if (StringUtils.isNotBlank(apiKey)) {
            environment.put("OCTOPUS_API_KEY", apiKey);
        }
        if (StringUtils.isNotBlank(spaceId)) {
            environment.put("OCTOPUS_SPACE", spaceId);
        }
        
        Proc process = launcher.launch()
                .cmds(cmdArgs)
                .masks(masks)
                .stdout(outputStream)
                .envs(environment)
                .pwd(workspace)
                .start();

        int exitCode = process.join();

        String stdout = outputStream.toString(StandardCharsets.UTF_8.name());

        // Log output to build listener for visibility
        listener.getLogger().print(stdout);
        log.info(String.format("Octopus CLI exit code: %d", exitCode));

        if (exitCode != 0) {
            log.error("Octopus CLI command failed. Please check the build log for details on the error.");
        }

        return new CliExecutionResult(stdout, exitCode);
    }

    /**
     * Build mask array from set of masked indices
     */
    private static boolean[] buildMaskArray(int size, Set<Integer> maskedIndices) {
        Boolean[] masks = new Boolean[size];
        Arrays.fill(masks, Boolean.FALSE);

        for (Integer index : maskedIndices) {
            if (index < size) {
                masks[index] = Boolean.TRUE;
            }
        }

        return ArrayUtils.toPrimitive(masks);
    }
}
