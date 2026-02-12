package hudson.plugins.octopusdeploy.cli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.octopusdeploy.constants.OctoConstants;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.Commandline;

import java.io.IOException;
import java.time.LocalTime;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;

/**
 * Wrapper class for executing Golang-based Octopus CLI commands.
 * Supports the new CLI from https://github.com/OctopusDeploy/cli
 */
public class CliWrapper extends BaseCliWrapper {
    /**
     * Package-private constructor - use OctopusCliWrapperBuilder to create
     * instances
     */
    CliWrapper(String toolId, FilePath workspace, Launcher launcher,
            EnvVars environment, TaskListener listener,
            String serverUrl, String apiKey, String spaceId,
            String projectName, boolean verboseLogging, boolean ignoreSslErrors) {
        super(toolId, workspace, launcher, environment, listener,
                serverUrl, apiKey, spaceId, projectName, verboseLogging, ignoreSslErrors);
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
        args.add(format);
        args.add("create");

        // Add common arguments
        addCommonArguments(args, false);

        args.add("--id");
        args.add(packageId);

        if (StringUtils.isNotBlank(packageVersion)) {
            args.add("--version");
            args.add(packageVersion);
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

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices).toResult();
    }

    @Override
    public Result push(List<String> packagePaths, String overwriteMode, String additionalArgs)
            throws IOException, InterruptedException {
        Result loginRes = login();
        if (loginRes != Result.SUCCESS) {
            return loginRes;
        }

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus package upload
        args.add("package");
        args.add("upload");

        // Add common arguments
        addCommonArguments(args, false);

        if (packagePaths != null && !packagePaths.isEmpty()) {
            for (String packagePath : packagePaths) {
                args.add("--package");
                args.add(packagePath);
            }
        }

        if (StringUtils.isNotBlank(overwriteMode)) {
            args.add("--overwrite-mode");
            args.add(convertLegacyOverwriteMode(overwriteMode));
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices).toResult();
    }

    @Override
    public Result pushBuildInformation(List<String> packageIds, String version,
            String filePath, String overwriteMode,
            String additionalArgs)
            throws IOException, InterruptedException {
        Result loginRes = login();
        if (loginRes != Result.SUCCESS) {
            return loginRes;
        }

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus build-information push
        args.add("build-information");
        args.add("upload");

        // Add common arguments
        addCommonArguments(args, false);

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

        if (StringUtils.isNotBlank(convertLegacyOverwriteMode(overwriteMode))) {
            args.add("--overwrite-mode");
            args.add(convertLegacyOverwriteMode(overwriteMode));
        }

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        return execute(args, maskedIndices).toResult();
    }

    @Override
    public Result deployRelease(String version, String environment, String tenant,
            String tenantTag, List<String> variables,
            boolean waitForDeployment, String deploymentTimeout,
            boolean cancelOnTimeout, String additionalArgs)
            throws IOException, InterruptedException {
        Result loginRes = login();
        if (loginRes != Result.SUCCESS) {
            return loginRes;
        }

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus release deploy
        args.add("release");
        args.add("deploy");

        // Add common arguments (includes project)
        addCommonArguments(args, true);

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

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        CliExecutionResult deployResult = execute(args, maskedIndices);
        if (!deployResult.isSuccess()) {
            return deployResult.toResult();
        }

        if (waitForDeployment) {
            String stdout = deployResult.getStdout();
            JSONArray json = (JSONArray) JSONSerializer.toJSON(stdout);
            String taskId = json.getJSONObject(0).getString("ServerTaskId");
            return waitForDeployment(taskId, deploymentTimeout, cancelOnTimeout);
        }

        return deployResult.toResult();
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
        Result loginRes = login();
        if (loginRes != Result.SUCCESS) {
            return loginRes;
        }

        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus release create
        args.add("release");
        args.add("create");

        // Add common arguments (includes project)
        addCommonArguments(args, true);

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

        if (StringUtils.isNotBlank(additionalArgs)) {
            String[] myArgs = Commandline.translateCommandline(additionalArgs);
            args.addAll(Arrays.asList(myArgs));
        }

        CliExecutionResult createResult = execute(args, maskedIndices);
        if (!createResult.isSuccess()) {
            return createResult.toResult();
        }

        if (StringUtils.isNotBlank(deployToEnvironment)) {
            if (StringUtils.isBlank(version)) {
                String stdout = createResult.getStdout();
                JSONObject json = (JSONObject) JSONSerializer.toJSON(stdout);
                version = json.getString("Version");
            }

            return deployRelease(version, deployToEnvironment, tenant, tenantTag,
                    variables, waitForDeployment, deploymentTimeout,
                    cancelOnTimeout, additionalArgs);
        }

        return Result.SUCCESS;
    }

    private Result login() throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus login
        args.add("login");

        // Server
        if (StringUtils.isNotBlank(serverUrl)) {
            args.add("--server");
            args.add(serverUrl);
        }

        // API Key (masked)
        if (StringUtils.isNotBlank(apiKey)) {
            args.add("--api-key");
            args.add(apiKey);
            maskedIndices.add(args.size()); // size is +1 of the apiKey index, but later we also add the binary path at
                                            // the start of the args list, so it works out
        }

        // SSL
        if (ignoreSslErrors) {
            args.add("--ignore-ssl-errors");
        }

        // No prompt & JSON output
        args.add("--no-prompt");
        args.add("--output-format");
        args.add("json");

        return execute(args, maskedIndices).toResult();
    }

    private Result waitForDeployment(String taskId, String deploymentTimeout, boolean cancelOnTimeout)
            throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        Set<Integer> maskedIndices = new HashSet<>();

        // New CLI uses: octopus deployment watch
        args.add("task");
        args.add("wait");

        args.add(taskId);
        args.add("--progress");

        if (StringUtils.isNotBlank(deploymentTimeout)) {
            LocalTime time = LocalTime.parse(deploymentTimeout);
            args.add("--timeout");
            args.add(String.valueOf(time.toSecondOfDay()));
        }

        if (cancelOnTimeout) {
            args.add("--cancel-on-timeout");
        }

        args.add("--space");
        args.add(spaceId);

        // No prompt & JSON output
        args.add("--no-prompt");
        args.add("--output-format");
        args.add("json");

        CliExecutionResult watchResult = execute(args, maskedIndices);
        return watchResult.toResult();
    }

    /**
     * Add common arguments to the command
     */
    private void addCommonArguments(List<String> args, boolean includeProject) {
        // Project
        if (includeProject && StringUtils.isNotBlank(projectName)) {
            args.add("--project");
            args.add(projectName);
        }

        // Space
        if (StringUtils.isNotBlank(spaceId)) {
            args.add("--space");
            args.add(spaceId);
        }

        // No prompt & JSON output
        args.add("--no-prompt");
        args.add("--output-format");
        args.add("json");
    }

    private static String convertLegacyOverwriteMode(String mode) {
        if (mode == null) {
            return "fail";
        }

        switch (mode) {
            case "OverwriteExisting":
                return "overwrite";
            case "FailIfExists":
                return "fail";
            case "IgnoreIfExists":
                return "ignore";
            default:
                return "fail";
        }
    }
}
