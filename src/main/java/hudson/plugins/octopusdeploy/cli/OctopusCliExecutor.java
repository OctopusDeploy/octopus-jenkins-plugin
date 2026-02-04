package hudson.plugins.octopusdeploy.cli;

import hudson.model.Result;

import java.io.IOException;
import java.util.List;

/**
 * Interface for executing Octopus CLI commands.
 * Implemented by both the legacy .NET CLI wrapper and the new Golang-based CLI wrapper.
 */
public interface OctopusCliExecutor {

    /**
     * Execute pack command to create a package
     */
    Result pack(String packageId, String packageVersion, String format,
                String sourcePath, List<String> includePaths, String outputPath,
                boolean overwriteExisting, String additionalArgs)
            throws IOException, InterruptedException;

    /**
     * Execute push command to push packages to Octopus
     */
    Result push(List<String> packagePaths, String overwriteMode, String additionalArgs)
            throws IOException, InterruptedException;

    /**
     * Execute build-information command to push build information
     */
    Result pushBuildInformation(List<String> packageIds, String version,
                                String filePath, String overwriteMode,
                                String additionalArgs)
            throws IOException, InterruptedException;

    /**
     * Execute deploy-release command to deploy a release
     */
    Result deployRelease(String version, String environment, String tenant,
                        String tenantTag, List<String> variables,
                        boolean waitForDeployment, String deploymentTimeout,
                        boolean cancelOnTimeout, String additionalArgs)
            throws IOException, InterruptedException;

    /**
     * Execute create-release command to create a new release
     */
    Result createRelease(String version, String channel, String releaseNotes,
                        String defaultPackageVersion, List<String> packages,
                        String gitRef, String gitCommit,
                        String deployToEnvironment, String tenant, String tenantTag,
                        List<String> variables, boolean waitForDeployment,
                        String deploymentTimeout, boolean cancelOnTimeout,
                        String additionalArgs)
            throws IOException, InterruptedException;
}
