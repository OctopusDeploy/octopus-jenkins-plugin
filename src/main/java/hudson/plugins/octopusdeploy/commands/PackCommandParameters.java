package hudson.plugins.octopusdeploy.commands;

import hudson.plugins.octopusdeploy.OverwriteMode;
import hudson.plugins.octopusdeploy.services.OctoCliService;

public class PackCommandParameters extends CommandParameters {
    private final String packageId;
    private final String packageFormat;
    private final String sourcePath;
    private final String packageVersion;
    private final String includePaths;
    private final String outputPath;
    private final boolean overwriteExisting;

    public PackCommandParameters(OctoCliService octoCliService, String toolId, String packageId, String packageFormat, String sourcePath, String packageVersion, String includePaths, String outputPath, String additionalArgs, boolean verboseLogging, boolean overwriteExisting) {
        super(octoCliService, toolId, verboseLogging, additionalArgs);
        this.packageId = packageId;
        this.packageFormat = packageFormat;
        this.sourcePath = sourcePath;
        this.packageVersion = packageVersion;
        this.includePaths = includePaths;
        this.outputPath = outputPath;
        this.overwriteExisting = overwriteExisting;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getPackageFormat() {
        return packageFormat;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public String getIncludePaths() {
        return includePaths;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public boolean isOverwriteExisting() {
        return overwriteExisting;
    }
}
