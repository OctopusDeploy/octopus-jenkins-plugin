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
 * Wrapper class for executing Golang-based Octopus CLI commands.
 * Supports the new CLI from https://github.com/OctopusDeploy/cli
 */
public class CliWrapper implements OctopusCliExecutor {

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

    /**
     * Package-private constructor - use OctopusCliWrapperBuilder to create instances
     */
    CliWrapper(String toolId, FilePath workspace, Launcher launcher,
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

    @Override
    public Result pack(String packageId, String packageVersion, String format,
                      String sourcePath, List<String> includePaths, String outputPath,
                      boolean overwriteExisting, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        checkState(StringUtils.isNotBlank(packageId),
                  String.format(OctoConstants.Errors.INPUT_CANNOT_BE_BLANK_MESSAGE_FORMAT, "Package ID"));

        // New CLI uses: octopus package pack
        args.add("package");
        args.add("pack");

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
            args.add("--base-path");
            args.add(sourcePath);
        }

        if (includePaths != null && !includePaths.isEmpty()) {
            for (String include : includePaths) {
                args.add("--include");
                args.add(include);
            }
        }

        if (StringUtils.isNotBlank(outputPath)) {
            args.add("--out-folder");
            args.add(outputPath);
        }

        if (overwriteExisting) {
            args.add("--overwrite");
        }

        if (verboseLogging) {
            args.add("--debug");
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices);
    }

    @Override
    public Result push(List<String> packagePaths, String overwriteMode, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus package upload
        args.add("package");
        args.add("upload");

        // Add common arguments
        addCommonArguments(args, maskedIndices, false);

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

        return execute(args, maskedIndices);
    }

    @Override
    public Result pushBuildInformation(List<String> packageIds, String version,
                                      String filePath, String overwriteMode,
                                      String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus build-information push
        args.add("build-information");
        args.add("push");

        // Add common arguments
        addCommonArguments(args, maskedIndices, false);

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

        return execute(args, maskedIndices);
    }

    @Override
    public Result deployRelease(String version, String environment, String tenant,
                               String tenantTag, List<String> variables,
                               boolean waitForDeployment, String deploymentTimeout,
                               boolean cancelOnTimeout, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus release deploy
        args.add("release");
        args.add("deploy");

        // Add common arguments (includes project)
        addCommonArguments(args, maskedIndices, true);

        if (StringUtils.isNotBlank(version)) {
            args.add("--version");
            args.add(version);
        }

        if (StringUtils.isNotBlank(environment)) {
            args.add("--environment");
            args.add(environment);
        }

        if (StringUtils.isNotBlank(tenant)) {
            args.add("--tenant");
            args.add(tenant);
        }

        if (StringUtils.isNotBlank(tenantTag)) {
            args.add("--tenant-tag");
            args.add(tenantTag);
        }

        if (variables != null && !variables.isEmpty()) {
            for (String variable : variables) {
                args.add("--variable");
                args.add(variable);
            }
        }

        if (waitForDeployment) {
            args.add("--progress");

            if (StringUtils.isNotBlank(deploymentTimeout)) {
                args.add("--deployment-timeout");
                args.add(deploymentTimeout);
            }

            if (cancelOnTimeout) {
                args.add("--cancel-on-timeout");
            }
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices);
    }

    @Override
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

        // New CLI uses: octopus release create
        args.add("release");
        args.add("create");

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
            args.add("--release-notes");
            args.add(releaseNotes);
        }

        if (StringUtils.isNotBlank(defaultPackageVersion)) {
            args.add("--package-version");
            args.add(defaultPackageVersion);
        }

        if (packages != null && !packages.isEmpty()) {
            for (String pkg : packages) {
                args.add("--package");
                args.add(pkg);
            }
        }

        if (StringUtils.isNotBlank(gitRef)) {
            args.add("--git-ref");
            args.add(gitRef);
        }

        if (StringUtils.isNotBlank(gitCommit)) {
            args.add("--git-commit");
            args.add(gitCommit);
        }

        if (StringUtils.isNotBlank(deployToEnvironment)) {
            args.add("--deploy-to");
            args.add(deployToEnvironment);
        }

        if (StringUtils.isNotBlank(tenant)) {
            args.add("--tenant");
            args.add(tenant);
        }

        if (StringUtils.isNotBlank(tenantTag)) {
            args.add("--tenant-tag");
            args.add(tenantTag);
        }

        if (variables != null && !variables.isEmpty()) {
            for (String variable : variables) {
                args.add("--variable");
                args.add(variable);
            }
        }

        if (waitForDeployment) {
            args.add("--progress");

            if (StringUtils.isNotBlank(deploymentTimeout)) {
                args.add("--deployment-timeout");
                args.add(deploymentTimeout);
            }

            if (cancelOnTimeout) {
                args.add("--cancel-on-timeout");
            }
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices);
    }

    /**
     * Add common arguments to the command
     */
    private void addCommonArguments(List<String> args, Set<Integer> maskedIndices, boolean includeProject) {
        // Project
        if (includeProject && StringUtils.isNotBlank(projectName)) {
            args.add("--project");
            args.add(projectName);
        }

        // Server
        if (StringUtils.isNotBlank(serverUrl)) {
            args.add("--server");
            args.add(serverUrl);
        }

        // API Key (masked)
        if (StringUtils.isNotBlank(apiKey)) {
            args.add("--api-key");
            maskedIndices.add(args.size()); // Mask the next value
            args.add(apiKey);
        }

        // Space
        if (StringUtils.isNotBlank(spaceId)) {
            args.add("--space");
            args.add(spaceId);
        }

        // SSL
        if (ignoreSslErrors) {
            args.add("--ignore-ssl-errors");
        }

        // Debug
        if (verboseLogging) {
            args.add("--debug");
        }
    }

    /**
     * Core execution method
     */
    private Result execute(List<String> args, Set<Integer> maskedIndices)
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

        // Build full command: [cliPath, ...args]
        // Note: New CLI doesn't need a command parameter before the subcommands
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(cliPath);
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

        log.error("Octopus CLI command failed. Please check the build log for details on the error.");
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
}
