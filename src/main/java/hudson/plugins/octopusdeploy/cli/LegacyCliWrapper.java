package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;

import java.io.IOException;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 * Wrapper class for executing legacy .NET-based Octopus CLI commands.
 * Handles command construction, argument masking, and process execution.
 */
public class LegacyCliWrapper extends BaseCliWrapper {
    LegacyCliWrapper(String toolId, FilePath workspace, Launcher launcher,
                     EnvVars environment, TaskListener listener,
                     String serverUrl, String apiKey, String spaceId,
                     String projectName, boolean verboseLogging, boolean ignoreSslErrors) {
        super(toolId, workspace, launcher, environment, listener,
              serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
    }

    public Result pack(String packageId, String packageVersion, String format,
            String sourcePath, List<String> includePaths, String outputPath,
            boolean overwriteExisting, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        args.add("pack");

        // Add common arguments
        addCommonArguments(args, maskedIndices, false);

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

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices).toResult();
    }

    public Result push(List<String> packagePaths, String overwriteMode, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        args.add("push");

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

        return execute(args, maskedIndices).toResult();
    }

    public Result pushBuildInformation(List<String> packageIds, String version,
            String filePath, String overwriteMode,
            String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        args.add("build-information");

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

        return execute(args, maskedIndices).toResult();
    }

    public Result deployRelease(String version, String environment, String tenant,
            String tenantTag, List<String> variables,
            boolean waitForDeployment, String deploymentTimeout,
            boolean cancelOnTimeout, String additionalArgs)
            throws IOException, InterruptedException {

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        args.add("deploy-release");

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

        return execute(args, maskedIndices).toResult();
    }

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

        args.add("create-release");

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

        return execute(args, maskedIndices).toResult();
    }

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
}
