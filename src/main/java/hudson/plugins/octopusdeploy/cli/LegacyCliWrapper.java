package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.Log;
import hudson.plugins.octopusdeploy.OctoInstallation;
import hudson.plugins.octopusdeploy.OctopusDeployPlugin;
import hudson.plugins.octopusdeploy.OctopusDeployServer;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import hudson.plugins.octopusdeploy.utils.JenkinsHelpers;
import jenkins.model.Jenkins;
import jenkins.util.BuildListenerAdapter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;

import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 * Wrapper class for executing Octopus CLI commands.
 * Handles command construction, argument masking, and process execution.
 */
public class LegacyCliWrapper {

    // Jenkins dependencies
    private final String toolId;
    private final FilePath workspace;
    private final Launcher launcher;
    private final EnvVars environment;
    private final TaskListener listener;

    // Common configuration (optional)
    private final String serverUrl;
    private final String apiKey;
    private final String spaceId;
    private final String projectName;
    private final boolean verboseLogging;
    private final boolean ignoreSslErrors;

    private LegacyCliWrapper(Builder builder) {
        this.toolId = builder.toolId;
        this.workspace = builder.workspace;
        this.launcher = builder.launcher;
        this.environment = builder.environment;
        this.listener = builder.listener;
        this.serverUrl = builder.serverUrl;
        this.apiKey = builder.apiKey;
        this.spaceId = builder.spaceId;
        this.projectName = builder.projectName;
        this.verboseLogging = builder.verboseLogging;
        this.ignoreSslErrors = builder.ignoreSslErrors;
    }

    /**
     * Builder for OctopusCliWrapper
     */
    public static class Builder {
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

        public Builder(String toolId, FilePath workspace, Launcher launcher,
                EnvVars environment, TaskListener listener) {
            this.toolId = toolId;
            this.workspace = workspace;
            this.launcher = launcher;
            this.environment = environment;
            this.listener = listener;
        }

        public Builder serverId(String serverId) {
            // Get server configuration
            OctopusDeployServer server = getOctopusDeployServer(serverId);
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

        public Builder spaceId(String spaceId) {
            this.spaceId = spaceId;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder verboseLogging(boolean verboseLogging) {
            this.verboseLogging = verboseLogging;
            return this;
        }

        public LegacyCliWrapper build() {
            return new LegacyCliWrapper(this);
        }
    }

    /**
     * Execute pack command
     */
    public Result pack(String packageId, String packageVersion, String format,
            String sourcePath, List<String> includePaths, String outputPath,
            boolean overwriteExisting, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        checkState(StringUtils.isNotBlank(packageId),
                String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Package ID"));

        args.add("--id");
        args.add(packageId);

        if (StringUtils.isNotBlank(packageVersion)) {
            args.add("--version");
            args.add(packageVersion);
        }

        if (StringUtils.isNotBlank(format)) {
            args.add("--format");
            args.add(format);
        }

        if (StringUtils.isNotBlank(sourcePath)) {
            args.add("--basePath");
            args.add(sourcePath);
        }

        if (includePaths != null && !includePaths.isEmpty()) {
            for (String include : includePaths) {
                args.add("--include");
                args.add(include);
            }
        }

        if (StringUtils.isNotBlank(outputPath)) {
            args.add("--outFolder");
            args.add(outputPath);
        }

        if (overwriteExisting) {
            args.add("--overwrite");
        }

        if (verboseLogging) {
            args.add("--verbose");
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute("pack", args, maskedIndices);
    }

    /**
     * Execute push command
     */
    public Result push(List<String> packagePaths, String overwriteMode, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // Add common arguments
        addCommonArguments(args, maskedIndices, false); // push doesn't need project

        if (packagePaths != null && !packagePaths.isEmpty()) {
            for (String packagePath : packagePaths) {
                args.add("--package");
                args.add(packagePath);
            }
        }

        if (StringUtils.isNotBlank(overwriteMode)) {
            args.add("--overwrite-mode");
            args.add(overwriteMode);
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute("push", args, maskedIndices);
    }

    /**
     * Execute build-information push command
     */
    public Result pushBuildInformation(List<String> packageIds, String version,
            String filePath, String overwriteMode,
            String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // Add common arguments
        addCommonArguments(args, maskedIndices, false); // build-information doesn't need project

        if (packageIds != null && !packageIds.isEmpty()) {
            for (String packageId : packageIds) {
                args.add("--package-id");
                args.add(packageId);
            }
        }

        if (StringUtils.isNotBlank(version)) {
            args.add("--version");
            args.add(version);
        }

        if (StringUtils.isNotBlank(filePath)) {
            args.add("--file");
            args.add(filePath);
        }

        if (StringUtils.isNotBlank(overwriteMode)) {
            args.add("--overwrite-mode");
            args.add(overwriteMode);
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute("build-information", args, maskedIndices);
    }

    /**
     * Execute deploy-release command
     */
    public Result deployRelease(String version, String environment, String tenant,
            String tenantTag, List<String> variables,
            boolean waitForDeployment, String deploymentTimeout,
            boolean cancelOnTimeout, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // Add common arguments (includes project)
        addCommonArguments(args, maskedIndices, true);

        if (StringUtils.isNotBlank(version)) {
            args.add("--version");
            args.add(version);
        }

        if (StringUtils.isNotBlank(environment)) {
            args.add("--deployTo");
            args.add(environment);
        }

        if (StringUtils.isNotBlank(tenant)) {
            args.add("--tenant");
            args.add(tenant);
        }

        if (StringUtils.isNotBlank(tenantTag)) {
            args.add("--tenantTag");
            args.add(tenantTag);
        }

        if (variables != null && !variables.isEmpty()) {
            for (String variable : variables) {
                args.add("--variable");
                args.add(variable);
            }
        }

        if (waitForDeployment) {
            args.add("--waitForDeployment");

            if (StringUtils.isNotBlank(deploymentTimeout)) {
                args.add("--deploymentTimeout");
                args.add(deploymentTimeout);
            }

            if (cancelOnTimeout) {
                args.add("--cancelOnTimeout");
            }
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute("deploy-release", args, maskedIndices);
    }

    /**
     * Execute create-release command
     */
    public Result createRelease(String version, String channel, String releaseNotes,
            String defaultPackageVersion, List<String> packages,
            String gitRef, String gitCommit,
            String deployToEnvironment, String tenant, String tenantTag,
            List<String> variables, boolean waitForDeployment,
            String deploymentTimeout, boolean cancelOnTimeout,
            String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // Add common arguments (includes project)
        addCommonArguments(args, maskedIndices, true);

        if (StringUtils.isNotBlank(version)) {
            args.add("--version");
            args.add(version);
        }

        if (StringUtils.isNotBlank(channel)) {
            args.add("--channel");
            args.add(channel);
        }

        if (StringUtils.isNotBlank(releaseNotes)) {
            args.add("--releaseNotes");
            args.add(releaseNotes);
        }

        if (StringUtils.isNotBlank(defaultPackageVersion)) {
            args.add("--packageVersion");
            args.add(defaultPackageVersion);
        }

        if (packages != null && !packages.isEmpty()) {
            for (String pkg : packages) {
                args.add("--package");
                args.add(pkg);
            }
        }

        if (StringUtils.isNotBlank(gitRef)) {
            args.add("--gitRef");
            args.add(gitRef);
        }

        if (StringUtils.isNotBlank(gitCommit)) {
            args.add("--gitCommit");
            args.add(gitCommit);
        }

        if (StringUtils.isNotBlank(deployToEnvironment)) {
            args.add("--deployTo");
            args.add(deployToEnvironment);
        }

        if (StringUtils.isNotBlank(tenant)) {
            args.add("--tenant");
            args.add(tenant);
        }

        if (StringUtils.isNotBlank(tenantTag)) {
            args.add("--tenantTag");
            args.add(tenantTag);
        }

        if (variables != null && !variables.isEmpty()) {
            for (String variable : variables) {
                args.add("--variable");
                args.add(variable);
            }
        }

        if (waitForDeployment) {
            args.add("--waitForDeployment");

            if (StringUtils.isNotBlank(deploymentTimeout)) {
                args.add("--deploymentTimeout");
                args.add(deploymentTimeout);
            }

            if (cancelOnTimeout) {
                args.add("--cancelOnTimeout");
            }
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute("create-release", args, maskedIndices);
    }

    /**
     * Add common arguments to the command
     */
    private void addCommonArguments(List<String> args, Set<Integer> maskedIndices, boolean includeProject) {
        // Project
        if (includeProject && StringUtils.isNotBlank(projectName)) {
            args.add(OctoConstants.Commands.Arguments.PROJECT_NAME);
            args.add(projectName);
        }

        // Server
        if (StringUtils.isNotBlank(serverUrl)) {
            args.add(OctoConstants.Commands.Arguments.SERVER_URL);
            args.add(serverUrl);
        }

        // API Key (masked)
        if (StringUtils.isNotBlank(apiKey)) {
            args.add(OctoConstants.Commands.Arguments.API_KEY);
            maskedIndices.add(args.size()); // Mask the next value
            args.add(apiKey);
        }

        // Space
        if (StringUtils.isNotBlank(spaceId)) {
            args.add(OctoConstants.Commands.Arguments.SPACE_NAME);
            args.add(spaceId);
        }

        // SSL
        if (ignoreSslErrors) {
            args.add("--ignoreSslErrors");
        }

        // Debug
        if (verboseLogging) {
            args.add("--debug");
        }
    }

    /**
     * Core execution method
     */
    private Result execute(String command, List<String> args, Set<Integer> maskedIndices)
            throws IOException, InterruptedException {

        BuildListener buildListener = listener instanceof BuildListener
                ? (BuildListener) listener
                : new BuildListenerAdapter(listener);
        Log log = new Log(buildListener);

        // Get CLI path
        checkState(StringUtils.isNotBlank(toolId),
                String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Octopus CLI"));

        Node builtOn = workspace.toComputer().getNode();
        String cliPath = getOctopusToolPath(toolId, builtOn, environment, listener);

        if (StringUtils.isBlank(cliPath)) {
            log.error("OCTOPUS-JENKINS-INPUT-ERROR-0003: The path for the selected Octopus CLI does not exist.");
            return Result.FAILURE;
        }

        // Build full command: [cliPath, command, ...args]
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(cliPath);
        cmdArgs.add(command);
        cmdArgs.addAll(args);

        // Build mask array
        boolean[] masks = buildMaskArray(cmdArgs.size(), maskedIndices);

        // Launch process
        environment.put("OCTOEXTENSION", "");
        Proc process = launcher.launch()
                .cmds(cmdArgs)
                .masks(masks)
                .stdout(listener)
                .envs(environment)
                .pwd(workspace)
                .start();

        int exitCode = process.join();
        log.info(String.format("Octopus CLI exit code: %d", exitCode));

        if (exitCode == 0) {
            return Result.SUCCESS;
        }

        log.error("Unable to create or deploy release. Please check the build log for details on the error.");
        return Result.FAILURE;
    }

    /**
     * Build mask array from set of masked indices
     */
    private boolean[] buildMaskArray(int size, Set<Integer> maskedIndices) {
        Boolean[] masks = new Boolean[size];
        Arrays.fill(masks, Boolean.FALSE);

        for (Integer index : maskedIndices) {
            if (index < size) {
                masks[index] = Boolean.TRUE;
            }
        }

        return ArrayUtils.toPrimitive(masks);
    }

    /**
     * Get Octopus tool path
     */
    private static String getOctopusToolPath(String name, Node builtOn, EnvVars env, TaskListener taskListener) {
        Jenkins jenkins = JenkinsHelpers.getJenkins();
        OctoInstallation.DescriptorImpl descriptor = (OctoInstallation.DescriptorImpl) jenkins
                .getDescriptor(OctoInstallation.class);
        return descriptor.getInstallation(name).getPathToOctoExe(builtOn, env, taskListener);
    }

    /**
     * Get the default OctopusDeployServer from OctopusDeployPlugin configuration
     * 
     * @return the default server
     */
    private static OctopusDeployServer getDefaultOctopusDeployServer() {
        Jenkins jenkinsInstance = JenkinsHelpers.getJenkins();
        OctopusDeployPlugin.DescriptorImpl descriptor = (OctopusDeployPlugin.DescriptorImpl) jenkinsInstance
                .getDescriptor(OctopusDeployPlugin.class);
        return descriptor.getDefaultOctopusDeployServer();
    }

    /**
     * Get the list of OctopusDeployServer from OctopusDeployPlugin configuration
     * 
     * @return all configured servers
     */
    private static List<OctopusDeployServer> getOctopusDeployServers() {
        Jenkins jenkinsInstance = JenkinsHelpers.getJenkins();
        OctopusDeployPlugin.DescriptorImpl descriptor = (OctopusDeployPlugin.DescriptorImpl) jenkinsInstance
                .getDescriptor(OctopusDeployPlugin.class);
        return descriptor.getOctopusDeployServers();
    }

    /**
     * Get the instance of OctopusDeployServer by serverId
     * 
     * @param serverId The id of OctopusDeployServer in the configuration.
     * @return the server by id
     */
    private static OctopusDeployServer getOctopusDeployServer(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            return getDefaultOctopusDeployServer();
        }

        for (OctopusDeployServer server : getOctopusDeployServers()) {
            if (server.getServerId().equals(serverId)) {
                return server;
            }
        }

        return null;
    }
}
